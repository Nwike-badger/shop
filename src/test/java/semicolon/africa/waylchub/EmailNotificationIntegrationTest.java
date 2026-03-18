//package semicolon.africa.waylchub;
//
//import org.awaitility.Awaitility;
//import org.junit.jupiter.api.*;
//import org.mockito.Mockito;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
//import org.testcontainers.containers.MongoDBContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
//import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
//import semicolon.africa.waylchub.dto.productDto.ProductRequest;
//import semicolon.africa.waylchub.dto.productDto.VariantRequest;
//import semicolon.africa.waylchub.model.order.Address;
//import semicolon.africa.waylchub.model.order.Order;
//import semicolon.africa.waylchub.model.product.Product;
//import semicolon.africa.waylchub.model.product.ProductVariant;
//import semicolon.africa.waylchub.repository.orderRepository.OrderRepository;
//import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
//import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
//import semicolon.africa.waylchub.service.emailService.EmailService;
//import semicolon.africa.waylchub.service.orderService.OrderService;
//import semicolon.africa.waylchub.service.productService.ProductService;
//
//import java.math.BigDecimal;
//import java.time.Duration;
//import java.util.List;
//import java.util.Map;
//
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.ArgumentMatchers.contains;
//
//@SpringBootTest
//@Testcontainers
//class EmailNotificationIntegrationTest {
//
//    @Container
//    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
//            .withExposedPorts(27017);
//
//    @DynamicPropertySource
//    static void setProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
//    }
//
//    @Autowired private OrderService orderService;
//    @Autowired private ProductService productService;
//    @Autowired private ProductRepository productRepository;
//    @Autowired private ProductVariantRepository variantRepository;
//    @Autowired private OrderRepository orderRepository;
//
//    // We spy on the EmailService so it actually sends the email,
//    // but we can also track its method calls!
//    @MockitoSpyBean
//    private EmailService emailService;
//
//    @BeforeEach
//    void setUp() {
//        productRepository.deleteAll();
//        variantRepository.deleteAll();
//        orderRepository.deleteAll();
//        Mockito.reset(emailService);
//    }
//
//    @Test
//    @DisplayName("📧 Live Test: Successful payment triggers HTML receipt email")
//    void testSuccessfulPaymentTriggersEmail() {
//        // 1. Setup Data
//        Product product = createProduct("Nike Air Force 1", "nike-af1", new BigDecimal("45000"));
//        ProductVariant variant = createVariant(product.getId(), "AF1-WHT-42", new BigDecimal("45000"), 10);
//
//
//        String myPersonalEmail = "CHRISCHIDI29@GMAIL.COM";
//
//        OrderRequest request = buildOrderRequest(myPersonalEmail, variant.getId(), 1);
//
//        // 2. Create the Order (This handles the DB transaction)
//        Order order = orderService.createOrder(request);
//
//        // 3. Trigger Payment Success
//        // This method saves to the DB and fires the @TransactionalEventListener OrderPaidEvent
//        String fakeMonnifyRef = "MNFY-TEST-998877";
//        orderService.processSuccessfulPayment(order.getId(), fakeMonnifyRef);
//
//        // 4. Verify the Async Email Thread
//        // Since the email sends on a background thread, we must wait for it.
//        Awaitility.await()
//                .atMost(Duration.ofSeconds(10)) // Give Resend API up to 10 seconds to respond
//                .untilAsserted(() -> {
//
//                    // Verify that the sendHtmlEmail method was called exactly once
//                    Mockito.verify(emailService, Mockito.times(1)).sendHtmlEmail(
//                            eq(myPersonalEmail), // Sent to the right person
//                            contains(order.getOrderNumber()), // Subject contains order number
//                            anyString() // The compiled Thymeleaf HTML string
//                    );
//                });
//
//        System.out.println("✅ Email verified! Check your inbox for order: " + order.getOrderNumber());
//    }
//
//    // =========================================================================
//    // HELPER METHODS
//    // =========================================================================
//
//    private Product createProduct(String name, String slug, BigDecimal price) {
//        ProductRequest req = new ProductRequest();
//        req.setName(name);
//        req.setSlug(slug);
//        req.setBasePrice(price);
//        req.setIsActive(true);
//        return productService.createOrUpdateProduct(req);
//    }
//
//    private ProductVariant createVariant(String productId, String sku, BigDecimal price, int stock) {
//        VariantRequest req = new VariantRequest();
//        req.setProductId(productId);
//        req.setSku(sku);
//        req.setPrice(price);
//        req.setStockQuantity(stock);
//        req.setAttributes(Map.of("Color", "White"));
//        req.setIsActive(true);
//        return productService.saveVariant(req);
//    }
//
//    private OrderRequest buildOrderRequest(String email, String variantId, int quantity) {
//        OrderRequest req = new OrderRequest();
//        req.setCustomerEmail(email);
//
//        OrderItemRequest item = new OrderItemRequest();
//        item.setVariantId(variantId);
//        item.setQuantity(quantity);
//        req.setItems(List.of(item));
//
//        req.setPaymentMethod("MONNIFY");
//
//        Address address = Address.builder()
//                .firstName("Test").lastName("User")
//                .streetAddress("123 Test St")
//                .city("Lagos").state("Lagos")
//                .build();
//
//        req.setShippingAddress(address);
//        return req;
//    }
//}