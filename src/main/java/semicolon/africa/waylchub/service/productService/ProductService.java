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
    // CREATE / UPDATE PRODUCT
    // =====================================================

    @Transactional
    public Product createOrUpdateProduct(ProductRequest request) {
        Product product = request.getId() != null ?
                productRepository.findById(request.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found"))
                : new Product();

        // 1. Basic Fields
        product.setName(request.getName());
        product.setSlug(request.getSlug());
        product.setDescription(request.getDescription());
        product.setSpecifications(request.getSpecifications());
        product.setImages(request.getImages());

        // 2. Handle Category & Lineage (The Fix)
        if (request.getCategorySlug() != null) {
            Category cat = categoryRepository.findBySlug(request.getCategorySlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

            product.setCategory(cat);
            product.setCategoryName(cat.getName());

            // CRITICAL: Save the lineage string directly on the product
            // e.g., ",101,202,305,"
            product.setCategoryLineage(cat.getLineage());
        }

        // 3. Handle Brand
        if (request.getBrandSlug() != null) {
            Brand brand = brandRepository.findBySlug(request.getBrandSlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
            product.setBrand(brand);
            product.setBrandName(brand.getName());
        }

        // 4. Handle Variants (Map DTO to POJO)
        if (request.getVariantOptions() != null) {
            List<VariantOption> options = request.getVariantOptions().entrySet().stream()
                    .map(entry -> {
                        VariantOption opt = new VariantOption();
                        opt.setName(entry.getKey());   // e.g., "Color"
                        opt.setValues(entry.getValue()); // e.g., ["Red", "Blue"]
                        return opt;
                    })
                    .toList();
            product.setVariantOptions(options);
        }

        return productRepository.save(product);
    }

    // =====================================================
    // CREATE / UPDATE VARIANT
    // =====================================================

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

        updateParentAggregates(product.getId());

        return saved;
    }

    // =====================================================
    // AGGREGATE UPDATE (Performance Cache)
    // =====================================================

    private void updateParentAggregates(String productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow();

        List<ProductVariant> variants =
                variantRepository.findByProductId(productId);

        if (variants.isEmpty()) {
            product.setTotalStock(0);
            product.setMinPrice(BigDecimal.ZERO);
            product.setMaxPrice(BigDecimal.ZERO);
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

    // =====================================================
    // FILTER PRODUCTS (Optimized)
    // =====================================================

    public Page<Product> filterProducts(ProductFilterRequest filter, Pageable pageable) {
        Query query = new Query();

        // 1. Text Search
        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(filter.getKeyword()));
        }

        // 2. Category Lineage Filter (The Fix)
        // We no longer query the DBRef. We query the string field directly.
        if (filter.getCategorySlug() != null) {
            Category category = categoryRepository.findBySlug(filter.getCategorySlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

            // Matches if product is DIRECTLY in this category OR in a SUB-category
            // Because lineage looks like ",1,5,9," we can regex for ",5,"
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("category.id").is(category.getId()), // Direct match
                    Criteria.where("categoryLineage").regex("," + category.getId() + ",") // Child match
            ));
        }

        // 3. Price Range (Using Denormalized Min/Max)
        if (filter.getMinPrice() != null) {
            query.addCriteria(Criteria.where("maxPrice").gte(filter.getMinPrice()));
        }
        if (filter.getMaxPrice() != null) {
            query.addCriteria(Criteria.where("minPrice").lte(filter.getMaxPrice()));
        }

        query.addCriteria(Criteria.where("isActive").is(true));

        List<Product> products = mongoTemplate.find(query.with(pageable), Product.class);
        long count = mongoTemplate.count(query.skip(-1).limit(-1), Product.class);

        return PageableExecutionUtils.getPage(products, pageable, () -> count);
    }

    // =====================================================
    // ATOMIC STOCK REDUCTION (ORDER SERVICE USE)
    // =====================================================

    public void reduceStockAtomic(String variantId, int quantity) {

        Query query = new Query(
                Criteria.where("id").is(variantId)
                        .and("stockQuantity").gte(quantity)
        );

        Update update = new Update().inc("stockQuantity", -quantity);

        var result = mongoTemplate.updateFirst(
                query, update, ProductVariant.class);

        if (result.getModifiedCount() == 0) {
            throw new RuntimeException("Insufficient stock");
        }
    }

    public void addStockAtomic(String variantId, String productId, int quantity) {
        Query query = new Query(Criteria.where("id").is(variantId));
        Update update = new Update().inc("stockQuantity", quantity);
        mongoTemplate.updateFirst(query, update, ProductVariant.class);

        // As you noted, this is mandatory!
        updateParentAggregates(productId);
    }

    private void validateVariantAttributes(Product product,
                                           Map<String, String> attributes) {

        for (Map.Entry<String, String> entry : attributes.entrySet()) {

            VariantOption option = product.getVariantOptions().stream()

                    .filter(o -> o.getName().equalsIgnoreCase(entry.getKey()))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Invalid option: " + entry.getKey()));

            if (!option.getValues().contains(entry.getValue())) {
                throw new IllegalArgumentException(
                        "Invalid value '" + entry.getValue()
                                + "' for option '" + entry.getKey() + "'");
            }
        }
    }
}
