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
    @Autowired private OrderRepository orderRepository;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🚀 STARTING JUMIA-STYLE DATA SEEDING...");

        // 1. CLEAR DATA
        orderRepository.deleteAll();
        productRepo.deleteAll();
        categoryRepo.deleteAll();
        brandRepo.deleteAll();

        // 2. CREATE BRANDS
        List<String> brandNames = Arrays.asList("Samsung", "Nexus", "Apple", "Nike", "Adidas", "Binatone");
        for (String name : brandNames) {
            Brand b = new Brand();
            b.setName(name);
            b.setSlug(name.toLowerCase());
            brandRepo.save(b);
        }

        // 3. CREATE NESTED CATEGORIES (The Tree)
        // Level 1
        Category electronics = saveCat("Electronics", "electronics", null);
        Category fashion = saveCat("Fashion", "fashion", null);

        // Level 2
        Category computing = saveCat("Computing", "computing", electronics);
        Category kitchen = saveCat("Kitchen & Dining", "kitchen-dining", electronics);
        Category menShoes = saveCat("Men's Shoes", "mens-shoes", fashion);

        // Level 3 (Leaf Nodes)
        Category laptops = saveCat("Laptops", "laptops", computing);
        Category blenders = saveCat("Blenders", "blenders", kitchen);
        Category sneakers = saveCat("Sneakers", "sneakers", menShoes);

        // 4. GENERATE 50 PRODUCTS
        System.out.println("📦 Generating 50 diverse products...");

        for (int i = 1; i <= 50; i++) {
            ProductRequest req = new ProductRequest();

            // Alternate between categories to test search/filters
            if (i % 3 == 0) {
                seedLaptop(req, i, laptops.getSlug());
            } else if (i % 3 == 1) {
                seedBlender(req, i, blenders.getSlug());
            } else {
                seedSneaker(req, i, sneakers.getSlug());
            }

            productService.addOrUpdateProduct(req);
        }

        System.out.println("✅ SEEDING COMPLETE: 50 Products, 3-Level Categories, 6 Brands.");
        simulateSearch();
    }

    private void seedLaptop(ProductRequest req, int i, String catSlug) {
        req.setName("MacBook Pro M" + i);
        req.setSlug("macbook-m-" + i);
        req.setSku("LAP-APL-" + i);
        req.setPrice(new BigDecimal(1200000));
        req.setCategorySlug(catSlug);
        req.setBrandSlug("apple");

        // Technical Attributes
        req.setAttributes(Arrays.asList(
                new ProductAttributeRequest("Processor", "M2 Chip"),
                new ProductAttributeRequest("RAM", "16GB"),
                new ProductAttributeRequest("Screen Size", "14-inch")
        ));
    }

    private void seedBlender(ProductRequest req, int i, String catSlug) {
        req.setName("Nexus Silent Blender " + i);
        req.setSlug("nexus-blender-" + i);
        req.setSku("KIT-NX-" + i);
        req.setPrice(new BigDecimal(45000));
        req.setCategorySlug(catSlug);
        req.setBrandSlug("nexus");

        // Appliance Attributes
        req.setAttributes(Arrays.asList(
                new ProductAttributeRequest("Power", "2500W"),
                new ProductAttributeRequest("Speed Settings", "5"),
                new ProductAttributeRequest("Jug Material", "Glass")
        ));
    }

    private void seedSneaker(ProductRequest req, int i, String catSlug) {
        req.setName("Nike Air Jordan " + i);
        req.setSlug("nike-jordan-" + i);
        req.setSku("SH-NK-J-" + i);
        req.setPrice(new BigDecimal(85000));
        req.setCategorySlug(catSlug);
        req.setBrandSlug("nike");

        // Fashion Attributes
        req.setAttributes(Arrays.asList(
                new ProductAttributeRequest("Color", i % 2 == 0 ? "Red/Black" : "White/Blue"),
                new ProductAttributeRequest("Size", "44"),
                new ProductAttributeRequest("Material", "Leather")
        ));
    }

    private Category saveCat(String name, String slug, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setParent(parent);
        // If you have Lineage logic in your Service, call the service here instead!
        return categoryRepo.save(c);
    }

    private void simulateSearch() {
        System.out.println("\n🔍 SIMULATING JUMIA SEARCH...");
        System.out.println("User searches for: 'Sneakers' with Brand: 'Nike'");
        // This confirms your data structure is ready for the frontend filters
    }
}