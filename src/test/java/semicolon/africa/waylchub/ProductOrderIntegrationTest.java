package semicolon.africa.waylchub;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.dto.productDto.VariantRequest;
import semicolon.africa.waylchub.exception.InsufficientStockException;
import semicolon.africa.waylchub.model.order.*;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.orderRepository.OrderRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
import semicolon.africa.waylchub.service.orderService.OrderService;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ✅ CRITICAL INTEGRATION TESTS — Product + Order Services
 *
 * These tests validate the complete checkout flow:
 * - Product availability → Stock reduction → Order creation → Stock restoration
 *
 * MUST PASS BEFORE PRODUCTION
 */
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductOrderIntegrationTest {

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

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        variantRepository.deleteAll();
        orderRepository.deleteAll();
    }

    // =============================================================================
    // TEST 1: Happy Path — Complete Checkout Flow
    // =============================================================================

    @Test
    @Order(1)
    @DisplayName("✅ 1. HAPPY PATH: Product → Checkout → Stock Reduced → Order Created")
    void testCompleteCheckoutFlow() {
        // 1. Create product with 100 stock
        Product product = createProduct("iPhone 15", "iphone-15", BigDecimal.valueOf(999));
        ProductVariant variant = createVariant(product.getId(), "IPHONE-BLK",
                BigDecimal.valueOf(999), 100);

        // 2. Customer orders 5 items (✅ FIX: Used Builder)
        OrderRequest orderRequest = buildOrderRequest(
                "customer@example.com",
                List.of(OrderItemRequest.builder()
                        .variantId(variant.getId())
                        .quantity(5)
                        .build())
        );

        // 3. Create order
        semicolon.africa.waylchub.model.order.Order order = orderService.createOrder(orderRequest);

        // 4. Verify order created
        assertThat(order.getId()).isNotNull();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getGrandTotal()).isGreaterThan(BigDecimal.ZERO);

        // 5. Verify stock reduced immediately
        ProductVariant updated = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(95);

        // 6. Verify parent aggregate updated (async)
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Product updatedProduct = productService.getProductById(product.getId());
                    assertThat(updatedProduct.getTotalStock()).isEqualTo(95);
                });
    }

    // =============================================================================
    // TEST 2: Insufficient Stock Validation
    // =============================================================================

    @Test
    @Order(2)
    @DisplayName("🛑 2. INSUFFICIENT STOCK: Order fails, no stock change")
    void testInsufficientStock() {
        // 1. Create product with only 3 stock
        Product product = createProduct("Limited Item", "limited", BigDecimal.TEN);
        ProductVariant variant = createVariant(product.getId(), "LIMITED-1",
                BigDecimal.TEN, 3);

        // 2. Try to order 5 items (more than available) (✅ FIX: Used Builder)
        OrderRequest request = buildOrderRequest(
                "customer@example.com",
                List.of(OrderItemRequest.builder()
                        .variantId(variant.getId())
                        .quantity(5)
                        .build())
        );

        // 3. Verify exception thrown
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("unavailable");

        // 4. Verify no stock change (still 3)
        ProductVariant unchanged = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(unchanged.getStockQuantity()).isEqualTo(3);

        // 5. Verify no order created
        List<semicolon.africa.waylchub.model.order.Order> orders = orderRepository.findAll();
        assertThat(orders).isEmpty();
    }

    // =============================================================================
    // TEST 3: Order Cancellation → Stock Restoration
    // =============================================================================

    @Test
    @Order(3)
    @DisplayName("🔄 3. CANCELLATION: Order cancelled → Stock restored (async)")
    void testOrderCancellationRestoresStock() {
        // 1. Create product and order
        Product product = createProduct("Cancellable", "cancellable", BigDecimal.TEN);
        ProductVariant variant = createVariant(product.getId(), "CANCEL-1",
                BigDecimal.TEN, 100);

        // (✅ FIX: Used Builder)
        OrderRequest request = buildOrderRequest(
                "customer@example.com",
                List.of(OrderItemRequest.builder()
                        .variantId(variant.getId())
                        .quantity(10)
                        .build())
        );

        semicolon.africa.waylchub.model.order.Order order = orderService.createOrder(request);

        // 2. Verify stock reduced
        ProductVariant afterCheckout = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(afterCheckout.getStockQuantity()).isEqualTo(90);

        // 3. Cancel order
        semicolon.africa.waylchub.model.order.Order cancelled = orderService.cancelOrder(order.getId(), "Changed mind");

        // 4. Verify status changed
        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

        // 5. Verify stock restored (async event)
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    ProductVariant restored = variantRepository.findById(variant.getId()).orElseThrow();
                    assertThat(restored.getStockQuantity()).isEqualTo(100);
                });
    }

    // =============================================================================
    // TEST 4: Concurrent Orders (High Load Simulation)
    // =============================================================================

//    @Test
//    @Order(4)
//    @DisplayName("⚡ 4. CONCURRENCY: 20 simultaneous checkouts (draining stock to 0)")
//    void testConcurrentCheckouts() throws InterruptedException {
    // ... (Test commented out in original file) ...
//    }

    // =============================================================================
    // TEST 5: Multiple Products in One Order
    // =============================================================================
    @Test
    @Order(4)
    @DisplayName("⚡ REAL WORLD: 3 users buy 1 item each from stock of 3 at same time")
    void testThreeUsersSameMillisecond() throws InterruptedException {

        int threadCount = 5;
        int initialStock = 5;

        Product product = createProduct("Flash Sale", "flash-sale", BigDecimal.valueOf(100));
        ProductVariant variant = createVariant(product.getId(), "FLASH-1",
                BigDecimal.valueOf(100), initialStock);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int user = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // All threads start EXACT SAME TIME

                    // (✅ FIX: Used Builder)
                    OrderRequest request = buildOrderRequest(
                            "user" + user + "@mail.com",
                            List.of(OrderItemRequest.builder()
                                    .variantId(variant.getId())
                                    .quantity(1)
                                    .build())
                    );

                    orderService.createOrder(request);
                    success.incrementAndGet();

                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();   // wait until all threads are ready
        startLatch.countDown(); // release all at same millisecond
        doneLatch.await();
        executor.shutdown();

        assertThat(success.get()).isEqualTo(5);
        assertThat(failure.get()).isEqualTo(0);

        ProductVariant updated = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(0);
    }

    @Test
    @Order(5)
    @DisplayName("🛒 5. MULTI-ITEM ORDER: Multiple products, all stock reduced")
    void testMultiItemOrder() {
        // 1. Create 3 different products
        Product product1 = createProduct("Item A", "item-a", BigDecimal.TEN);
        ProductVariant variant1 = createVariant(product1.getId(), "A-1", BigDecimal.TEN, 50);

        Product product2 = createProduct("Item B", "item-b", BigDecimal.valueOf(20));
        ProductVariant variant2 = createVariant(product2.getId(), "B-1", BigDecimal.valueOf(20), 30);

        Product product3 = createProduct("Item C", "item-c", BigDecimal.valueOf(30));
        ProductVariant variant3 = createVariant(product3.getId(), "C-1", BigDecimal.valueOf(30), 20);

        // 2. Order 5 of each (✅ FIX: Used Builder)
        OrderRequest request = buildOrderRequest(
                "customer@example.com",
                List.of(
                        OrderItemRequest.builder().variantId(variant1.getId()).quantity(5).build(),
                        OrderItemRequest.builder().variantId(variant2.getId()).quantity(5).build(),
                        OrderItemRequest.builder().variantId(variant3.getId()).quantity(5).build()
                )
        );

        // 3. Create order
        semicolon.africa.waylchub.model.order.Order order = orderService.createOrder(request);

        // 4. Verify order has 3 items
        assertThat(order.getItems()).hasSize(3);

        // 5. Verify all stocks reduced
        assertThat(variantRepository.findById(variant1.getId()).get().getStockQuantity())
                .isEqualTo(45);
        assertThat(variantRepository.findById(variant2.getId()).get().getStockQuantity())
                .isEqualTo(25);
        assertThat(variantRepository.findById(variant3.getId()).get().getStockQuantity())
                .isEqualTo(15);
    }

    // =============================================================================
    // TEST 6: Inactive Product Rejection
    // =============================================================================

    @Test
    @Order(6)
    @DisplayName("🚫 6. INACTIVE PRODUCT: Cannot order deactivated product")
    void testInactiveProductRejection() {
        // 1. Create product
        Product product = createProduct("Inactive Item", "inactive", BigDecimal.TEN);
        ProductVariant variant = createVariant(product.getId(), "INACTIVE-1",
                BigDecimal.TEN, 100);

        // 2. Deactivate product
        product.setActive(false);
        productRepository.save(product);

        // 3. Try to order (✅ FIX: Used Builder)
        OrderRequest request = buildOrderRequest(
                "customer@example.com",
                List.of(OrderItemRequest.builder()
                        .variantId(variant.getId())
                        .quantity(5)
                        .build())
        );

        // 4. Verify rejection
        assertThatThrownBy(() -> orderService.createOrder(request))
                .hasMessageContaining("unavailable");
    }

    // =============================================================================
    // TEST 7: Rollback on Order Save Failure
    // =============================================================================

    @Test
    @Order(7)
    @DisplayName("↩️ 7. ROLLBACK: Order save fails → Stock automatically restored")
    void testRollbackOnOrderSaveFailure() {
        // NOTE: Handled in CriticalRollbackBugTest class
    }

    @Test
    @Order(8)
    @DisplayName("🛑 OVERSALE PROTECTION: 4 buyers for stock of 3 → only 3 succeed")
    void testOversellProtection() throws InterruptedException {

        int threadCount = 4;
        int initialStock = 3;

        Product product = createProduct("Limited Drop", "limited-drop", BigDecimal.valueOf(100));
        ProductVariant variant = createVariant(product.getId(), "DROP-1",
                BigDecimal.valueOf(100), initialStock);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // (✅ FIX: Used Builder)
                    OrderRequest req = buildOrderRequest(
                            UUID.randomUUID() + "@mail.com",
                            List.of(OrderItemRequest.builder()
                                    .variantId(variant.getId())
                                    .quantity(1)
                                    .build())
                    );
                    orderService.createOrder(req);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(success.get()).isEqualTo(3);
        assertThat(failure.get()).isEqualTo(1);

        ProductVariant updated = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(0);
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

        // Shipping address
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