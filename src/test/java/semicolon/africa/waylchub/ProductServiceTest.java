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

// ✅ Import Awaitility
import org.awaitility.Awaitility;
import java.time.Duration;

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
        request.setBasePrice(BigDecimal.valueOf(100.00));
        BigDecimal twenty = new BigDecimal("20");
        request.setDiscount(twenty);

        Product saved = productService.createOrUpdateProduct(request);

        assertThat(saved.getBasePrice()).isEqualByComparingTo(BigDecimal.valueOf(80.00));
        assertThat(saved.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(saved.getDiscount()).isEqualTo(20);
    }

    // ==============================================
    // TEST 2: VARIANT AGGREGATION (Min/Max/Stock)
    // ==============================================
    @Test
    @Order(2)
    @DisplayName("✅ 2. Variant Aggregation (Async Event Test)")
    void testVariantAggregation() {
        ProductRequest pReq = new ProductRequest();
        pReq.setName("T-Shirt");
        pReq.setSlug("t-shirt");
        pReq.setBasePrice(BigDecimal.valueOf(10));
        pReq.setVariantOptions(Map.of("Size", List.of("S", "M", "L")));
        Product parent = productService.createOrUpdateProduct(pReq);

        VariantRequest v1 = new VariantRequest();
        v1.setProductId(parent.getId());
        v1.setSku("TEE-S");
        v1.setPrice(BigDecimal.valueOf(10.00));
        v1.setStockQuantity(5);
        v1.setAttributes(Map.of("Size", "S"));
        productService.saveVariant(v1);

        VariantRequest v2 = new VariantRequest();
        v2.setProductId(parent.getId());
        v2.setSku("TEE-M");
        v2.setPrice(BigDecimal.valueOf(20.00));
        v2.setStockQuantity(10);
        v2.setAttributes(Map.of("Size", "M"));
        productService.saveVariant(v2);

        // ✅ FIX: Use Awaitility to wait for the async event listener to finish updating the parent
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Product updatedParent = productService.getProductById(parent.getId());
                    assertThat(updatedParent.getTotalStock()).isEqualTo(15);
                    assertThat(updatedParent.getMinPrice()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
                    assertThat(updatedParent.getMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(20.00));
                });
    }

    // ==============================================
    // TEST 3: VALIDATION (Strict Attributes)
    // ==============================================
    @Test
    @Order(3)
    @DisplayName("🛑 3. Prevent Invalid Variant Attributes")
    void testInvalidVariantAttributes() {
        ProductRequest pReq = new ProductRequest();
        pReq.setName("Hoodie");
        pReq.setSlug("hoodie");
        pReq.setBasePrice(BigDecimal.TEN);
        pReq.setVariantOptions(Map.of("Size", List.of("S", "M")));
        Product parent = productService.createOrUpdateProduct(pReq);

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
        ProductRequest pReq = new ProductRequest();
        pReq.setName("Stock Parent");
        pReq.setSlug("stock-parent");
        pReq.setBasePrice(BigDecimal.TEN);
        Product parent = productService.createOrUpdateProduct(pReq);

        ProductVariant variant = new ProductVariant();
        variant.setProductId(parent.getId());
        variant.setSku("ATOMIC-TEST");
        variant.setStockQuantity(10);
        variant.setPrice(BigDecimal.TEN);
        variant.setManageStock(true);
        variantRepository.save(variant);

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

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);

        // ✅ Check variant stock is zero
        ProductVariant finalVariant = variantRepository.findById(variant.getId()).get();
        assertThat(finalVariant.getStockQuantity()).isEqualTo(0);

        // ✅ FIX: Use Awaitility to verify the parent was updated by the events
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Product updatedParent = productService.getProductById(parent.getId());
                    assertThat(updatedParent.getTotalStock()).isEqualTo(0);
                });
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

        productService.deleteProduct(parent.getId());

        assertThat(productRepository.findById(parent.getId())).isEmpty();
        List<ProductVariant> variants = variantRepository.findByProductId(parent.getId());
        assertThat(variants).isEmpty();
    }

    // ==============================================
    // TEST 6: DEACTIVATION LOGIC
    // ==============================================
    @Test
    @Order(6)
    @DisplayName("🚫 6. Deactivating Product Hides It From Search")
    void testDeactivationLogic() {
        ProductRequest activeReq = new ProductRequest();
        activeReq.setName("Visible Phone");
        activeReq.setSlug("visible-phone");
        activeReq.setBasePrice(BigDecimal.TEN);
        activeReq.setIsActive(true);
        productService.createOrUpdateProduct(activeReq);

        ProductRequest inactiveReq = new ProductRequest();
        inactiveReq.setName("Hidden Phone");
        inactiveReq.setSlug("hidden-phone");
        inactiveReq.setBasePrice(BigDecimal.TEN);
        inactiveReq.setIsActive(false);
        productService.createOrUpdateProduct(inactiveReq);

        List<Product> results = productService.searchProducts("Phone");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSlug()).isEqualTo("visible-phone");
    }
}