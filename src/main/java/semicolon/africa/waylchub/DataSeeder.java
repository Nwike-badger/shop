package semicolon.africa.waylchub;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.productDto.ProductAttributeRequest;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.product.Brand;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.OrderRepository;
import semicolon.africa.waylchub.repository.productRepository.BrandRepository;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.service.orderService.OrderService;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private CategoryRepository categoryRepo;
    @Autowired private BrandRepository brandRepo;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepo;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;

    @Override
    public void run(String... args) throws Exception {
        // 1. CLEANUP PREVIOUS DATA
        orderRepository.deleteAll();
        productRepo.deleteAll();
        categoryRepo.deleteAll();
        brandRepo.deleteAll();

        System.out.println("🌱 SEEDING DATA...");

        // 2. CREATE BRANDS (Directly via Repo is fine for setup)
        createBrand("Nexus", "nexus");
        createBrand("Nike", "nike");

        // 3. CREATE CATEGORY HIERARCHY
        Category appliances = categoryRepo.save(createCategory("Appliances", "appliances", null));
        Category smallApp = categoryRepo.save(createCategory("Small Appliances", "small-appliances", appliances));
        categoryRepo.save(createCategory("Blenders", "blenders", smallApp));
        categoryRepo.save(createCategory("Irons", "irons", smallApp));

        // 4. CREATE PRODUCTS (Using ProductRequest DTO)

        // Product 1: A Blender
        ProductRequest p1Req = new ProductRequest();
        p1Req.setName("Nexus 3000W Heavy Duty Blender");
        p1Req.setSlug("nexus-3000w-blender");
        p1Req.setPrice(new BigDecimal("25000"));
        p1Req.setStockQuantity(10); // Initial Stock
        p1Req.setCategorySlug("blenders"); // Link via Slug
        p1Req.setBrandSlug("nexus");

        // Attributes
        ProductAttributeRequest attr1 = new ProductAttributeRequest(); attr1.setName("Power"); attr1.setValue("3000W");
        ProductAttributeRequest attr2 = new ProductAttributeRequest(); attr2.setName("Capacity"); attr2.setValue("2L");
        p1Req.setAttributes(Arrays.asList(attr1, attr2));

        // Save using Service
        Product savedBlender = productService.addOrUpdateProduct(p1Req);


        // Product 2: An Iron
        ProductRequest p2Req = new ProductRequest();
        p2Req.setName("Nexus Steam Iron");
        p2Req.setSlug("nexus-steam-iron");
        p2Req.setPrice(new BigDecimal("8000"));
        p2Req.setStockQuantity(20);
        p2Req.setCategorySlug("irons");
        p2Req.setBrandSlug("nexus");

        productService.addOrUpdateProduct(p2Req);

        System.out.println("✅ DATA SEEDED!");

        // ---------------------------------------------------------
        // 🛒 TEST: ORDER PLACEMENT
        // ---------------------------------------------------------
        System.out.println("\n🛒 TESTING ORDER PLACEMENT...");

        // Create an Order Request Item (Buying 2 blenders)
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(savedBlender.getId()); // Use ID from saved product
        itemReq.setQuantity(2);

        // Create the Order Request
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setItems(Collections.singletonList(itemReq));
        // We don't set email in Request anymore, we pass it as 'authenticated user'

        try {
            // Mocking a logged-in user email
            String userEmail = "buyer@example.com";

            Order successfulOrder = orderService.placeOrder(orderRequest, userEmail);

            System.out.println("✅ Order Placed! Order ID: " + successfulOrder.getId());
            System.out.println("   Total Paid: " + successfulOrder.getTotalAmount());

            // VERIFY STOCK REDUCTION
            Product updatedProduct = productRepo.findById(savedBlender.getId()).get();
            System.out.println("📉 Stock Check: Started with 10, Bought 2, Now: " + updatedProduct.getStockQuantity());

            if (updatedProduct.getStockQuantity() == 8) {
                System.out.println("🔥 TEST PASSED: Stock reduced correctly!");
            } else {
                System.out.println("❌ TEST FAILED: Stock mismatch!");
            }

        } catch (Exception e) {
            System.out.println("❌ Order failed: " + e.getMessage());
            e.printStackTrace();
        }

        // ---------------------------------------------------------
        // 🧪 TEST: RESTOCKING LOGIC
        // ---------------------------------------------------------
        System.out.println("\n♻️ TESTING RESTOCK...");

        ProductRequest restockReq = new ProductRequest();
        restockReq.setSlug("nexus-3000w-blender"); // Same Slug
        restockReq.setStockQuantity(5); // Adding 5 more
        restockReq.setName("Nexus 3000W Heavy Duty Blender"); // Name required for validation
        restockReq.setPrice(new BigDecimal("25000")); // Price required

        productService.addOrUpdateProduct(restockReq);

        Product restockedProduct = productRepo.findBySlug("nexus-3000w-blender").get();
        System.out.println("📈 Stock after restock (8 + 5): " + restockedProduct.getStockQuantity());
    }

    // Helper to create categories quickly
    private Category createCategory(String name, String slug, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setParent(parent);
        return c;
    }

    private void createBrand(String name, String slug) {
        Brand b = new Brand();
        b.setName(name);
        b.setSlug(slug);
        brandRepo.save(b);
    }
}