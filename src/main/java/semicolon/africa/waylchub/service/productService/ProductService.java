package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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

/**
 * CONFIRMED INDEX STATUS (from model inspection):
 *
 *   ✅ Text search index — already defined on the Product model via:
 *      @CompoundIndex(def = "{'name': 'text', 'description': 'text', 'brandName': 'text'}")
 *      TextCriteria in filterProducts() will work correctly out of the box.
 *
 *   ✅ Duplicate variant prevention — compound unique index added to ProductVariant:
 *      @CompoundIndex(def = "{'productId': 1, 'attributes': 1}", unique = true)
 *      MongoDB will reject duplicate attribute combinations with DuplicateKeyException.
 *      Add a @ExceptionHandler(DuplicateKeyException.class) in your controller advice
 *      to return a clean 409 Conflict response.
 *
 *   ⚠️  Both Product and ProductVariant use @Version for optimistic locking.
 *      updateParentAggregates() uses atomic $set via mongoTemplate to bypass @Version
 *      entirely — safe under concurrent StockChangedEvent processing.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final MongoTemplate mongoTemplate;
    private final CampaignService campaignService;
    private final ApplicationEventPublisher eventPublisher;

    // =========================================================================
    // READS
    // =========================================================================

    public Product getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
    }

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
        return filterProducts(filter, pageable);
    }

    public List<Product> searchProducts(String keyword) {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setKeyword(keyword);
        return filterProducts(filter, Pageable.unpaged()).getContent();
    }

    public Page<Product> filterProducts(ProductFilterRequest filter, Pageable pageable) {
        Query query = new Query();

        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            // IMPORTANT: This requires a MongoDB text index on the Product collection.
            // If no text index exists, this throws an exception at runtime.
            // See the class-level Javadoc for the required index definition.
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(filter.getKeyword()));
        }

        if (filter.getCategorySlug() != null) {
            Category category = categoryRepository.findBySlug(filter.getCategorySlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

            // Matches the exact category OR any product whose lineage path includes it
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("category.id").is(category.getId()),
                    Criteria.where("categoryLineage").regex("," + category.getId() + ",")
            ));
        }

        if (filter.getMinPrice() != null)
            query.addCriteria(Criteria.where("minPrice").gte(filter.getMinPrice()));
        if (filter.getMaxPrice() != null)
            query.addCriteria(Criteria.where("maxPrice").lte(filter.getMaxPrice()));

        query.addCriteria(Criteria.where("isActive").is(true));

        List<Product> products = mongoTemplate.find(query.with(pageable), Product.class);

        // Reset skip/limit for the count query to avoid incorrect totals
        long count = mongoTemplate.count(query.skip(-1).limit(-1), Product.class);

        return PageableExecutionUtils.getPage(products, pageable, () -> count);
    }

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
    public Product createOrUpdateProduct(ProductRequest request) {
        Product product = request.getId() != null
                ? productRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"))
                : new Product();

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

        // Pricing + discount logic
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

        if (request.getCategorySlug() != null) {
            Category cat = categoryRepository.findBySlug(request.getCategorySlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            product.setCategory(cat);
            product.setCategoryName(cat.getName());
            product.setCategorySlug(cat.getSlug());
            product.setCategoryLineage(cat.getLineage());
        }

        if (request.getBrandSlug() != null) {
            Brand brand = brandRepository.findBySlug(request.getBrandSlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
            product.setBrand(brand);
            product.setBrandName(brand.getName());
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

        // Immediately sync price aggregates so the homepage reflects the new
        // basePrice without waiting for a variant save event
        updateParentAggregates(saved.getId());
        return saved;
    }

    @Transactional
    public ProductVariant saveVariant(VariantRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent product not found"));

        validateVariantAttributes(product, request.getAttributes());

        // NOTE ON RACE CONDITION:
        // This in-memory duplicate check is not concurrency-safe — two simultaneous
        // requests can both pass this check and create duplicates.
        //
        // The reliable fix is a compound unique index at the MongoDB level:
        //   @CompoundIndex(def = "{'productId': 1, 'attributes': 1}", unique = true)
        // on the ProductVariant document class. MongoDB will then reject the second
        // insert with a DuplicateKeyException, which you should catch and translate
        // to a 409 Conflict response in your exception handler.
        //
        // The in-memory check below is kept as an early-exit optimisation for the
        // common (non-concurrent) case.
        boolean duplicateExists = variantRepository.findByProductId(product.getId()).stream()
                .anyMatch(v -> v.getAttributes().equals(request.getAttributes())
                        && !v.getId().equals(request.getId()));

        if (duplicateExists) {
            throw new IllegalArgumentException(
                    "A variant with these exact attributes already exists.");
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

        int stockChange = (request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                - (oldStock != null ? oldStock : 0);

        eventPublisher.publishEvent(new StockChangedEvent(
                product.getId(), saved.getId(), stockChange));

        return saved;
    }

    @Transactional
    public void deleteProduct(String productId) {
        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        variantRepository.deleteAll(variants);
        productRepository.deleteById(productId);
    }

    @Transactional
    public void deleteVariant(String variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        String productId = variant.getProductId();

        variantRepository.delete(variant);

        eventPublisher.publishEvent(new StockChangedEvent(
                productId, variantId, -variant.getStockQuantity()));
    }

    @Transactional
    public void addReview(String productId, int starRating) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        double currentTotal = p.getAverageRating() * p.getReviewCount();
        double newTotal = currentTotal + starRating;
        int newCount = p.getReviewCount() + 1;
        p.setReviewCount(newCount);
        p.setAverageRating(newTotal / newCount);
        productRepository.save(p);
    }

    @Transactional
    public void applyDiscount(String productId, BigDecimal newBasePrice) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getCompareAtPrice() == null) {
            product.setCompareAtPrice(product.getBasePrice());
        }
        product.setBasePrice(newBasePrice);
        productRepository.save(product);
        updateParentAggregates(productId);
    }

    /**
     * Atomically reduces stock for a variant. Uses a conditional MongoDB update
     * (stock >= quantity) to prevent overselling without requiring application-level
     * locking or transactions.
     */
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

        // FIX: Mirror the result check from reduceStockAtomic.
        // Without this, a ghost StockChangedEvent fires even if the variant was
        // deleted between the caller obtaining the variantId and this method running,
        // which would silently corrupt the parent product's aggregate totals.
        if (result.getModifiedCount() == 0) {
            throw new ResourceNotFoundException(
                    "Variant not found or stock update failed: " + variantId);
        }

        eventPublisher.publishEvent(new StockChangedEvent(productId, variantId, quantity));
    }

    // =========================================================================
    // AGGREGATE HELPERS
    // Called by StockChangedEvent listener and after product price changes.
    // Uses targeted mongoTemplate $set updates — safe under concurrent execution.
    // =========================================================================

    /**
     * Updates totalStock, minPrice, and maxPrice on the parent Product document.
     *
     * CONCURRENCY FIX: The original implementation used a read-modify-write cycle
     * (findById → mutate → save). Because Product has @Version, concurrent calls
     * from multiple StockChangedEvent listener threads would race and throw
     * OptimisticLockingFailureException on the losing thread, silently leaving
     * aggregate totals stale.
     *
     * Fix: compute the new values in application memory (still requires one read
     * of all variants), then write ONLY the three aggregate fields via a targeted
     * mongoTemplate $set update. This bypasses @Version entirely because we are
     * not replacing the whole document — Spring Data only increments @Version on
     * full-document save() calls, not on mongoTemplate partial updates.
     */
    public void updateParentAggregates(String productId) {
        // We still need to read variants to compute the new values
        List<ProductVariant> variants = variantRepository.findByProductId(productId);

        final BigDecimal newMin;
        final BigDecimal newMax;
        final int newTotalStock;

        if (variants.isEmpty()) {
            // No variants: fall back to the product's own basePrice
            // We need the product only in the empty-variants case for basePrice
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

        // Atomic partial update — does NOT touch @Version, safe under concurrency
        Query query = new Query(Criteria.where("id").is(productId));
        Update update = new Update()
                .set("totalStock", newTotalStock)
                .set("minPrice", newMin)
                .set("maxPrice", newMax);

        mongoTemplate.updateFirst(query, update, Product.class);
    }

    // =========================================================================
    // VALIDATION
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