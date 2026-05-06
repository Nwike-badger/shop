package semicolon.africa.waylchub.service.customOrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.customOrderDto.CustomCategoryRequest;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.customOrder.CustomCategory;
import semicolon.africa.waylchub.model.customOrder.CustomOrder;
import semicolon.africa.waylchub.repository.customOrderRepository.CustomCategoryRepository;
import semicolon.africa.waylchub.repository.customOrderRepository.CustomStyleRepository;
import semicolon.africa.waylchub.service.storage.StorageService;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomCategoryService {

    /**
     * Comparator used by all list methods.
     * Null-safe: treats null sortOrder as 100 (the default), so legacy docs
     * without the field don't throw NPE.
     */
    private static final Comparator<CustomCategory> CATEGORY_ORDER =
            Comparator.comparingInt((CustomCategory c) ->
                            c.getSortOrder() != null ? c.getSortOrder() : 100)
                    .thenComparing(c -> c.getName() != null ? c.getName() : "");

    private final CustomCategoryRepository categoryRepository;
    private final CustomStyleRepository styleRepository;
    private final StorageService storageService;
    private final MongoTemplate mongoTemplate;

    // ─── Reads ───────────────────────────────────────────────────────────

    /**
     * Active categories, sorted for the /custom landing grid.
     *
     * Uses findByActiveTrue() — a simple, keyword-safe derived query.
     * Sorting is done in Java to avoid MongoDB query DSL keyword collisions
     * on the "sortOrder" field name ("Order" is a reserved Spring Data token).
     */
    public List<CustomCategory> listActive() {
        return categoryRepository.findByActiveTrue()
                .stream()
                .sorted(CATEGORY_ORDER)
                .toList();
    }

    /**
     * All categories including inactive — admin view.
     *
     * Uses inherited findAll() — the safest possible query.
     */
    public List<CustomCategory> listAll() {
        return categoryRepository.findAll()
                .stream()
                .sorted(CATEGORY_ORDER)
                .toList();
    }

    public CustomCategory getBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Custom category not found: " + slug));
    }

    /**
     * Number of custom orders referencing this category slug.
     * Used by admin list view (badge) and delete-block enforcement.
     */
    public long countOrdersForCategory(String categorySlug) {
        Query q = new Query(Criteria.where("categoryId").is(categorySlug));
        return mongoTemplate.count(q, CustomOrder.class);
    }

    // ─── Writes ──────────────────────────────────────────────────────────

    @Transactional
    public CustomCategory create(CustomCategoryRequest request) {
        if (request.getSlug() == null || request.getSlug().isBlank()) {
            throw new IllegalArgumentException("Slug is required when creating a category");
        }
        String slug = request.getSlug().trim().toLowerCase();

        if (categoryRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException(
                    "A category with slug '" + slug + "' already exists");
        }

        CustomCategory category = CustomCategory.builder()
                .slug(slug)
                .name(request.getName() != null ? request.getName().trim() : "")
                .tagline(request.getTagline())
                .description(request.getDescription())
                .genderHint(request.getGenderHint() != null ? request.getGenderHint() : "unisex")
                .priceFrom(request.getPriceFrom())
                .leadTime(request.getLeadTime())
                .accent(normaliseAccent(request.getAccent()))
                .coverImageUrl(request.getCoverImageUrl())
                .coverImagePublicId(request.getCoverImagePublicId())
                .measurementSet(request.getMeasurementSet())
                .silhouettePath(request.getSilhouettePath())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100)
                .active(request.getActive() != null ? request.getActive() : Boolean.TRUE)
                .build();

        CustomCategory saved = categoryRepository.save(category);
        log.info("[CustomCatalog] Created category {} ({})", saved.getSlug(), saved.getName());
        return saved;
    }

    @Transactional
    public CustomCategory update(String slug, CustomCategoryRequest request) {
        CustomCategory existing = getBySlug(slug);
        String oldPublicId = existing.getCoverImagePublicId();

        // Only overwrite fields that were explicitly sent (null = "don't change")
        if (request.getName() != null && !request.getName().isBlank())
            existing.setName(request.getName().trim());
        if (request.getTagline() != null)
            existing.setTagline(request.getTagline());
        if (request.getDescription() != null)
            existing.setDescription(request.getDescription());
        if (request.getGenderHint() != null)
            existing.setGenderHint(request.getGenderHint());
        if (request.getPriceFrom() != null)
            existing.setPriceFrom(request.getPriceFrom());
        if (request.getLeadTime() != null)
            existing.setLeadTime(request.getLeadTime());
        if (request.getAccent() != null)
            existing.setAccent(normaliseAccent(request.getAccent()));
        if (request.getCoverImageUrl() != null)
            existing.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getCoverImagePublicId() != null)
            existing.setCoverImagePublicId(request.getCoverImagePublicId());
        if (request.getMeasurementSet() != null)
            existing.setMeasurementSet(request.getMeasurementSet());
        if (request.getSilhouettePath() != null)
            existing.setSilhouettePath(request.getSilhouettePath());
        if (request.getSortOrder() != null)
            existing.setSortOrder(request.getSortOrder());
        if (request.getActive() != null)
            existing.setActive(request.getActive());

        CustomCategory saved = categoryRepository.save(existing);

        // Best-effort Cloudinary cleanup when the cover image is swapped
        String newPublicId = saved.getCoverImagePublicId();
        boolean publicIdChanged = oldPublicId != null && !oldPublicId.isBlank()
                && !oldPublicId.equals(newPublicId);
        if (publicIdChanged) {
            try {
                storageService.delete(oldPublicId);
                log.info("[CustomCatalog] Cleaned up orphaned Cloudinary asset {}", oldPublicId);
            } catch (Exception e) {
                log.warn("[CustomCatalog] Could not delete old Cloudinary asset {}: {}",
                        oldPublicId, e.getMessage());
            }
        }

        log.info("[CustomCatalog] Updated category {}", saved.getSlug());
        return saved;
    }

    /**
     * Hard delete — blocked if orders exist (admin must deactivate instead).
     * Cascades to styles.
     */
    @Transactional
    public void delete(String slug) {
        CustomCategory category = getBySlug(slug);

        long orderCount = countOrdersForCategory(slug);
        if (orderCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete category '" + slug + "' — " + orderCount
                            + " order(s) reference it. Deactivate instead.");
        }

        long styleCount = styleRepository.countByCategorySlug(slug);
        if (styleCount > 0) {
            styleRepository.deleteByCategorySlug(slug);
            log.info("[CustomCatalog] Cascade-deleted {} style(s) for category {}",
                    styleCount, slug);
        }

        if (category.getCoverImagePublicId() != null
                && !category.getCoverImagePublicId().isBlank()) {
            try {
                storageService.delete(category.getCoverImagePublicId());
            } catch (Exception e) {
                log.warn("[CustomCatalog] Could not delete cover image for {}: {}",
                        slug, e.getMessage());
            }
        }

        categoryRepository.delete(category);
        log.info("[CustomCatalog] Deleted category {}", slug);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /** Ensures accent hex always has a leading "#". */
    private String normaliseAccent(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }
}