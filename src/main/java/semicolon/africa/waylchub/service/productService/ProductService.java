package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.productDto.*;
import semicolon.africa.waylchub.event.StockChangedEvent;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.*;
import semicolon.africa.waylchub.repository.productRepository.*;
import semicolon.africa.waylchub.service.campaign.CampaignService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static semicolon.africa.waylchub.config.CacheConfig.PRODUCT_DETAIL_CACHE;
import static semicolon.africa.waylchub.config.CacheConfig.PRODUCTS_LIST_CACHE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductCategoryIndexer categoryIndexer;
    private final MongoTemplate mongoTemplate;
    private final CampaignService campaignService;
    private final ApplicationEventPublisher eventPublisher;
    private final org.springframework.cache.CacheManager cacheManager;
    // Self-reference through the Spring proxy. Calls made from WITHIN this class
    // to @Cacheable/@CacheEvict methods on this same class bypass the proxy and
    // silently skip the annotation — this field routes those calls back through
    // it. @Lazy is required: without it, Spring can't construct this bean while
    // also injecting it into itself (circular creation error at startup).
    @Lazy
    @Autowired
    private ProductService self;

    // =========================================================================
    // READS
    // =========================================================================

    @Cacheable(value = PRODUCT_DETAIL_CACHE, key = "'slug_' + #slug", sync = true)
    public Product getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
    }

    @Cacheable(value = PRODUCT_DETAIL_CACHE, key = "'byId_' + #id", sync = true)
    public Product getProductById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Page<Product> getProductsByCategorySlug(String slug, Pageable pageable) {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setCategorySlug(slug);
        return self.filterProducts(filter, pageable);
    }

    public Page<Product> getProductsByBrandSlug(String slug, Pageable pageable) {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setBrandSlug(slug);
        return self.filterProducts(filter, pageable);
    }

    public Page<Product> searchProducts(String keyword, Pageable pageable) {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setKeyword(keyword);
        return self.filterProducts(filter, pageable);
    }

    @Cacheable(
            value     = PRODUCTS_LIST_CACHE,
            condition = "#pageable.paged",
            unless    = "#result.content.size() == 0",
            key       = "(#filter.keyword       ?: '') + '_'"
                    + "+ (#filter.categorySlug  ?: '') + '_'"
                    + "+ (#filter.brandSlug   ?: '') + '_'"
                    + "+ (#filter.minPrice      ?: '') + '_'"
                    + "+ (#filter.maxPrice      ?: '') + '_'"
                    + "+ #pageable.pageNumber   + '_'"
                    + "+ #pageable.pageSize     + '_'"
                    + "+ (#pageable.sort.isSorted() ? #pageable.sort.toString() : 'unsorted')"
    )
    public Page<Product> filterProducts(ProductFilterRequest filter, Pageable pageable) {
        Query query = new Query();

        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(filter.getKeyword()));
        }

        // Matches products in this category, in any sub-category of it, and
        // products that are ALSO in it as one of their several categories.
        if (filter.getCategorySlug() != null && !filter.getCategorySlug().isBlank()) {
            Category category = categoryRepository.findBySlug(filter.getCategorySlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            query.addCriteria(Criteria.where("categoryLineageIds").in(category.getId()));
        }

        if (filter.getBrandSlug() != null) {
            query.addCriteria(Criteria.where("brandSlug").is(filter.getBrandSlug()));
        }

        if (filter.getMinPrice() != null)
            query.addCriteria(Criteria.where("minPrice").gte(filter.getMinPrice()));
        if (filter.getMaxPrice() != null)
            query.addCriteria(Criteria.where("maxPrice").lte(filter.getMaxPrice()));

        query.addCriteria(Criteria.where("isActive").is(true));

        query.fields().include(
                "name", "slug", "brandName", "categoryName", "categorySlug", "categorySlugs",
                "minPrice", "maxPrice", "basePrice", "totalStock", "discount",
                "compareAtPrice", "averageRating", "reviewCount", "images", "isActive"
        );

        List<Product> products = mongoTemplate.find(query.with(pageable), Product.class);
        return new RestPage<>(
                PageableExecutionUtils.getPage(products, pageable,
                        () -> mongoTemplate.count(query.skip(-1).limit(-1), Product.class))
        );
    }

    @Cacheable(value = PRODUCT_DETAIL_CACHE, key = "#productId", sync = true)
    public ProductDetailResponse getProductDetails(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        return ProductDetailResponse.builder()
                .product(product)
                .variants(variants)
                .build();
    }

    public List<Product> getProductsByIds(Collection<String> ids) {
        Query query = new Query(Criteria.where("id").in(ids));
        return mongoTemplate.find(query, Product.class);
    }

    // =========================================================================
    // WRITES
    // =========================================================================

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = PRODUCTS_LIST_CACHE,  allEntries = true),
            @CacheEvict(value = PRODUCT_DETAIL_CACHE, key = "#request.id ?: 'none'"),
            @CacheEvict(value = PRODUCT_DETAIL_CACHE, key = "'byId_' + (#request.id ?: 'none')"),
            @CacheEvict(value = PRODUCT_DETAIL_CACHE, key = "'slug_' + (#request.slug ?: 'none')")
    })
    public Product createOrUpdateProduct(ProductRequest request) {
        Product product = request.getId() != null
                ? productRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"))
                : new Product();

        String oldSlug = product.getSlug();

        product.setName(request.getName());
        product.setSlug(request.getSlug());
        product.setDescription(request.getDescription());
        product.setSpecifications(request.getSpecifications() != null
                ? request.getSpecifications() : new HashMap<>());
        product.setImages(request.getImages() != null
                ? request.getImages() : new ArrayList<>());

        if (request.getIsActive() != null) {
            product.setActive(request.getIsActive());
        }
        if (request.getTags() != null) {
            product.setTags(new HashSet<>(request.getTags()));
        }

        if (request.getDiscount() != null && request.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
            if (request.getDiscount().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Discount cannot exceed 100%");
            }
            BigDecimal originalPrice = request.getBasePrice();
            product.setCompareAtPrice(originalPrice);
            product.setDiscount(request.getDiscount().intValue());
            BigDecimal discountFactor = request.getDiscount()
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal amountOff = originalPrice.multiply(discountFactor);
            product.setBasePrice(originalPrice.subtract(amountOff)
                    .setScale(2, RoundingMode.HALF_UP));
        } else {
            product.setBasePrice(request.getBasePrice());
            product.setCompareAtPrice(request.getCompareAtPrice());
            product.setDiscount(0);
        }

        // ── Categories ──────────────────────────────────────────────────────
        // categorySlug  = main category (breadcrumb / card label)
        // categorySlugs = every category the product is in, e.g.
        //                 ["men-trousers", "women-trousers", "sale"]
        // If only categorySlug is sent (older admin form, seeder), it REPLACES the
        // old main category; the product's other categories are kept.
        if (request.getCategorySlug() != null || request.getCategorySlugs() != null) {
            Collection<String> all;
            if (request.getCategorySlugs() != null) {
                all = request.getCategorySlugs();
            } else {
                List<String> keep = new ArrayList<>(
                        product.getCategorySlugs() != null ? product.getCategorySlugs() : List.of());
                if (product.getCategorySlug() != null) keep.remove(product.getCategorySlug());
                all = keep;
            }
            categoryIndexer.apply(product, categoryIndexer.resolve(request.getCategorySlug(), all));
        }

        if (request.getBrandSlug() != null) {
            Brand brand = brandRepository.findBySlug(request.getBrandSlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
            product.setBrand(brand);
            product.setBrandName(brand.getName());
            product.setBrandSlug(brand.getSlug());
        }

        if (request.getVariantOptions() != null && !request.getVariantOptions().isEmpty()) {
            List<VariantOption> options = request.getVariantOptions().entrySet().stream()
                    .map(entry -> new VariantOption(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
            product.setVariantOptions(options);
        } else {
            product.setVariantOptions(new ArrayList<>());
        }

        Product saved = productRepository.save(product);

        if (oldSlug != null && !oldSlug.equals(saved.getSlug())) {
            try {
                Optional.ofNullable(cacheManager.getCache(PRODUCT_DETAIL_CACHE))
                        .ifPresent(c -> c.evict("slug_" + oldSlug));
            } catch (Exception e) {
                log.warn("[Cache] Manual slug evict failed for '{}': {}", oldSlug, e.getMessage());
            }
        }

        updateParentAggregates(saved.getId());
        return saved;
    }

    @Transactional
    @CacheEvict(value = PRODUCTS_LIST_CACHE, allEntries = true)
    public ProductVariant saveVariant(VariantRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent product not found"));

        validateVariantAttributes(product, request.getAttributes());

        boolean duplicateExists = variantRepository.findByProductId(product.getId()).stream()
                .anyMatch(v -> v.getAttributes().equals(request.getAttributes())
                        && !v.getId().equals(request.getId()));

        if (duplicateExists) {
            throw new IllegalArgumentException("A variant with these exact attributes already exists.");
        }

        ProductVariant variant = request.getId() != null
                ? variantRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"))
                : new ProductVariant();

        Integer oldStock = variant.getStockQuantity();

        variant.setProductId(product.getId());
        variant.setSku(request.getSku());
        variant.setPrice(request.getPrice());
        variant.setCompareAtPrice(request.getCompareAtPrice());
        variant.setStockQuantity(request.getStockQuantity());
        variant.setAttributes(request.getAttributes());
        variant.setImages(request.getImages());
        variant.setManageStock(true);

        if (request.getIsActive() != null) {
            variant.setActive(request.getIsActive());
        }

        boolean isNewVariant = request.getId() == null;
        if (isNewVariant && product.getActiveCampaignId() != null) {
            campaignService.applyActiveCampaignToNewVariant(variant, product.getActiveCampaignId());
        }

        ProductVariant saved = variantRepository.save(variant);

        evictProductCaches(product);

        int stockChange = (request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                - (oldStock != null ? oldStock : 0);
        eventPublisher.publishEvent(new StockChangedEvent(product.getId(), saved.getId(), stockChange));

        return saved;
    }


    /**
     * Saves ALL of a product's variants in one call (admin editor).
     *
     *  - Loads the product and its existing variants once.
     *  - Validates everything first; if anything is wrong, nothing is written.
     *  - New variants: one batch insert. Existing variants: saved only if changed.
     *  - Totals (min/max price, total stock) and caches are updated once at the end.
     *
     * Variants left out of the list are NOT deleted (same as before).
     */
    @CacheEvict(value = PRODUCTS_LIST_CACHE, allEntries = true)
    public List<ProductVariant> saveVariantsBulk(String productId, List<VariantRequest> requests) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        if (requests == null || requests.isEmpty()) return new ArrayList<>();

        List<ProductVariant> existing = variantRepository.findByProductId(productId);
        Map<String, ProductVariant> existingById = existing.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        // ── 1. Validate everything before writing anything ──────────────────
        Set<Map<String, String>> seenAttrs = new HashSet<>();
        Set<String> seenSkus = new HashSet<>();
        Set<String> requestIds = new HashSet<>();

        for (VariantRequest r : requests) {
            if (r.getProductId() != null && !r.getProductId().equals(productId)) {
                throw new IllegalArgumentException("Variant belongs to a different product.");
            }
            if (r.getSku() == null || r.getSku().isBlank()) {
                throw new IllegalArgumentException("Every variant needs a SKU.");
            }
            Map<String, String> attrs = r.getAttributes() != null ? r.getAttributes() : new HashMap<>();
            validateVariantAttributes(product, attrs);

            if (!seenAttrs.add(attrs)) {
                throw new IllegalArgumentException("Two variants have the same options: " + attrs);
            }
            if (!seenSkus.add(r.getSku().trim())) {
                throw new IllegalArgumentException("SKU used twice: " + r.getSku().trim());
            }
            if (r.getId() != null) {
                if (!existingById.containsKey(r.getId())) {
                    throw new ResourceNotFoundException("Variant not found on this product: " + r.getId());
                }
                requestIds.add(r.getId());
            }
        }

        // options must not clash with a variant that isn't in this request
        for (ProductVariant v : existing) {
            if (!requestIds.contains(v.getId()) && seenAttrs.contains(v.getAttributes())) {
                throw new IllegalArgumentException(
                        "A variant with these exact options already exists: " + v.getAttributes());
            }
        }

        // SKU must not be used by any other variant (any product) — one query
        Query skuQuery = new Query(Criteria.where("sku").in(seenSkus));
        if (!requestIds.isEmpty()) skuQuery.addCriteria(Criteria.where("id").nin(requestIds));
        skuQuery.fields().include("sku");
        List<ProductVariant> skuClashes = mongoTemplate.find(skuQuery, ProductVariant.class);
        if (!skuClashes.isEmpty()) {
            throw new IllegalArgumentException("SKU already in use: " +
                    skuClashes.stream().map(ProductVariant::getSku).collect(Collectors.joining(", ")));
        }

        // ── 2. Build the changes ─────────────────────────────────────────────
        List<ProductVariant> toInsert = new ArrayList<>();
        List<ProductVariant> toUpdate = new ArrayList<>();
        List<ProductVariant> result = new ArrayList<>();
        Map<ProductVariant, Integer> stockChange = new IdentityHashMap<>();

        for (VariantRequest r : requests) {
            boolean isNew = r.getId() == null;
            ProductVariant v = isNew ? new ProductVariant() : existingById.get(r.getId());
            Map<String, String> attrs = r.getAttributes() != null ? r.getAttributes() : new HashMap<>();
            int oldStock = v.getStockQuantity() != null ? v.getStockQuantity() : 0;
            int newStock = r.getStockQuantity() != null ? r.getStockQuantity() : 0;

            if (!isNew && isUnchanged(v, r, attrs)) {
                result.add(v);
                continue;
            }

            v.setProductId(productId);
            v.setSku(r.getSku().trim());
            v.setPrice(r.getPrice());
            v.setCompareAtPrice(r.getCompareAtPrice());
            v.setStockQuantity(r.getStockQuantity());
            v.setAttributes(attrs);
            v.setImages(r.getImages());
            v.setManageStock(true);
            if (r.getIsActive() != null) v.setActive(r.getIsActive());

            if (isNew && product.getActiveCampaignId() != null) {
                campaignService.applyActiveCampaignToNewVariant(v, product.getActiveCampaignId());
            }

            (isNew ? toInsert : toUpdate).add(v);
            result.add(v);
            if (newStock != oldStock) stockChange.put(v, newStock - oldStock);
        }

        // ── 3. Write: one batch insert, individual saves only for changed ones ──
        if (!toInsert.isEmpty()) {
            Collection<ProductVariant> inserted = mongoTemplate.insert(toInsert, ProductVariant.class);
            // insert() fills in the generated ids on the same objects
        }
        if (!toUpdate.isEmpty()) {
            variantRepository.saveAll(toUpdate);
        }

        // ── 4. Totals, caches, stock events — once ──────────────────────────
        evictProductCaches(product);
        updateParentAggregates(productId);
        stockChange.forEach((v, delta) ->
                eventPublisher.publishEvent(new StockChangedEvent(productId, v.getId(), delta)));

        log.info("[Variants] Bulk save for {}: {} new, {} updated, {} unchanged",
                product.getSlug(), toInsert.size(), toUpdate.size(),
                requests.size() - toInsert.size() - toUpdate.size());
        return result;
    }

    private static boolean isUnchanged(ProductVariant v, VariantRequest r, Map<String, String> attrs) {
        return Objects.equals(v.getSku(), r.getSku().trim())
                && sameAmount(v.getPrice(), r.getPrice())
                && sameAmount(v.getCompareAtPrice(), r.getCompareAtPrice())
                && Objects.equals(v.getStockQuantity(), r.getStockQuantity())
                && Objects.equals(v.getAttributes(), attrs)
                && Objects.equals(v.getImages(), r.getImages())
                && (r.getIsActive() == null || r.getIsActive() == v.isActive());
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }


    @Transactional
    @CacheEvict(value = PRODUCTS_LIST_CACHE, allEntries = true)
    public void deleteProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        variantRepository.deleteAll(variants);
        productRepository.deleteById(productId);

        evictProductCaches(product);
    }

    @Transactional
    @CacheEvict(value = PRODUCTS_LIST_CACHE, allEntries = true)
    public void deleteVariant(String variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        String productId = variant.getProductId();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        variantRepository.delete(variant);

        evictProductCaches(product);

        int stock = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
        eventPublisher.publishEvent(new StockChangedEvent(productId, variantId, -stock));
    }

    @Transactional
    @CacheEvict(value = PRODUCTS_LIST_CACHE, allEntries = true)
    public void addReview(String productId, int starRating) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        double currentTotal = p.getAverageRating() * p.getReviewCount();
        double newTotal = currentTotal + starRating;
        int newCount = p.getReviewCount() + 1;
        p.setReviewCount(newCount);
        p.setAverageRating(newTotal / newCount);
        productRepository.save(p);

        evictProductCaches(p);
    }

    @Transactional
    @CacheEvict(value = PRODUCTS_LIST_CACHE, allEntries = true)
    public void applyDiscount(String productId, BigDecimal newBasePrice) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getCompareAtPrice() == null) {
            product.setCompareAtPrice(product.getBasePrice());
        }
        product.setBasePrice(newBasePrice);
        productRepository.save(product);

        evictProductCaches(product);

        updateParentAggregates(productId);
    }

    public void reduceStockAtomic(String variantId, int quantity) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        if (!variant.isManageStock()) return;

        Query query = new Query(
                Criteria.where("id").is(variantId)
                        .and("stockQuantity").gte(quantity));
        Update update = new Update().inc("stockQuantity", -quantity);
        var result = mongoTemplate.updateFirst(query, update, ProductVariant.class);

        if (result.getModifiedCount() == 0) {
            throw new RuntimeException("Insufficient stock for variant: " + variantId);
        }
        eventPublisher.publishEvent(new StockChangedEvent(
                variant.getProductId(), variantId, -quantity));
    }

    public void addStockAtomic(String variantId, String productId, int quantity) {
        Query query = new Query(Criteria.where("id").is(variantId));
        Update update = new Update().inc("stockQuantity", quantity);
        var result = mongoTemplate.updateFirst(query, update, ProductVariant.class);

        if (result.getModifiedCount() == 0) {
            throw new ResourceNotFoundException(
                    "Variant not found or stock update failed: " + variantId);
        }
        eventPublisher.publishEvent(new StockChangedEvent(productId, variantId, quantity));
    }

    // =========================================================================
    // AGGREGATE HELPERS
    // =========================================================================

    public void updateParentAggregates(String productId) {
        List<ProductVariant> variants = variantRepository.findByProductId(productId);

        final BigDecimal newMin;
        final BigDecimal newMax;
        final int newTotalStock;

        if (variants.isEmpty()) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found during aggregate update: " + productId));
            BigDecimal base = product.getBasePrice() != null
                    ? product.getBasePrice() : BigDecimal.ZERO;
            newMin = base;
            newMax = base;
            newTotalStock = 0;
        } else {
            newTotalStock = variants.stream()
                    .filter(ProductVariant::isManageStock)
                    .mapToInt(v -> v.getStockQuantity() == null ? 0 : v.getStockQuantity())
                    .sum();
            newMin = variants.stream()
                    .map(ProductVariant::getPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            newMax = variants.stream()
                    .map(ProductVariant::getPrice)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
        }

        Query query = new Query(Criteria.where("id").is(productId));
        Update update = new Update()
                .set("totalStock", newTotalStock)
                .set("minPrice", newMin)
                .set("maxPrice", newMax);
        mongoTemplate.updateFirst(query, update, Product.class);

        evictProductCaches(productId);
        try {
            Optional.ofNullable(cacheManager.getCache(PRODUCTS_LIST_CACHE))
                    .ifPresent(org.springframework.cache.Cache::clear);
        } catch (Exception e) {
            log.warn("[Cache] Manual clear failed on '{}': {}",
                    PRODUCTS_LIST_CACHE, e.getMessage());
        }
    }

    // =========================================================================
    // CACHE HELPERS
    // =========================================================================

    /**
     * Evicts all three PRODUCT_DETAIL_CACHE keys for a given product
     * (bare id, byId_ prefix, slug_ prefix). They must always be evicted together.
     */
    private void evictProductCaches(Product product) {
        try {
            Optional.ofNullable(cacheManager.getCache(PRODUCT_DETAIL_CACHE))
                    .ifPresent(c -> {
                        c.evict(product.getId());
                        c.evict("byId_" + product.getId());
                        if (product.getSlug() != null) {
                            c.evict("slug_" + product.getSlug());
                        }
                    });
        } catch (Exception e) {
            log.warn("[Cache] Manual evict failed for product '{}': {}",
                    product.getId(), e.getMessage());
        }
    }

    private void evictProductCaches(String productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + productId));
            evictProductCaches(product);
        } catch (ResourceNotFoundException e) {
            throw e; // re-throw domain exceptions, don't swallow them
        } catch (Exception e) {
            log.warn("[Cache] Manual evict failed for productId '{}': {}",
                    productId, e.getMessage());
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private void validateVariantAttributes(Product product, Map<String, String> attributes) {
        if (product.getVariantOptions() == null || product.getVariantOptions().isEmpty()) return;

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            VariantOption option = product.getVariantOptions().stream()
                    .filter(o -> o.getName().equalsIgnoreCase(entry.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Invalid variant option: " + entry.getKey()));

            if (option.getValues() == null || !option.getValues().contains(entry.getValue())) {
                throw new IllegalArgumentException(
                        "Invalid value '" + entry.getValue() +
                                "' for option '" + entry.getKey() + "'");
            }
        }
    }
}