package semicolon.africa.waylchub;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
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
import semicolon.africa.waylchub.model.order.Address;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.orderRepository.OrderRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
import semicolon.africa.waylchub.service.orderService.OrderService;
import semicolon.africa.waylchub.service.productService.ProductService;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;



@SpringBootTest(properties = {
        "app.tax.vat-rate=0.075",
        "app.shippingFee=2500.00"
})
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired private OrderService orderService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    // We use SpyBean to intercept DB calls and simulate a crash for the rollback test
    @MockitoSpyBean
     private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        variantRepository.deleteAll();
        orderRepository.deleteAll();
        Mockito.reset(orderRepository); // Reset the spy between tests
    }

    // =========================================================================
    // 1. HAPPY PATH: CREATE ORDER & CALCULATE TOTALS
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("✅ 1. Create Order: Verifies prices, deducts stock, calculates totals")
    void testCreateOrderHappyPath() {
        // Setup: Product with 10 stock, priced at 10,000
        Product p = createProduct("Laptop", "laptop", true);
        ProductVariant v = createVariant(p.getId(), "MAC-01", new BigDecimal("10000.00"), 10);

        // Execute: User buys 2 laptops
        OrderRequest request = createStandardRequest(v.getId(), 2);
        Order order = orderService.createOrder(request);

        // Verify Order Details
        assertThat(order).isNotNull();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getItems()).hasSize(1);

        // Verify Denormalized Snapshot (Prices pulled from DB, not client)
        assertThat(order.getItems().get(0).getProductName()).isEqualTo("Laptop");
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo("10000.00");
        assertThat(order.getItems().get(0).getSubTotal()).isEqualByComparingTo("20000.00");

        // Verify Financial Math (Subtotal: 20000 + Shipping: 2500 + Tax(7.5%): 1500 = 24000)
        assertThat(order.getItemSubTotal()).isEqualByComparingTo("20000.00");
        assertThat(order.getShippingFee()).isEqualByComparingTo("2500.00");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1500.00");
        assertThat(order.getGrandTotal()).isEqualByComparingTo("24000.00");

        // Verify Stock Deducted
        ProductVariant updatedVariant = variantRepository.findById(v.getId()).orElseThrow();
        assertThat(updatedVariant.getStockQuantity()).isEqualTo(8); // 10 - 2
    }

    // =========================================================================
    // 2. CONTRACT: OUT OF STOCK REJECTION
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("🛑 2. Prevent Checkout: Insufficient Stock")
    void testInsufficientStockRejection() {
        Product p = createProduct("Sneakers", "sneakers", true);
        ProductVariant v = createVariant(p.getId(), "SNK-01", new BigDecimal("5000"), 3); // Only 3 in stock

        OrderRequest request = createStandardRequest(v.getId(), 5); // Trying to buy 5

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock");

        // Verify stock wasn't touched
        ProductVariant updatedVariant = variantRepository.findById(v.getId()).orElseThrow();
        assertThat(updatedVariant.getStockQuantity()).isEqualTo(3);
    }

    // =========================================================================
    // 3. CONTRACT: INACTIVE PRODUCT REJECTION
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("🛑 3. Prevent Checkout: Product is Inactive/Hidden")
    void testInactiveProductRejection() {
        Product p = createProduct("Old TV", "old-tv", false); // INACTIVE
        ProductVariant v = createVariant(p.getId(), "TV-01", new BigDecimal("5000"), 100);

        OrderRequest request = createStandardRequest(v.getId(), 1);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Product is no longer available for purchase");

        // Verify stock wasn't touched
        ProductVariant updatedVariant = variantRepository.findById(v.getId()).orElseThrow();
        assertThat(updatedVariant.getStockQuantity()).isEqualTo(100);
    }

    // =========================================================================
    // 4. CONTRACT: FRONTEND DUPLICATE CONSOLIDATION (Exceeds Standard)
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("🛡️ 4. Consolidate Duplicate Items from Frontend")
    void testDuplicateItemConsolidation() {
        Product p = createProduct("Mouse", "mouse", true);
        ProductVariant v = createVariant(p.getId(), "MS-01", new BigDecimal("1000"), 10);

        OrderRequest request = new OrderRequest();
        request.setCustomerEmail("test@test.com");

        // Frontend bug: Sends the same item twice in the array (qty 2 and qty 3)
        OrderItemRequest item1 = new OrderItemRequest();
        item1.setVariantId(v.getId()); item1.setQuantity(2);

        OrderItemRequest item2 = new OrderItemRequest();
        item2.setVariantId(v.getId()); item2.setQuantity(3);

        request.setItems(List.of(item1, item2));

        Order order = orderService.createOrder(request);

        // Should consolidate into ONE line item with qty 5
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(5);

        // Stock should be 10 - 5 = 5
        ProductVariant updatedVariant = variantRepository.findById(v.getId()).orElseThrow();
        assertThat(updatedVariant.getStockQuantity()).isEqualTo(5);
    }

    // =========================================================================
    // 5. CONTRACT: ROLLBACK PROTECTION (The Disaster Scenario)
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("🚑 5. Rollback Protection: DB Crash Restores Stock")
    void testOrderSaveFailureRollsBackStock() {
        Product p = createProduct("Rolex", "rolex", true);
        ProductVariant v = createVariant(p.getId(), "RLX-01", new BigDecimal("500000"), 5);

        OrderRequest request = createStandardRequest(v.getId(), 1);

        // MOCK THE DISASTER: Force the database to throw an error EXACTLY when saving the order
        Mockito.doThrow(new RuntimeException("Simulated Database Crash!"))
                .when(orderRepository).save(any(Order.class));

        // Execute: Try to buy
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Checkout failed due to a system error");

        // VERIFY ROLLBACK: The atomic deduction happened, but our catch block successfully refunded it!
        ProductVariant updatedVariant = variantRepository.findById(v.getId()).orElseThrow();
        assertThat(updatedVariant.getStockQuantity()).isEqualTo(5); // Stock remains 5!
    }

    // =========================================================================
    // 6. CONTRACT: ORDER CANCELLATION
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("🔄 6. Cancel Order: Restores Stock & Updates Status")
    void testCancelOrderRestoresStock() {
        Product p = createProduct("Camera", "camera", true);
        ProductVariant v = createVariant(p.getId(), "CAM-01", new BigDecimal("500"), 10);

        // 1. Checkout (Stock goes to 8)
        Order order = orderService.createOrder(createStandardRequest(v.getId(), 2));

        ProductVariant afterCheckout = variantRepository.findById(v.getId()).orElseThrow();
        assertThat(afterCheckout.getStockQuantity()).isEqualTo(8);

        // 2. Cancel Order (Force a real DB commit so the AFTER_COMMIT event fires)
        Order cancelledOrder = transactionTemplate.execute(status -> {
            return orderService.cancelOrder(order.getId(), "Customer requested");
        });

        // Verify Status updated instantly
        assertThat(cancelledOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

        // ✅ FIX: Wait for the async listener to process the event
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            // Verify Variant Stock Restored
            ProductVariant afterCancel = variantRepository.findById(v.getId()).orElseThrow();
            assertThat(afterCancel.getStockQuantity()).isEqualTo(10); // Back to 10

            // Verify Parent Stock Restored
            Product updatedParent = productService.getProductById(p.getId());
            assertThat(updatedParent.getTotalStock()).isEqualTo(10);
        });
    }

    // =========================================================================
    // 7. CONTRACT: CANCELLATION GUARDRAILS
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("🛡️ 7. Prevent Cancellation of Shipped Orders")
    void testCannotCancelShippedOrder() {
        Product p = createProduct("Desk", "desk", true);
        ProductVariant v = createVariant(p.getId(), "DSK-01", new BigDecimal("150"), 10);

        Order order = orderService.createOrder(createStandardRequest(v.getId(), 1));

        // Admin updates status to SHIPPED
        orderService.updateOrderStatus(order.getId(), OrderStatus.SHIPPED, "Handed to Courier");

        // Customer tries to cancel
        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), "Changed mind"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel an order that has already been shipped");
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Product createProduct(String name, String slug, boolean isActive) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setSlug(slug);
        req.setBasePrice(BigDecimal.TEN);
        req.setIsActive(isActive);
        return productService.createOrUpdateProduct(req);
    }

    private ProductVariant createVariant(String productId, String sku, BigDecimal price, int stock) {
        VariantRequest req = new VariantRequest();
        req.setProductId(productId);
        req.setSku(sku);
        req.setPrice(price);
        req.setStockQuantity(stock);
        req.setAttributes(Map.of("SKU", sku));
        return productService.saveVariant(req);
    }

    private OrderRequest createStandardRequest(String variantId, int quantity) {
        OrderRequest request = new OrderRequest();
        request.setCustomerEmail("user@visitaba.com");

        OrderItemRequest item = new OrderItemRequest();
        item.setVariantId(variantId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));

        Address address = Address.builder()
                .firstName("John").lastName("Doe")
                .streetAddress("123 Market Road")
                .city("Aba").state("Abia")
                .build();

        request.setShippingAddress(address);
        request.setPaymentMethod("PAYSTACK");

        return request;
    }
}