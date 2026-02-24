package semicolon.africa.waylchub;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.dto.productDto.VariantRequest;
import semicolon.africa.waylchub.model.order.Address;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.orderRepository.OrderRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
import semicolon.africa.waylchub.service.orderService.OrderService;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * 🚨 THIS TEST EXPOSES THE CRITICAL BUG
 *
 * EXPECTED BEHAVIOR:
 * - Stock: 100 → Order created → Stock: 95 (success)
 * - Stock: 100 → Order fails → Stock: 100 (rollback)
 *
 * ACTUAL BEHAVIOR WITH YOUR CODE:
 * - Stock: 100 → Order fails → Stock: 105 (BUG!)
 *
 * This test will:
 * ❌ FAIL with your current code (stock becomes 105)
 * ✅ PASS with the fixed code (stock stays 100)
 */
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CriticalRollbackBugTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired private ProductService productService;
    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private OrderRepository orderRepository;

    // Spy on OrderRepository to simulate save failure
    @SpyBean
    private OrderRepository orderRepositorySpy;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        variantRepository.deleteAll();
        orderRepository.deleteAll();
        Mockito.reset(orderRepositorySpy);
    }

    /**
     * 🚨 THE CRITICAL TEST — Exposes Stock Duplication Bug
     *
     * SCENARIO:
     * 1. Product has 100 stock
     * 2. Customer orders 5 items
     * 3. Stock reduced to 95
     * 4. Order save FAILS (simulated)
     * 5. What happens to stock?
     *
     * EXPECTED: Stock = 100 (rolled back)
     * YOUR CODE: Stock = 105 (BUG!)
     * FIXED CODE: Stock = 100 (correct)
     */
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("🚨 CRITICAL: Order save failure must NOT duplicate stock")
    void testOrderSaveFailureDoesNotDuplicateStock() {
        // 1. Setup: Create product with 100 stock
        Product product = createProduct("Test Product", "test-prod", BigDecimal.valueOf(50));
        ProductVariant variant = createVariant(product.getId(), "TEST-SKU",
                BigDecimal.valueOf(50), 100);

        String initialVariantId = variant.getId();

        // 2. Verify initial stock
        assertThat(variant.getStockQuantity()).isEqualTo(100);

        // 3. Mock orderRepository.save() to throw exception
        // This simulates a real failure like:
        // - Database connection lost
        // - Validation error
        // - Unique constraint violation
        Mockito.doThrow(new RuntimeException("Simulated DB error"))
                .when(orderRepositorySpy)
                .save(any(Order.class));

        // 4. Try to create order (will fail)
        OrderRequest request = buildOrderRequest(
                "customer@example.com",
                List.of(new OrderItemRequest(initialVariantId, 5))
        );

        // 5. Verify exception is thrown
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class);

        // 6. 🚨 THE CRITICAL ASSERTION 🚨
        // Stock must be exactly 100 (original value)
        // NOT 105 (which happens with manual rollback bug)
        ProductVariant afterFailure = variantRepository.findById(initialVariantId).orElseThrow();

        System.out.println("\n=== CRITICAL BUG CHECK ===");
        System.out.println("Initial stock: 100");
        System.out.println("Order quantity: 5");
        System.out.println("Order save: FAILED");
        System.out.println("Expected stock after rollback: 100");
        System.out.println("Actual stock after rollback: " + afterFailure.getStockQuantity());

        if (afterFailure.getStockQuantity() == 105) {
            System.out.println("❌ BUG DETECTED: Stock duplicated due to manual rollback!");
            System.out.println("   Spring rolled back to 100, then manual restore added 5 more.");
        } else if (afterFailure.getStockQuantity() == 100) {
            System.out.println("✅ CORRECT: Spring's automatic rollback worked perfectly.");
        } else {
            System.out.println("⚠️ UNEXPECTED: Stock = " + afterFailure.getStockQuantity());
        }
        System.out.println("==========================\n");

        // This will FAIL with your current code (shows 105)
        // This will PASS with fixed code (shows 100)
        assertThat(afterFailure.getStockQuantity())
                .as("Stock must return to original value after order save failure")
                .isEqualTo(100);
    }

    /**
     * 🧪 CONTROL TEST — Happy Path (Should Always Pass)
     *
     * This proves the test setup is correct by showing
     * that successful orders DO reduce stock.
     */
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("✅ CONTROL: Successful order reduces stock correctly")
    void testSuccessfulOrderReducesStock() {
        // Reset the spy to allow normal save operation
        Mockito.reset(orderRepositorySpy);

        // Setup: Product with 100 stock
        Product product = createProduct("Control Product", "control-prod", BigDecimal.valueOf(50));
        ProductVariant variant = createVariant(product.getId(), "CONTROL-SKU",
                BigDecimal.valueOf(50), 100);

        // Create order (should succeed)
        OrderRequest request = buildOrderRequest(
                "customer@example.com",
                List.of(new OrderItemRequest(variant.getId(), 5))
        );

        Order order = orderService.createOrder(request);

        // Verify order created
        assertThat(order.getId()).isNotNull();

        // Verify stock reduced
        ProductVariant afterSuccess = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(afterSuccess.getStockQuantity()).isEqualTo(95);

        System.out.println("\n=== CONTROL TEST ===");
        System.out.println("Initial stock: 100");
        System.out.println("Order quantity: 5");
        System.out.println("Order save: SUCCESS");
        System.out.println("Final stock: " + afterSuccess.getStockQuantity());
        System.out.println("✅ Stock correctly reduced to 95");
        System.out.println("====================\n");
    }

    /**
     * 🔬 STRESS TEST — Multiple Failed Orders
     *
     * Shows the compounding effect of the bug.
     * With manual rollback, each failure adds phantom stock.
     */
    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("🔬 STRESS: 10 failed orders must not compound the bug")
    void testMultipleFailuresDoNotCompound() {
        // Setup: Product with 100 stock
        Product product = createProduct("Stress Product", "stress-prod", BigDecimal.valueOf(50));
        ProductVariant variant = createVariant(product.getId(), "STRESS-SKU",
                BigDecimal.valueOf(50), 100);

        String variantId = variant.getId();

        // Mock all saves to fail
        Mockito.doThrow(new RuntimeException("Simulated DB error"))
                .when(orderRepositorySpy)
                .save(any(Order.class));

        // Try to create 10 orders (all will fail)
        for (int i = 0; i < 10; i++) {
            OrderRequest request = buildOrderRequest(
                    "customer" + i + "@example.com",
                    List.of(new OrderItemRequest(variantId, 5))
            );

            try {
                orderService.createOrder(request);
            } catch (Exception e) {
                // Expected to fail
            }
        }

        // Check final stock
        ProductVariant afterStress = variantRepository.findById(variantId).orElseThrow();

        System.out.println("\n=== STRESS TEST ===");
        System.out.println("Initial stock: 100");
        System.out.println("Failed orders: 10");
        System.out.println("Order quantity each: 5");
        System.out.println("Expected stock: 100");
        System.out.println("Actual stock: " + afterStress.getStockQuantity());

        if (afterStress.getStockQuantity() == 150) {
            System.out.println("❌ CRITICAL BUG: 50 phantom stock added!");
            System.out.println("   (10 failures × 5 items = 50 extra)");
        } else if (afterStress.getStockQuantity() == 100) {
            System.out.println("✅ CORRECT: No phantom stock added.");
        }
        System.out.println("===================\n");

        // With your bug: stock = 150 (10 × 5 = 50 extra)
        // With fix: stock = 100 (unchanged)
        assertThat(afterStress.getStockQuantity())
                .as("Multiple failures must not compound phantom stock")
                .isEqualTo(100);
    }

    // =============================================================================
    // HELPER METHODS
    // =============================================================================

    private Product createProduct(String name, String slug, BigDecimal price) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setSlug(slug);
        req.setBasePrice(price);
        return productService.createOrUpdateProduct(req);
    }

    private ProductVariant createVariant(String productId, String sku,
                                         BigDecimal price, int stock) {
        VariantRequest req = new VariantRequest();
        req.setProductId(productId);
        req.setSku(sku);
        req.setPrice(price);
        req.setStockQuantity(stock);
        req.setAttributes(Map.of("Type", "Standard"));
        return productService.saveVariant(req);
    }

    private OrderRequest buildOrderRequest(String email, List<OrderItemRequest> items) {
        OrderRequest req = new OrderRequest();
        req.setCustomerEmail(email);
        req.setItems(items);
        req.setPaymentMethod("CARD");

        Address address = new Address();
        address.setStreetAddress("123 Test St");
        address.setCity("Lagos");
        address.setState("Lagos");
        address.setCountry("Nigeria");
        address.setPostalCode("100001");

        req.setShippingAddress(address);
        req.setBillingAddress(address);

        return req;
    }
}