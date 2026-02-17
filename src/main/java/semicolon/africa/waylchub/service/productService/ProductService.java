package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.productDto.*;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.*;
import semicolon.africa.waylchub.repository.productRepository.*;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final MongoTemplate mongoTemplate;

    // =====================================================
    // 1. READ OPERATIONS (Public)
    // =====================================================

    public Product getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
    }

    public List<Product> getProductsByCategorySlug(String slug) {
        // Reuse the filter engine for category listing
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setCategorySlug(slug);
        return filterProducts(filter, Pageable.unpaged()).getContent();
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }


    public Page<Product> filterProducts(ProductFilterRequest filter, Pageable pageable) {
        Query query = new Query();

        // 1. Text Search
        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(filter.getKeyword()));
        }

        // 2. Category Lineage (Performance Fix)
        if (filter.getCategorySlug() != null) {
            Category category = categoryRepository.findBySlug(filter.getCategorySlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("category.id").is(category.getId()),
                    Criteria.where("categoryLineage").regex("," + category.getId() + ",")
            ));
        }

        // 3. Price & Attributes
        if (filter.getMinPrice() != null) query.addCriteria(Criteria.where("maxPrice").gte(filter.getMinPrice()));
        if (filter.getMaxPrice() != null) query.addCriteria(Criteria.where("minPrice").lte(filter.getMaxPrice()));

        query.addCriteria(Criteria.where("isActive").is(true));

        List<Product> products = mongoTemplate.find(query.with(pageable), Product.class);
        long count = mongoTemplate.count(query.skip(-1).limit(-1), Product.class);

        return PageableExecutionUtils.getPage(products, pageable, () -> count);
    }

    // =====================================================
    // 2. WRITE OPERATIONS (Admin)
    // =====================================================

    @Transactional
    public Product createOrUpdateProduct(ProductRequest request) {
        Product product = request.getId() != null ?
                productRepository.findById(request.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found"))
                : new Product();

        // Basic Info
        product.setName(request.getName());
        product.setSlug(request.getSlug());
        product.setDescription(request.getDescription());
        product.setSpecifications(request.getSpecifications());
        product.setImages(request.getImages());
        product.setBasePrice(request.getBasePrice());

        if (request.getCompareAtPrice() != null) {
            product.setCompareAtPrice(request.getCompareAtPrice());
        }

        // Denormalize Category (Performance Fix)
        if (request.getCategorySlug() != null) {
            Category cat = categoryRepository.findBySlug(request.getCategorySlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

            product.setCategory(cat);
            product.setCategoryName(cat.getName());
            product.setCategorySlug(cat.getSlug());
            product.setCategoryLineage(cat.getLineage()); // Critical for filtering
        }

        // Denormalize Brand
        if (request.getBrandSlug() != null) {
            Brand brand = brandRepository.findBySlug(request.getBrandSlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
            product.setBrand(brand);
            product.setBrandName(brand.getName());
        }

        // Variant Definitions (e.g., "Color": ["Red", "Blue"])
        if (request.getVariantOptions() != null) {
            List<VariantOption> options = request.getVariantOptions().entrySet().stream()
                    .map(entry -> new VariantOption(entry.getKey(), entry.getValue()))
                    .toList();
            product.setVariantOptions(options);
        }

        if (request.getDiscount() != null && request.getDiscount().compareTo(BigDecimal.ZERO) > 0) {

            // 1. The input price becomes the "Original Price" (compareAtPrice)
            BigDecimal originalPrice = request.getBasePrice();
            product.setCompareAtPrice(originalPrice);

            // 2. Set the discount percentage
            product.setDiscount(request.getDiscount().intValue());

            // 3. Calculate new "Selling Price" (basePrice)
            // Formula: Price = Original - (Original * (Discount / 100))
            BigDecimal discountFactor = request.getDiscount().divide(BigDecimal.valueOf(100));
            BigDecimal amountOff = originalPrice.multiply(discountFactor);
            BigDecimal sellingPrice = originalPrice.subtract(amountOff);

            product.setBasePrice(sellingPrice);

        } else {
            // No discount: Selling Price is just the input Price
            product.setBasePrice(request.getBasePrice());
            product.setCompareAtPrice(request.getCompareAtPrice()); // Optional manual override
            product.setDiscount(0);
        }

        return productRepository.save(product);
    }

    public List<Product> searchProducts(String keyword) {
        // Reuse the filter engine for simple search
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setKeyword(keyword);
        return filterProducts(filter, Pageable.unpaged()).getContent();
    }

    @Transactional
    public ProductVariant saveVariant(VariantRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent product not found"));

        validateVariantAttributes(product, request.getAttributes());

        ProductVariant variant = request.getId() != null ?
                variantRepository.findById(request.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found"))
                : new ProductVariant();

        variant.setProductId(product.getId());
        variant.setSku(request.getSku());
        variant.setPrice(request.getPrice());
        variant.setCompareAtPrice(request.getCompareAtPrice());
        variant.setStockQuantity(request.getStockQuantity());
        variant.setAttributes(request.getAttributes());
        variant.setImages(request.getImages());

        ProductVariant saved = variantRepository.save(variant);

        // 🔥 IMPORTANT: Always recalculate parent totals after variant change
        updateParentAggregates(product.getId());

        return saved;
    }

    /**
     * Calculates MinPrice, MaxPrice, and TotalStock for the parent Product
     * based on all its existing variants.
     */
    private void updateParentAggregates(String productId) {
        Product product = productRepository.findById(productId).orElseThrow();
        List<ProductVariant> variants = variantRepository.findByProductId(productId);

        if (variants.isEmpty()) {
            product.setTotalStock(0);
            product.setMinPrice(product.getBasePrice() != null ? product.getBasePrice() : BigDecimal.ZERO);
            product.setMaxPrice(product.getBasePrice() != null ? product.getBasePrice() : BigDecimal.ZERO);
        } else {
            int totalStock = variants.stream()
                    .filter(ProductVariant::isManageStock)
                    .mapToInt(v -> v.getStockQuantity() == null ? 0 : v.getStockQuantity())
                    .sum();

            BigDecimal min = variants.stream()
                    .map(ProductVariant::getPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            BigDecimal max = variants.stream()
                    .map(ProductVariant::getPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            product.setTotalStock(totalStock);
            product.setMinPrice(min);
            product.setMaxPrice(max);
        }
        productRepository.save(product);
    }

    public Product getProductById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    // =====================================================
    // 4. BUSINESS LOGIC (Reviews & Discounts)
    // =====================================================

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

        // Store old price as comparison if not already set
        if (product.getCompareAtPrice() == null) {
            product.setCompareAtPrice(product.getBasePrice());
        }
        product.setBasePrice(newBasePrice);
        productRepository.save(product);

        // Note: For a robust system, you might want to iterate variants and
        // update their prices too, but this handles the display price.
        updateParentAggregates(productId);
    }

    // =====================================================
    // 5. ATOMIC INVENTORY (Order Service)
    // =====================================================

    public void reduceStockAtomic(String variantId, int quantity) {
        Query query = new Query(Criteria.where("id").is(variantId).and("stockQuantity").gte(quantity));
        Update update = new Update().inc("stockQuantity", -quantity);

        var result = mongoTemplate.updateFirst(query, update, ProductVariant.class);

        if (result.getModifiedCount() == 0) {
            throw new RuntimeException("Insufficient stock or Variant not found");
        }
    }

    public void addStockAtomic(String variantId, String productId, int quantity) {
        Query query = new Query(Criteria.where("id").is(variantId));
        Update update = new Update().inc("stockQuantity", quantity);
        mongoTemplate.updateFirst(query, update, ProductVariant.class);

        updateParentAggregates(productId);
    }

    // =====================================================
    // 6. VALIDATION
    // =====================================================

    private void validateVariantAttributes(Product product, Map<String, String> attributes) {
        if (product.getVariantOptions() == null) return;

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            VariantOption option = product.getVariantOptions().stream()
                    .filter(o -> o.getName().equalsIgnoreCase(entry.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid variant option: " + entry.getKey()));

            if (!option.getValues().contains(entry.getValue())) {
                throw new IllegalArgumentException(
                        "Invalid value '" + entry.getValue() + "' for option '" + entry.getKey() + "'");
            }
        }
    }
}