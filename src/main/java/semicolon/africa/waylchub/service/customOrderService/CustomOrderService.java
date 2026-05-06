package semicolon.africa.waylchub.service.customOrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.customOrderDto.CustomOrderRequest;
import semicolon.africa.waylchub.dto.customOrderDto.QuoteRequest;
import semicolon.africa.waylchub.dto.customOrderDto.StyleImageInput;
import semicolon.africa.waylchub.event.CustomOrderQuotedEvent;
import semicolon.africa.waylchub.event.CustomOrderSubmittedEvent;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.customOrder.*;
import semicolon.africa.waylchub.repository.customOrderRepository.CustomOrderRepository;
import semicolon.africa.waylchub.service.storage.StorageService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Core business logic for custom (made-to-measure) orders.
 *
 * UPDATED: Category lookup now goes through {@link CustomCategoryService}
 * (which reads from MongoDB), with a fallback to the static
 * {@link CustomCategoryRegistry} for resilience — useful before the seeder
 * has run on a fresh DB.
 *
 * State machine, idempotency, deposit math, and image resolution are unchanged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOrderService {

    private static final String STYLE_REF_FOLDER = "custom-orders/style-refs";

    /** State machine — same as before. */
    private static final Map<CustomOrderStatus, Set<CustomOrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            CustomOrderStatus.SUBMITTED,    Set.of(CustomOrderStatus.QUOTED, CustomOrderStatus.REJECTED, CustomOrderStatus.CANCELLED),
            CustomOrderStatus.QUOTED,       Set.of(CustomOrderStatus.DEPOSIT_PAID, CustomOrderStatus.CANCELLED),
            CustomOrderStatus.DEPOSIT_PAID, Set.of(CustomOrderStatus.IN_PRODUCTION, CustomOrderStatus.CANCELLED),
            CustomOrderStatus.IN_PRODUCTION, Set.of(CustomOrderStatus.READY, CustomOrderStatus.CANCELLED),
            CustomOrderStatus.READY,        Set.of(CustomOrderStatus.SHIPPED, CustomOrderStatus.DELIVERED),
            CustomOrderStatus.SHIPPED,      Set.of(CustomOrderStatus.DELIVERED),
            CustomOrderStatus.DELIVERED,    Set.of(CustomOrderStatus.COMPLETED)
    );

    private final CustomOrderRepository repository;
    private final CustomReferenceGenerator referenceGenerator;
    private final CustomCategoryService categoryService;     // ✨ DB-backed
    private final CustomCategoryRegistry categoryRegistry;   // fallback only
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    // =========================================================================
    //  SUBMIT
    // =========================================================================

    @Transactional
    public CustomOrder submit(CustomOrderRequest request, String customerId) {
        log.info("Custom order submission — category={} gender={} customer={}",
                request.getCategoryId(), request.getGender(),
                customerId != null ? customerId : "guest");

        // Idempotency
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<CustomOrder> existing = repository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotency hit on key '{}' — returning existing order {}",
                        request.getIdempotencyKey(), existing.get().getReferenceNumber());
                return existing.get();
            }
        }

        validateSubmission(request);

        // Resolve category — DB first, fallback to static registry
        ResolvedCategory category = resolveCategory(request.getCategoryId());

        // Resolve style images (upload data URLs, keep pre-uploaded URLs)
        List<String> resolvedImageUrls = resolveStyleImages(request.getReferenceImages());

        // Build embedded specs
        StyleSpec style = StyleSpec.builder()
                .selectedStyleId(request.getSelectedStyleId())
                .selectedStyleName(null)
                .referenceImageUrls(resolvedImageUrls)
                .styleNotes(request.getStyleNotes())
                .build();

        SizeSpec size = SizeSpec.builder()
                .mode(request.getSizeMode())
                .chartSize(request.getSizeMode() == SizeMode.CHART ? request.getChartSize() : null)
                .measurements(request.getSizeMode() == SizeMode.MANUAL
                        ? new HashMap<>(request.getMeasurements()) : new HashMap<>())
                .profileName(request.getProfileName())
                .build();

        DetailsSpec details = DetailsSpec.builder()
                .fitting(request.getFitting() != null ? request.getFitting() : FittingPreference.REGULAR)
                .fabric(request.getFabric())
                .color(request.getColor())
                .occasion(request.getOccasion())
                .needBy(request.getNeedBy())
                .notes(request.getNotes())
                .build();

        DeliverySpec delivery = DeliverySpec.builder()
                .mode(request.getDeliveryMode())
                .address(request.getDeliveryAddress())
                .build();

        CustomOrder order = CustomOrder.builder()
                .referenceNumber(referenceGenerator.generate())
                .categoryId(request.getCategoryId())
                .categoryName(category.name())
                .gender(request.getGender())
                .customerId(customerId)
                .customerName(request.getCustomerName().trim())
                .customerEmail(request.getCustomerEmail())
                .whatsappNumber(request.getWhatsappNumber())
                .phoneNumber(request.getPhoneNumber())
                .style(style)
                .size(size)
                .details(details)
                .delivery(delivery)
                .pricing(PricingSpec.builder().build())
                .status(CustomOrderStatus.SUBMITTED)
                .statusHistory(new ArrayList<>())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        appendHistory(order, CustomOrderStatus.SUBMITTED,
                "Order submitted by " + (customerId != null ? "customer" : "guest"),
                customerId != null ? customerId : "guest");

        CustomOrder saved = repository.save(order);
        log.info("Custom order {} created (id={})", saved.getReferenceNumber(), saved.getId());

        eventPublisher.publishEvent(new CustomOrderSubmittedEvent(saved));
        return saved;
    }

    /**
     * Looks the category up in the DB first; falls back to the static registry.
     * The fallback covers the case where someone hits the API before the
     * seeder has populated MongoDB on a fresh deploy.
     */
    private ResolvedCategory resolveCategory(String categoryId) {
        try {
            CustomCategory dbCategory = categoryService.getBySlug(categoryId);
            BigDecimal priceFrom = dbCategory.getPriceFrom() != null ? dbCategory.getPriceFrom() : BigDecimal.ZERO;
            return new ResolvedCategory(dbCategory.getName(), priceFrom);
        } catch (ResourceNotFoundException dbMiss) {
            // Fallback to static registry (early-bootstrap or seeder hasn't run)
            try {
                CustomCategoryRegistry.Entry entry = categoryRegistry.getRequired(categoryId);
                log.warn("[CustomOrder] Category '{}' not in DB, using static registry fallback", categoryId);
                return new ResolvedCategory(entry.getName(), entry.getPriceFrom());
            } catch (ResourceNotFoundException registryMiss) {
                throw new ResourceNotFoundException("Custom category not found: " + categoryId);
            }
        }
    }

    private record ResolvedCategory(String name, BigDecimal priceFrom) {}

    private void validateSubmission(CustomOrderRequest req) {
        // Category must exist in either DB or registry
        resolveCategory(req.getCategoryId());

        // Style: must have a selection OR uploads
        boolean hasSelection = req.getSelectedStyleId() != null && !req.getSelectedStyleId().isBlank();
        boolean hasUploads = req.getReferenceImages() != null && !req.getReferenceImages().isEmpty();
        if (!hasSelection && !hasUploads) {
            throw new IllegalArgumentException(
                    "Pick a style from the gallery or upload at least one reference image");
        }

        // Size: validate per mode
        switch (req.getSizeMode()) {
            case CHART -> {
                if (req.getChartSize() == null || req.getChartSize().isBlank()) {
                    throw new IllegalArgumentException("Chart size is required when sizeMode=CHART");
                }
            }
            case MANUAL -> {
                long filledCount = req.getMeasurements().values().stream()
                        .filter(v -> v != null && !v.isBlank()).count();
                if (filledCount < 4) {
                    throw new IllegalArgumentException(
                            "Provide at least 4 measurements when sizeMode=MANUAL");
                }
            }
            case TAILOR_VISIT -> { /* no measurement data needed */ }
        }

        // Contact
        if (req.getCustomerName() == null || req.getCustomerName().isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        boolean hasWhatsApp = req.getWhatsappNumber() != null && !req.getWhatsappNumber().isBlank();
        boolean hasPhone = req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank();
        if (!hasWhatsApp && !hasPhone) {
            throw new IllegalArgumentException(
                    "Provide either a WhatsApp number or a phone number");
        }

        // Delivery
        if (req.getDeliveryMode() == DeliveryMode.NIGERIA) {
            if (req.getDeliveryAddress() == null
                    || req.getDeliveryAddress().getStreetAddress() == null
                    || req.getDeliveryAddress().getStreetAddress().isBlank()) {
                throw new IllegalArgumentException(
                        "Delivery address is required when delivery is outside Aba");
            }
        }
    }

    private List<String> resolveStyleImages(List<StyleImageInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return new ArrayList<>();

        List<String> resolved = new ArrayList<>(inputs.size());
        for (StyleImageInput input : inputs) {
            if (input.getUrl() != null && !input.getUrl().isBlank()) {
                resolved.add(input.getUrl());
            } else if (input.getDataUrl() != null && !input.getDataUrl().isBlank()) {
                StorageService.UploadResult result =
                        storageService.uploadDataUrl(input.getDataUrl(), STYLE_REF_FOLDER);
                resolved.add(result.url());
            }
        }
        return resolved;
    }

    // =========================================================================
    //  READS
    // =========================================================================

    public CustomOrder getByReference(String referenceNumber) {
        return repository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Custom order not found: " + referenceNumber));
    }

    public Page<CustomOrder> listAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Page<CustomOrder> listByStatus(CustomOrderStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    public Page<CustomOrder> listForCustomer(String customerId, Pageable pageable) {
        return repository.findByCustomerId(customerId, pageable);
    }

    // =========================================================================
    //  QUOTE
    // =========================================================================

    @Transactional
    public CustomOrder applyQuote(String referenceNumber, QuoteRequest request, String adminUserId) {
        CustomOrder order = getByReference(referenceNumber);

        if (order.getStatus() != CustomOrderStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Can only quote a SUBMITTED order. Current status: " + order.getStatus());
        }

        BigDecimal quoted = request.getQuotedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit = quoted.multiply(BigDecimal.valueOf(0.5)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = quoted.subtract(deposit).setScale(2, RoundingMode.HALF_UP);

        // Sanity check — uses DB-backed category info now
        ResolvedCategory category = resolveCategory(order.getCategoryId());
        if (quoted.compareTo(category.priceFrom()) < 0) {
            log.warn("[Quote] Order {} quoted at ₦{} which is below category 'from' price ₦{}",
                    referenceNumber, quoted, category.priceFrom());
        }

        PricingSpec pricing = order.getPricing() != null ? order.getPricing() : PricingSpec.builder().build();
        pricing.setQuotedAmount(quoted);
        pricing.setQuoteNotes(request.getQuoteNotes());
        pricing.setQuotedBy(adminUserId);
        pricing.setQuotedAt(Instant.now());
        pricing.setDepositAmount(deposit);
        pricing.setBalanceAmount(balance);
        order.setPricing(pricing);

        order.setStatus(CustomOrderStatus.QUOTED);
        appendHistory(order, CustomOrderStatus.QUOTED,
                "Quote issued: ₦" + quoted + " (deposit ₦" + deposit + ")",
                adminUserId);

        CustomOrder saved = repository.save(order);
        log.info("Custom order {} quoted at ₦{} by admin {}",
                referenceNumber, quoted, adminUserId);

        eventPublisher.publishEvent(new CustomOrderQuotedEvent(saved));
        return saved;
    }

    // =========================================================================
    //  STATUS TRANSITIONS
    // =========================================================================

    @Transactional
    public CustomOrder updateStatus(String referenceNumber, CustomOrderStatus newStatus, String note, String actor) {
        CustomOrder order = getByReference(referenceNumber);
        validateTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);
        appendHistory(order, newStatus, note, actor);
        return repository.save(order);
    }

    public Set<CustomOrderStatus> nextAllowed(CustomOrderStatus current) {
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
    }

    private void validateTransition(CustomOrderStatus current, CustomOrderStatus next) {
        Set<CustomOrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new IllegalStateException(
                    "Cannot transition from " + current + " to " + next
                            + ". Allowed: " + allowed);
        }
    }

    private void appendHistory(CustomOrder order, CustomOrderStatus status, String note, String actor) {
        if (order.getStatusHistory() == null) {
            order.setStatusHistory(new ArrayList<>());
        }
        order.getStatusHistory().add(CustomOrderStatusHistory.builder()
                .status(status)
                .note(note)
                .actor(actor)
                .timestamp(Instant.now())
                .build());
    }
}