package semicolon.africa.waylchub.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.campaignDto.CampaignRequest;
import semicolon.africa.waylchub.dto.campaignDto.CampaignResponse;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.campaign.Campaign;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.campaign.CampaignRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final MongoTemplate mongoTemplate;

    // ══════════════════════════════════════════════════════════════════════════
    // SCHEDULER
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Fires every minute.
     *
     * If running multiple app instances: add ShedLock (see README) so only one
     * instance runs this at a time.
     *
     * The @Version field on Campaign handles the residual race between this
     * scheduler and a simultaneous manualActivate() call. The first thread to
     * mark the campaign active wins; the second gets OptimisticLockingFailureException,
     * which we catch and swallow because the job is already done.
     */
    @Scheduled(cron = "0 * * * * *")
    public void processCampaigns() {
        Instant now = Instant.now();

        campaignRepository
                .findByActiveFalseAndStartDateLessThanEqualAndEndDateGreaterThan(now, now)
                .forEach(campaign -> {
                    try {
                        activateCampaign(campaign);
                    } catch (OptimisticLockingFailureException e) {
                        // Another instance or thread already activated this campaign. Safe to ignore.
                        log.debug("Campaign [{}] already activated by another thread — skipping.", campaign.getId());
                    }
                });

        campaignRepository
                .findByActiveTrueAndEndDateLessThanEqual(now)
                .forEach(campaign -> {
                    try {
                        deactivateCampaign(campaign);
                    } catch (OptimisticLockingFailureException e) {
                        log.debug("Campaign [{}] already deactivated by another thread — skipping.", campaign.getId());
                    }
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN CRUD
    // ══════════════════════════════════════════════════════════════════════════

    public CampaignResponse createCampaign(CampaignRequest request) {
        validateRequest(request);
        Campaign campaign = Campaign.builder()
                .name(request.getName())
                .description(request.getDescription())
                .targetCategorySlug(request.getTargetCategorySlug())
                .targetBrandSlug(request.getTargetBrandSlug())
                .targetTag(request.getTargetTag())
                .targetProductIds(request.getTargetProductIds())
                .targetVariantIds(request.getTargetVariantIds())
                .discountPercentage(request.getDiscountPercentage())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();
        return toResponse(campaignRepository.save(campaign));
    }

    public CampaignResponse updateCampaign(String id, CampaignRequest request) {
        Campaign campaign = findById(id);
        if (campaign.isActive()) {
            throw new IllegalStateException("Cannot edit an active campaign. Deactivate it first.");
        }
        validateRequest(request);
        campaign.setName(request.getName());
        campaign.setDescription(request.getDescription());
        campaign.setTargetCategorySlug(request.getTargetCategorySlug());
        campaign.setTargetBrandSlug(request.getTargetBrandSlug());
        campaign.setTargetTag(request.getTargetTag());
        campaign.setTargetProductIds(request.getTargetProductIds());
        campaign.setTargetVariantIds(request.getTargetVariantIds());
        campaign.setDiscountPercentage(request.getDiscountPercentage());
        campaign.setStartDate(request.getStartDate());
        campaign.setEndDate(request.getEndDate());
        return toResponse(campaignRepository.save(campaign));
    }

    public void deleteCampaign(String id) {
        Campaign campaign = findById(id);
        if (campaign.isActive()) {
            deactivateCampaign(campaign);
        }
        campaignRepository.deleteById(id);
    }

    public CampaignResponse manualActivate(String id) {
        Campaign campaign = findById(id);
        if (campaign.isActive()) throw new IllegalStateException("Campaign is already active.");
        activateCampaign(campaign);
        return toResponse(findById(id));
    }

    public CampaignResponse manualDeactivate(String id) {
        Campaign campaign = findById(id);
        if (!campaign.isActive()) throw new IllegalStateException("Campaign is not active.");
        deactivateCampaign(campaign);
        return toResponse(findById(id));
    }

    public List<CampaignResponse> getAllCampaigns() {
        return campaignRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public CampaignResponse getCampaign(String id) {
        return toResponse(findById(id));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CORE ENGINE
    // ══════════════════════════════════════════════════════════════════════════

    void activateCampaign(Campaign campaign) {
        log.info("Activating campaign [{}]: {}", campaign.getId(), campaign.getName());

        if (campaign.isVariantLevel()) {
            activateVariantLevelCampaign(campaign);
        } else {
            activateProductLevelCampaign(campaign);
        }
    }

    void deactivateCampaign(Campaign campaign) {
        log.info("Deactivating campaign [{}]: {}", campaign.getId(), campaign.getName());

        if (campaign.isVariantLevel()) {
            deactivateVariantLevelCampaign(campaign);
        } else {
            deactivateProductLevelCampaign(campaign);
        }
    }

    // ── Product-level activation ───────────────────────────────────────────────

    private void activateProductLevelCampaign(Campaign campaign) {
        Query targetQuery = buildProductTargetQuery(campaign);
        // ✅ FIX: exclude products already owned by another active campaign
        targetQuery.addCriteria(Criteria.where("activeCampaignId").isNull());

        List<Product> targetProducts = mongoTemplate.find(targetQuery, Product.class);
        if (targetProducts.isEmpty()) {
            log.warn("Campaign [{}] matched 0 products.", campaign.getId());
            markActive(campaign);
            return;
        }

        Set<String> productIds = targetProducts.stream().map(Product::getId).collect(Collectors.toSet());

        // ✅ FIX: single batch query — no N+1
        Map<String, List<ProductVariant>> variantsByProduct = batchFetchVariants(productIds);

        BigDecimal factor = discountFactor(campaign.getDiscountPercentage());

        BulkOperations productOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Product.class);
        BulkOperations variantOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ProductVariant.class);
        int variantOpCount = 0;

        for (Product product : targetProducts) {
            BigDecimal base = coalesce(product.getBasePrice(), BigDecimal.ZERO);
            productOps.updateOne(
                    Query.query(Criteria.where("id").is(product.getId())),
                    new Update()
                            .set("activeCampaignId", campaign.getId())
                            .set("originalBasePrice", base)  // safe backup — NOT compareAtPrice
                            .set("basePrice", base.multiply(factor).setScale(2, RoundingMode.HALF_UP))
            );

            for (ProductVariant variant : variantsByProduct.getOrDefault(product.getId(), List.of())) {
                if (variant.getPrice() == null) continue;
                // Skip variants already locked by a variant-level campaign
                if (variant.getActiveCampaignId() != null) continue;

                variantOps.updateOne(
                        Query.query(Criteria.where("id").is(variant.getId())),
                        new Update()
                                .set("activeCampaignId", campaign.getId())
                                .set("originalPrice", variant.getPrice())  // safe backup
                                .set("price", variant.getPrice().multiply(factor).setScale(2, RoundingMode.HALF_UP))
                );
                variantOpCount++;
            }
        }

        productOps.execute();
        if (variantOpCount > 0) variantOps.execute(); // ✅ FIX: guard on actual op count

        markActive(campaign);
        log.info("Campaign [{}] activated: {} products, {} variants.", campaign.getId(), targetProducts.size(), variantOpCount);
    }

    private void deactivateProductLevelCampaign(Campaign campaign) {
        List<Product> products = mongoTemplate.find(
                Query.query(Criteria.where("activeCampaignId").is(campaign.getId())),
                Product.class);

        if (products.isEmpty()) { markInactive(campaign); return; }

        Set<String> productIds = products.stream().map(Product::getId).collect(Collectors.toSet());
        Map<String, List<ProductVariant>> variantsByProduct = batchFetchVariants(productIds);

        BulkOperations productOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Product.class);
        BulkOperations variantOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ProductVariant.class);
        int variantOpCount = 0;

        for (Product product : products) {
            BigDecimal restore = coalesce(product.getOriginalBasePrice(), product.getBasePrice());
            productOps.updateOne(
                    Query.query(Criteria.where("id").is(product.getId())),
                    new Update()
                            .unset("activeCampaignId")
                            .set("basePrice", restore)
                            .unset("originalBasePrice")
            );

            for (ProductVariant variant : variantsByProduct.getOrDefault(product.getId(), List.of())) {
                // Only restore variants that belong to THIS campaign
                if (!campaign.getId().equals(variant.getActiveCampaignId())) continue;

                BigDecimal restorePrice = coalesce(variant.getOriginalPrice(), variant.getPrice());
                variantOps.updateOne(
                        Query.query(Criteria.where("id").is(variant.getId())),
                        new Update()
                                .unset("activeCampaignId")
                                .set("price", restorePrice)
                                .unset("originalPrice")
                );
                variantOpCount++;
            }
        }

        productOps.execute();
        if (variantOpCount > 0) variantOps.execute();

        markInactive(campaign);
        log.info("Campaign [{}] deactivated: {} products restored.", campaign.getId(), products.size());
    }

    // ── Variant-level activation ───────────────────────────────────────────────

    /**
     * Discounts only the specified variants. The parent product's basePrice
     * is NOT changed — only product.minPrice/maxPrice aggregates are refreshed.
     */
    private void activateVariantLevelCampaign(Campaign campaign) {
        // Exclude variants already owned by another campaign
        Query variantQuery = Query.query(
                Criteria.where("id").in(campaign.getTargetVariantIds())
                        .and("activeCampaignId").isNull()
                        .and("isActive").is(true)
        );

        List<ProductVariant> targetVariants = mongoTemplate.find(variantQuery, ProductVariant.class);
        if (targetVariants.isEmpty()) {
            log.warn("Variant-level campaign [{}] matched 0 variants.", campaign.getId());
            markActive(campaign);
            return;
        }

        BigDecimal factor = discountFactor(campaign.getDiscountPercentage());
        BulkOperations variantOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ProductVariant.class);

        for (ProductVariant variant : targetVariants) {
            if (variant.getPrice() == null) continue;
            variantOps.updateOne(
                    Query.query(Criteria.where("id").is(variant.getId())),
                    new Update()
                            .set("activeCampaignId", campaign.getId())
                            .set("originalPrice", variant.getPrice())
                            .set("price", variant.getPrice().multiply(factor).setScale(2, RoundingMode.HALF_UP))
            );
        }

        variantOps.execute();

        // Refresh minPrice/maxPrice on affected parent products
        refreshAggregatesForVariants(targetVariants);

        markActive(campaign);
        log.info("Variant-level campaign [{}] activated: {} variants.", campaign.getId(), targetVariants.size());
    }

    private void deactivateVariantLevelCampaign(Campaign campaign) {
        List<ProductVariant> variants = mongoTemplate.find(
                Query.query(Criteria.where("activeCampaignId").is(campaign.getId())),
                ProductVariant.class);

        if (variants.isEmpty()) { markInactive(campaign); return; }

        BulkOperations variantOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ProductVariant.class);

        for (ProductVariant variant : variants) {
            BigDecimal restore = coalesce(variant.getOriginalPrice(), variant.getPrice());
            variantOps.updateOne(
                    Query.query(Criteria.where("id").is(variant.getId())),
                    new Update()
                            .unset("activeCampaignId")
                            .set("price", restore)
                            .unset("originalPrice")
            );
        }

        variantOps.execute();
        refreshAggregatesForVariants(variants);

        markInactive(campaign);
        log.info("Variant-level campaign [{}] deactivated: {} variants restored.", campaign.getId(), variants.size());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GHOST VARIANT HOOK — called by ProductService.saveVariant()
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * CRITICAL FIX: Ghost Variant protection (Stale Memory fix applied).
     *
     * Mutates the variant object IN MEMORY before ProductService saves it.
     * This means:
     *   - Only ONE database write ever happens (via variantRepository.save)
     *   - The returned JSON reflects the discounted price immediately — no refresh needed
     *   - Deactivation restores correctly because originalPrice holds the admin's intended price
     *
     * Called by ProductService.saveVariant() BEFORE variantRepository.save(),
     * only when the parent product has an activeCampaignId set and this is a new variant.
     *
     * Skipped for variant-level campaigns: the admin explicitly chose which variants
     * to target, so newly added variants are intentionally excluded.
     */
    public void applyActiveCampaignToNewVariant(ProductVariant variant, String activeCampaignId) {
        Campaign campaign = campaignRepository.findById(activeCampaignId).orElse(null);
        if (campaign == null || !campaign.isActive() || campaign.isVariantLevel()) return;

        BigDecimal price = variant.getPrice();
        if (price == null) return;

        BigDecimal factor = discountFactor(campaign.getDiscountPercentage());

        // Mutate the Java object — ProductService.saveVariant() persists it in one write
        variant.setActiveCampaignId(campaign.getId());
        variant.setOriginalPrice(price);   // backup the admin's intended full price
        variant.setPrice(price.multiply(factor).setScale(2, RoundingMode.HALF_UP));

        log.info("Ghost variant fix: applied campaign [{}] discount to new variant in memory.", campaign.getId());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * CRITICAL FIX: Multiple addCriteria() calls are implicitly ANDed by Spring
     * Data MongoDB — this is exactly what we want.
     *
     * Category="Laptops" + Brand="Apple" → Apple Laptops only (AND intersection),
     * NOT all Laptops + all Apple products (OR union).
     */
    Query buildProductTargetQuery(Campaign campaign) {
        Query query = new Query();
        boolean hasCriteria = false;

        if (campaign.getTargetCategorySlug() != null) {
            query.addCriteria(Criteria.where("categorySlug").is(campaign.getTargetCategorySlug()));
            hasCriteria = true;
        }
        if (campaign.getTargetBrandSlug() != null) {
            query.addCriteria(Criteria.where("brandName").is(campaign.getTargetBrandSlug()));
            hasCriteria = true;
        }
        if (campaign.getTargetTag() != null) {
            query.addCriteria(Criteria.where("tags").is(campaign.getTargetTag()));
            hasCriteria = true;
        }
        if (campaign.getTargetProductIds() != null && !campaign.getTargetProductIds().isEmpty()) {
            query.addCriteria(Criteria.where("id").in(campaign.getTargetProductIds()));
            hasCriteria = true;
        }

        if (!hasCriteria) {
            throw new IllegalStateException("Campaign [" + campaign.getId() + "] has no targeting rules.");
        }

        query.addCriteria(Criteria.where("isActive").is(true));
        return query;
    }

    private Map<String, List<ProductVariant>> batchFetchVariants(Set<String> productIds) {
        return mongoTemplate
                .find(Query.query(Criteria.where("productId").in(productIds)), ProductVariant.class)
                .stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));
    }

    /**
     * Refreshes minPrice and maxPrice on parent products after variant-level
     * price changes, so that storefront sorting/filtering stays correct.
     */
    private void refreshAggregatesForVariants(List<ProductVariant> variants) {
        Set<String> productIds = variants.stream()
                .map(ProductVariant::getProductId)
                .collect(Collectors.toSet());

        Map<String, List<ProductVariant>> allVariantsByProduct = batchFetchVariants(productIds);

        BulkOperations productOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Product.class);

        for (String productId : productIds) {
            List<ProductVariant> all = allVariantsByProduct.getOrDefault(productId, List.of());

            OptionalDouble min = all.stream()
                    .filter(v -> v.getPrice() != null)
                    .mapToDouble(v -> v.getPrice().doubleValue())
                    .min();
            OptionalDouble max = all.stream()
                    .filter(v -> v.getPrice() != null)
                    .mapToDouble(v -> v.getPrice().doubleValue())
                    .max();

            Update update = new Update();
            min.ifPresent(m -> update.set("minPrice", BigDecimal.valueOf(m).setScale(2, RoundingMode.HALF_UP)));
            max.ifPresent(m -> update.set("maxPrice", BigDecimal.valueOf(m).setScale(2, RoundingMode.HALF_UP)));

            if (min.isPresent()) {
                productOps.updateOne(Query.query(Criteria.where("id").is(productId)), update);
            }
        }

        productOps.execute();
    }

    private BigDecimal discountFactor(BigDecimal percentage) {
        return BigDecimal.ONE.subtract(
                percentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    private BigDecimal coalesce(BigDecimal primary, BigDecimal fallback) {
        return primary != null ? primary : fallback;
    }

    private void markActive(Campaign campaign) {
        campaign.setActive(true);
        campaignRepository.save(campaign); // @Version incremented here
    }

    private void markInactive(Campaign campaign) {
        campaign.setActive(false);
        campaignRepository.save(campaign);
    }

    private Campaign findById(String id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + id));
    }

    private void validateRequest(CampaignRequest request) {
        boolean hasProductTargeting =
                request.getTargetCategorySlug() != null ||
                        request.getTargetBrandSlug() != null ||
                        request.getTargetTag() != null ||
                        (request.getTargetProductIds() != null && !request.getTargetProductIds().isEmpty());

        boolean hasVariantTargeting =
                request.getTargetVariantIds() != null && !request.getTargetVariantIds().isEmpty();

        if (!hasProductTargeting && !hasVariantTargeting) {
            throw new IllegalArgumentException(
                    "Campaign must target at least one category, brand, tag, product list, or variant list.");
        }

        if (hasProductTargeting && hasVariantTargeting) {
            throw new IllegalArgumentException(
                    "Campaign cannot mix product-level and variant-level targeting. " +
                            "Create two separate campaigns instead.");
        }

        if (request.getEndDate() != null && request.getStartDate() != null
                && !request.getEndDate().isAfter(request.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date.");
        }
    }

    private CampaignResponse toResponse(Campaign c) {
        int affectedProducts = (int) mongoTemplate.count(
                Query.query(Criteria.where("activeCampaignId").is(c.getId())),
                Product.class);
        int affectedVariants = (int) mongoTemplate.count(
                Query.query(Criteria.where("activeCampaignId").is(c.getId())),
                ProductVariant.class);

        return CampaignResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .targetCategorySlug(c.getTargetCategorySlug())
                .targetBrandSlug(c.getTargetBrandSlug())
                .targetTag(c.getTargetTag())
                .targetProductIds(c.getTargetProductIds())
                .targetVariantIds(c.getTargetVariantIds())
                .discountPercentage(c.getDiscountPercentage())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .active(c.isActive())
                .affectedProductCount(affectedProducts)
                .affectedVariantCount(affectedVariants)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}