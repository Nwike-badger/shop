package semicolon.africa.waylchub.service.customOrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.customOrderDto.CustomStyleRequest;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.customOrder.CustomStyle;
import semicolon.africa.waylchub.repository.customOrderRepository.CustomStyleRepository;
import semicolon.africa.waylchub.service.storage.StorageService;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomStyleService {

    /** Null-safe comparator on sortOrder then name. */
    private static final Comparator<CustomStyle> STYLE_ORDER =
            Comparator.comparingInt((CustomStyle s) ->
                            s.getSortOrder() != null ? s.getSortOrder() : 100)
                    .thenComparing(s -> s.getName() != null ? s.getName() : "");

    private final CustomStyleRepository styleRepository;
    private final CustomCategoryService categoryService;
    private final StorageService storageService;

    // ─── Reads ───────────────────────────────────────────────────────────

    /**
     * Active styles for a category — customer-facing wizard gallery.
     * Uses simple derived query (categorySlug + activeTrue) — both field names
     * are keyword-safe. Sorted in Java to avoid "sortOrder" / "Order" keyword issue.
     */
    public List<CustomStyle> listActiveForCategory(String categorySlug) {
        return styleRepository.findByCategorySlugAndActiveTrue(categorySlug)
                .stream()
                .sorted(STYLE_ORDER)
                .toList();
    }

    /**
     * All styles for a category including inactive — admin view.
     */
    public List<CustomStyle> listAllForCategory(String categorySlug) {
        return styleRepository.findByCategorySlug(categorySlug)
                .stream()
                .sorted(STYLE_ORDER)
                .toList();
    }

    public CustomStyle getBySlug(String slug) {
        return styleRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Custom style not found: " + slug));
    }

    // ─── Writes ──────────────────────────────────────────────────────────

    @Transactional
    public CustomStyle create(String categorySlug, CustomStyleRequest request) {
        // Validate parent category exists before creating orphan style
        categoryService.getBySlug(categorySlug);

        if (request.getSlug() == null || request.getSlug().isBlank()) {
            throw new IllegalArgumentException("Slug is required when creating a style");
        }
        String slug = request.getSlug().trim().toLowerCase();
        if (styleRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException(
                    "A style with slug '" + slug + "' already exists");
        }

        CustomStyle style = CustomStyle.builder()
                .slug(slug)
                .categorySlug(categorySlug)
                .name(request.getName() != null ? request.getName().trim() : "")
                .tone(request.getTone())
                .imageUrl(request.getImageUrl())
                .imagePublicId(request.getImagePublicId())
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100)
                .active(request.getActive() != null ? request.getActive() : Boolean.TRUE)
                .build();

        CustomStyle saved = styleRepository.save(style);
        log.info("[CustomCatalog] Created style {} under category {}",
                saved.getSlug(), categorySlug);
        return saved;
    }

    @Transactional
    public CustomStyle update(String slug, CustomStyleRequest request) {
        CustomStyle existing = getBySlug(slug);
        String oldPublicId = existing.getImagePublicId();

        if (request.getName() != null && !request.getName().isBlank())
            existing.setName(request.getName().trim());
        if (request.getTone() != null)
            existing.setTone(request.getTone());
        if (request.getImageUrl() != null)
            existing.setImageUrl(request.getImageUrl());
        if (request.getImagePublicId() != null)
            existing.setImagePublicId(request.getImagePublicId());
        if (request.getDescription() != null)
            existing.setDescription(request.getDescription());
        if (request.getSortOrder() != null)
            existing.setSortOrder(request.getSortOrder());
        if (request.getActive() != null)
            existing.setActive(request.getActive());

        CustomStyle saved = styleRepository.save(existing);

        // Cleanup orphaned Cloudinary asset on image swap
        String newPublicId = saved.getImagePublicId();
        boolean publicIdChanged = oldPublicId != null && !oldPublicId.isBlank()
                && !oldPublicId.equals(newPublicId);
        if (publicIdChanged) {
            try {
                storageService.delete(oldPublicId);
            } catch (Exception e) {
                log.warn("[CustomCatalog] Could not delete old style image {}: {}",
                        oldPublicId, e.getMessage());
            }
        }

        log.info("[CustomCatalog] Updated style {}", slug);
        return saved;
    }

    @Transactional
    public void delete(String slug) {
        CustomStyle style = getBySlug(slug);

        if (style.getImagePublicId() != null && !style.getImagePublicId().isBlank()) {
            try {
                storageService.delete(style.getImagePublicId());
            } catch (Exception e) {
                log.warn("[CustomCatalog] Could not delete image for style {}: {}",
                        slug, e.getMessage());
            }
        }

        styleRepository.delete(style);
        log.info("[CustomCatalog] Deleted style {}", slug);
    }
}