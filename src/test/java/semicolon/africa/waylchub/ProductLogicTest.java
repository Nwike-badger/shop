package semicolon.africa.waylchub;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import semicolon.africa.waylchub.dto.productDto.CategoryRequest;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.dto.productDto.VariantRequest;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
import semicolon.africa.waylchub.service.productService.CategoryService;
import semicolon.africa.waylchub.service.productService.ProductService;

// ✅ Import Awaitility
import org.awaitility.Awaitility;
import java.time.Duration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductLogicTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired private ProductService productService;
    @Autowired private CategoryService categoryService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        variantRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    // ============================================================
    // ✅ PHASE 2: PRICE & STOCK LOGIC TESTS
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("5️⃣ Min & Max Price Calculation (Aggregation)")
    void testMinMaxPriceLogic() {
        // 1. Create Parent Product
        ProductRequest pReq = new ProductRequest();
        pReq.setName("Price Test Item");
        pReq.setSlug("price-test");
        pReq.setBasePrice(BigDecimal.ZERO); // Initial dummy price
        Product parent = productService.createOrUpdateProduct(pReq);

        // 2. Add Cheap Variant ($10)
        createVariant(parent.getId(), "V-LOW", BigDecimal.valueOf(10), 5);

        // 3. Add Expensive Variant ($100)
        createVariant(parent.getId(), "V-HIGH", BigDecimal.valueOf(100), 5);

        // 4. Verify Parent Aggregates (✅ Wrapped in Awaitility)
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Product updated = productService.getProductById(parent.getId());
                    // Min should be lowest variant ($10)
                    assertThat(updated.getMinPrice()).isEqualByComparingTo(BigDecimal.valueOf(10));
                    // Max should be highest variant ($100)
                    assertThat(updated.getMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
                });
    }

    @Test
    @Order(2)
    @DisplayName("6️⃣ Total Stock Aggregation")
    void testTotalStockAggregation() {
        Product parent = productService.createOrUpdateProduct(createProductReq("Stock Test", "stock-test"));

        // Add 3 variants with 10 stock each
        createVariant(parent.getId(), "S-1", BigDecimal.TEN, 10);
        createVariant(parent.getId(), "S-2", BigDecimal.TEN, 10);
        createVariant(parent.getId(), "S-3", BigDecimal.TEN, 10);

        // ✅ Wrapped in Awaitility to allow the event to process
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Product updated = productService.getProductById(parent.getId());
                    // Total should be 30
                    assertThat(updated.getTotalStock()).isEqualTo(30);
                });
    }

    @Test
    @Order(3)
    @DisplayName("7️⃣ Low Stock Threshold Logic")
    void testLowStockDetection() {
        Product parent = productService.createOrUpdateProduct(createProductReq("Low Stock Item", "low-stock"));

        // Create variant with stock 3 (Default threshold is usually 5)
        ProductVariant v = new ProductVariant();
        v.setProductId(parent.getId());
        v.setSku("LOW-SKU");
        v.setPrice(BigDecimal.TEN);
        v.setStockQuantity(3);
        v.setLowStockThreshold(5); // Explicitly set threshold
        variantRepository.save(v);

        // Fetch back and check logic
        ProductVariant saved = variantRepository.findById(v.getId()).orElseThrow();

        boolean isLowStock = saved.getStockQuantity() < saved.getLowStockThreshold();
        assertThat(isLowStock).as("Should be flagged as low stock").isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("8️⃣ manageStock = false Behavior (Infinite Stock)")
    void testManageStockFalse() {
        Product parent = productService.createOrUpdateProduct(createProductReq("Digital Item", "digital-item"));

        // Create variant with 0 stock BUT manageStock = false
        ProductVariant v = new ProductVariant();
        v.setProductId(parent.getId());
        v.setSku("DIGITAL-001");
        v.setPrice(BigDecimal.TEN);
        v.setStockQuantity(0);
        v.setManageStock(false); // 🔑 Key configuration
        ProductVariant saved = variantRepository.save(v);

        // Action: Try to reduce stock by 1 (Should succeed despite 0 quantity)
        productService.reduceStockAtomic(saved.getId(), 1);

        // Verify: No exception threw, and stock is still 0 (didn't go negative)
        ProductVariant verify = variantRepository.findById(saved.getId()).get();
        assertThat(verify.getStockQuantity()).isEqualTo(0);
    }

    // ============================================================
    // ✅ PHASE 3: RELATION & CONSISTENCY TESTS
    // ============================================================

    @Test
    @Order(5)
    @DisplayName("9️⃣ Variant Must Belong to Valid Product")
    void testVariantOrphanPrevention() {
        VariantRequest vReq = new VariantRequest();
        vReq.setProductId("non-existent-id-999"); // 🛑 Invalid ID
        vReq.setSku("GHOST-SKU");
        vReq.setPrice(BigDecimal.TEN);

        // Should fail because parent doesn't exist
        assertThatThrownBy(() -> productService.saveVariant(vReq))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Parent product not found");
    }

    @Test
    @Order(6)
    @DisplayName("🔟 Category Integrity (DBRef check)")
    void testCategoryIntegrity() {
        // 1. Create a Category first
        CategoryRequest catReq = new CategoryRequest();
        catReq.setName("Electronics");
        catReq.setSlug("electronics");
        categoryService.createCategory(catReq);

        // 2. Create Product linked to this Category
        ProductRequest pReq = createProductReq("iPhone", "iphone");
        pReq.setCategorySlug("electronics"); // Linking via slug

        Product savedProduct = productService.createOrUpdateProduct(pReq);

        // 3. Verify Relation
        assertThat(savedProduct.getCategory()).isNotNull();
        assertThat(savedProduct.getCategory().getName()).isEqualTo("Electronics");
        assertThat(savedProduct.getCategorySlug()).isEqualTo("electronics");

        // 4. Verify Lineage Copied
        assertThat(savedProduct.getCategoryLineage()).isEqualTo(",");
    }

    @Test
    @Order(7)
    @DisplayName("🛑 Invalid Category Link Fails Gracefully")
    void testInvalidCategoryLink() {
        ProductRequest pReq = createProductReq("Ghost Phone", "ghost-phone");
        pReq.setCategorySlug("imaginary-category"); // Doesn't exist

        assertThatThrownBy(() -> productService.createOrUpdateProduct(pReq))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    // ============================================================
    // 🛠️ HELPER METHODS
    // ============================================================

    private ProductRequest createProductReq(String name, String slug) {
        ProductRequest r = new ProductRequest();
        r.setName(name);
        r.setSlug(slug);
        r.setBasePrice(BigDecimal.valueOf(100));
        return r;
    }

    private void createVariant(String productId, String sku, BigDecimal price, int stock) {
        VariantRequest v = new VariantRequest();
        v.setProductId(productId);
        v.setSku(sku);
        v.setPrice(price);
        v.setStockQuantity(stock);

        // FIX: Use the SKU as the attribute value to guarantee uniqueness
        // Example: {"Type": "V-LOW"} then {"Type": "V-HIGH"}
        v.setAttributes(Map.of("Type", sku));

        productService.saveVariant(v);
    }
}