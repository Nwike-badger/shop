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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final MongoTemplate mongoTemplate;

    // ✅ CRITICAL FIX #2: Add event publisher for async aggregate updates
    private final ApplicationEventPublisher eventPublisher;

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

    /**
     * ✅ CRITICAL FIX #3: Added pagination parameter
     * Returns Page<Product> instead of loading all products into memory.
     */
    public Page<Product> getProductsByCategorySlug(String slug, Pageable pageable) {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setCategorySlug(slug);
        return filterProducts(filter, pageable);
    }

    public List<Product> searchProducts(String keyword) {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setKeyword(keyword);
        // Search can stay unpaged for now (usually returns <100 results)
        return filterProducts(filter, Pageable.unpaged()).getContent();
    }

    public Page<Product> filterProducts(ProductFilterRequest filter, Pageable pageable) {
        Query query = new Query();

        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(filter.getKeyword()));
        }

        if (filter.getCategorySlug() != null) {
            Category category = categoryRepository.findBySlug(filter.getCategorySlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("category.id").is(category.getId()),
                    Criteria.where("categoryLineage").regex("," + category.getId() + ",")
            ));
        }

        if (filter.getMinPrice() != null)
            query.addCriteria(Criteria.where("minPrice").gte(filter.getMinPrice()));
        if (filter.getMaxPrice() != null)
            query.addCriteria(Criteria.where("maxPrice").lte(filter.getMaxPrice()));

        query.addCriteria(Criteria.where("isActive").is(true)); // Filters out inactive products

        List<Product> products = mongoTemplate.find(query.with(pageable), Product.class);

        // Optimize count query
        long count = mongoTemplate.count(query.skip(-1).limit(-1), Product.class);

        return PageableExecutionUtils.getPage(products, pageable, () -> count);
    }

    @Transactional
    public Product createOrUpdateProduct(ProductRequest request) {
        Product product = request.getId() != null ?
                productRepository.findById(request.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found"))
                : new Product();

        product.setName(request.getName());
        product.setSlug(request.getSlug());
        product.setDescription(request.getDescription());
        product.setSpecifications(request.getSpecifications() != null ?
                request.getSpecifications() : new HashMap<>());
        product.setImages(request.getImages() != null ?
                request.getImages() : new ArrayList<>());

        // Explicit mapping of isActive
        if (request.getIsActive() != null) {
            product.setActive(request.getIsActive());
        }

        // Pricing and Discount Logic
        if (request.getDiscount() != null && request.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
            if (request.getDiscount().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Discount cannot exceed 100%");
            }
            BigDecimal originalPrice = request.getBasePrice();
            product.setCompareAtPrice(originalPrice);
            product.setDiscount(request.getDiscount().intValue());

            BigDecimal discountFactor = request.getDiscount().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal amountOff = originalPrice.multiply(discountFactor);
            product.setBasePrice(originalPrice.subtract(amountOff).setScale(2, RoundingMode.HALF_UP));
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

        return productRepository.save(product);
    }

    @Transactional
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

        ProductVariant variant = request.getId() != null ?
                variantRepository.findById(request.getId())
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

        ProductVariant saved = variantRepository.save(variant);

        // Calculate the change in stock and publish event
        int stockChange = (request.getStockQuantity() != null ? request.getStockQuantity() : 0) -
                (oldStock != null ? oldStock : 0);

        eventPublisher.publishEvent(new StockChangedEvent(
                product.getId(),
                saved.getId(),
                stockChange
        ));

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

        // Publish event to reduce parent stock by the deleted variant's amount
        eventPublisher.publishEvent(new StockChangedEvent(
                productId,
                variantId,
                -variant.getStockQuantity()
        ));
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
     * Atomic stock reduction for high concurrency (e.g., checkout).
     * Now uses event publishing to avoid OptimisticLockingFailureException on the parent document.
     */
    public void reduceStockAtomic(String variantId, int quantity) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        if (!variant.isManageStock()) return;

        Query query = new Query(
                Criteria.where("id").is(variantId)
                        .and("stockQuantity").gte(quantity)
        );
        Update update = new Update().inc("stockQuantity", -quantity);

        var result = mongoTemplate.updateFirst(query, update, ProductVariant.class);

        if (result.getModifiedCount() == 0) {
            throw new RuntimeException("Insufficient stock");
        }

        eventPublisher.publishEvent(new StockChangedEvent(
                variant.getProductId(),
                variantId,
                -quantity
        ));
    }

    public void addStockAtomic(String variantId, String productId, int quantity) {
        Query query = new Query(Criteria.where("id").is(variantId));
        Update update = new Update().inc("stockQuantity", quantity);
        mongoTemplate.updateFirst(query, update, ProductVariant.class);

        eventPublisher.publishEvent(new StockChangedEvent(
                productId,
                variantId,
                quantity
        ));
    }

    // Add this method to fetch multiple parent products in one query
    public List<Product> getProductsByIds(Collection<String> ids) {
        Query query = new Query(Criteria.where("id").in(ids));
        return mongoTemplate.find(query, Product.class);
    }

    /**
     * Made public so StockChangeEventListener can call it.
     * Should only be called by the listener or admin-level operations (like applyDiscount).
     */
    public void updateParentAggregates(String productId) {
        Product product = productRepository.findById(productId).orElseThrow();
        List<ProductVariant> variants = variantRepository.findByProductId(productId);

        if (variants.isEmpty()) {
            product.setTotalStock(0);
            BigDecimal base = product.getBasePrice() != null ?
                    product.getBasePrice() : BigDecimal.ZERO;
            product.setMinPrice(base);
            product.setMaxPrice(base);
        } else {
            int totalStock = variants.stream()
                    .filter(ProductVariant::isManageStock)
                    .mapToInt(v -> v.getStockQuantity() == null ? 0 : v.getStockQuantity())
                    .sum();

            BigDecimal min = variants.stream()
                    .map(ProductVariant::getPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            BigDecimal max = variants.stream()
                    .map(ProductVariant::getPrice)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            product.setTotalStock(totalStock);
            product.setMinPrice(min);
            product.setMaxPrice(max);
        }

        productRepository.save(product);
    }

    private void validateVariantAttributes(Product product, Map<String, String> attributes) {
        if (product.getVariantOptions() == null || product.getVariantOptions().isEmpty())
            return;

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