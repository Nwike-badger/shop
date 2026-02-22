package semicolon.africa.waylchub;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.dto.productDto.VariantRequest;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductServiceTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        variantRepository.deleteAll();
    }

    // ==============================================
    // TEST 1: PRODUCT CREATION & DISCOUNT LOGIC
    // ==============================================
    @Test
    @Order(1)
    @DisplayName("✅ 1. Create Product with Auto-Calculated Discount")
    void testCreateProductWithDiscount() {
        ProductRequest request = new ProductRequest();
        request.setName("Nike Air Max");
        request.setSlug("nike-air-max");
        request.setBasePrice(BigDecimal.valueOf(100.00)); // $100 Original
        BigDecimal twenty = new BigDecimal("20");
        request.setDiscount(twenty); // 20% Off

        Product saved = productService.createOrUpdateProduct(request);

        // Expected: BasePrice = 80, CompareAt = 100, Discount = 20
        assertThat(saved.getBasePrice()).isEqualByComparingTo(BigDecimal.valueOf(80.00));
        assertThat(saved.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(saved.getDiscount()).isEqualTo(20);
    }

    // ==============================================
    // TEST 2: VARIANT AGGREGATION (Min/Max/Stock)
    // ==============================================
    @Test
    @Order(2)
    @DisplayName("✅ 2. Variant Aggregation (Min/Max Price & Total Stock)")
    void testVariantAggregation() {
        // 1. Create Parent
        ProductRequest pReq = new ProductRequest();
        pReq.setName("T-Shirt");
        pReq.setSlug("t-shirt");
        pReq.setBasePrice(BigDecimal.valueOf(10));
        // Define allowed options
        pReq.setVariantOptions(Map.of("Size", List.of("S", "M", "L")));
        Product parent = productService.createOrUpdateProduct(pReq);

        // 2. Add Variant 1 (Small, $10, Stock 5)
        VariantRequest v1 = new VariantRequest();
        v1.setProductId(parent.getId());
        v1.setSku("TEE-S");
        v1.setPrice(BigDecimal.valueOf(10.00));
        v1.setStockQuantity(5);
        v1.setAttributes(Map.of("Size", "S"));
        productService.saveVariant(v1);

        // 3. Add Variant 2 (Medium, $20, Stock 10)
        VariantRequest v2 = new VariantRequest();
        v2.setProductId(parent.getId());
        v2.setSku("TEE-M");
        v2.setPrice(BigDecimal.valueOf(20.00));
        v2.setStockQuantity(10);
        v2.setAttributes(Map.of("Size", "M"));
        productService.saveVariant(v2);

        // 4. Verify Parent Aggregates
        Product updatedParent = productService.getProductById(parent.getId());

        assertThat(updatedParent.getTotalStock()).isEqualTo(15); // 5 + 10
        assertThat(updatedParent.getMinPrice()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(updatedParent.getMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(20.00));
    }

    // ==============================================
    // TEST 3: VALIDATION (Strict Attributes)
    // ==============================================
    @Test
    @Order(3)
    @DisplayName("🛑 3. Prevent Invalid Variant Attributes")
    void testInvalidVariantAttributes() {
        // Parent only allows "Size": ["S", "M"]
        ProductRequest pReq = new ProductRequest();
        pReq.setName("Hoodie");
        pReq.setSlug("hoodie");
        pReq.setBasePrice(BigDecimal.TEN);
        pReq.setVariantOptions(Map.of("Size", List.of("S", "M")));
        Product parent = productService.createOrUpdateProduct(pReq);

        // Try adding "Size": "XL" (Not allowed)
        VariantRequest v1 = new VariantRequest();
        v1.setProductId(parent.getId());
        v1.setSku("HOODIE-XL");
        v1.setPrice(BigDecimal.TEN);
        v1.setAttributes(Map.of("Size", "XL"));

        assertThatThrownBy(() -> productService.saveVariant(v1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid value 'XL'");
    }

    // ==============================================
    // TEST 4: ATOMIC STOCK (Concurrency)
    // ==============================================
    @Test
    @Order(4)
    @DisplayName("⚡ 4. Atomic Stock Reduction (Concurrency Test)")
    void testAtomicStock() throws InterruptedException {
        // 1. Create a REAL Parent Product
        ProductRequest pReq = new ProductRequest();
        pReq.setName("Stock Parent");
        pReq.setSlug("stock-parent");
        pReq.setBasePrice(BigDecimal.TEN);
        Product parent = productService.createOrUpdateProduct(pReq);

        // 2. Setup: Variant with 10 items linked to REAL parent
        ProductVariant variant = new ProductVariant();
        variant.setProductId(parent.getId());
        variant.setSku("ATOMIC-TEST");
        variant.setStockQuantity(10);
        variant.setPrice(BigDecimal.TEN);
        variant.setManageStock(true);
        variantRepository.save(variant);

        // 3. Concurrency simulation
        int numberOfThreads = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    productService.reduceStockAtomic(variant.getId(), 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // With the fix in Service (try-catch), we expect 10 successes
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);

        ProductVariant finalVariant = variantRepository.findById(variant.getId()).get();
        assertThat(finalVariant.getStockQuantity()).isEqualTo(0);
    }
    // ==============================================
    // TEST 5: DELETION CASCADING
    // ==============================================
    @Test
    @Order(5)
    @DisplayName("🗑️ 5. Deleting Product Deletes Variants")
    void testDeleteProductCascade() {
        ProductRequest pReq = new ProductRequest();
        pReq.setName("To Delete");
        pReq.setSlug("del-prod");
        pReq.setBasePrice(BigDecimal.TEN);
        Product parent = productService.createOrUpdateProduct(pReq);

        VariantRequest v1 = new VariantRequest();
        v1.setProductId(parent.getId());
        v1.setSku("DEL-1");
        v1.setPrice(BigDecimal.TEN);
        productService.saveVariant(v1);

        // Delete Parent
        productService.deleteProduct(parent.getId());

        // Assert Parent is gone
        assertThat(productRepository.findById(parent.getId())).isEmpty();

        // Assert Variant is gone (Cascade worked)
        List<ProductVariant> variants = variantRepository.findByProductId(parent.getId());
        assertThat(variants).isEmpty();
    }

    // ... inside ProductServiceTest.java ...

    @Test
    @Order(6)
    @DisplayName("🚫 14. Deactivating Product Hides It From Search")
    void testDeactivationLogic() {
        // 1. Create Active Product
        ProductRequest activeReq = new ProductRequest();
        activeReq.setName("Visible Phone");
        activeReq.setSlug("visible-phone");
        activeReq.setBasePrice(BigDecimal.TEN);
        activeReq.setIsActive(true);
        productService.createOrUpdateProduct(activeReq);

        // 2. Create Inactive Product
        ProductRequest inactiveReq = new ProductRequest();
        inactiveReq.setName("Hidden Phone");
        inactiveReq.setSlug("hidden-phone");
        inactiveReq.setBasePrice(BigDecimal.TEN);
        inactiveReq.setIsActive(false); // This will map to model.isActive = false
        productService.createOrUpdateProduct(inactiveReq);

        // 3. Search for "Phone"
        List<Product> results = productService.searchProducts("Phone");

        // 4. Verify only the active one is returned
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSlug()).isEqualTo("visible-phone");
    }
}