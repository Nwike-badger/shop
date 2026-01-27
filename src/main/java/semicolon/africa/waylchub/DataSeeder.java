package semicolon.africa.waylchub;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.dto.productDto.ProductAttributeRequest;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.model.product.Brand;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.ProductImage; // ✅ Import the new model
import semicolon.africa.waylchub.repository.OrderRepository;
import semicolon.africa.waylchub.repository.productRepository.BrandRepository;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections; // ✅ Added
import java.util.List;
import java.util.Random;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private CategoryRepository categoryRepo;
    @Autowired private BrandRepository brandRepo;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepo;
    @Autowired private OrderRepository orderRepository;

    private final Random random = new Random();

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🌱 INITIALIZING SYSTEM CLEANUP...");
        orderRepository.deleteAll();
        productRepo.deleteAll();
        categoryRepo.deleteAll();
        brandRepo.deleteAll();

        // 1. CREATE BRANDS
        System.out.println("🏷️ Seeding Brands...");
        List<String> brandNames = Arrays.asList(
                "Nexus", "Hisense", "Polystar", "TCL", "Binatone",
                "Samsung", "Apple", "Tecno", "Itel", "Xiaomi", "LG"
        );
        for (String name : brandNames) {
            Brand b = new Brand();
            b.setName(name);
            b.setSlug(name.toLowerCase());
            brandRepo.save(b);
        }

        // ==========================================
        // 2. ROOT: HOME APPLIANCES
        // ==========================================
        System.out.println("🏠 Seeding Home Appliances Hierarchy...");
        Category homeAppliances = saveCat("Home Appliances", "home-appliances", null);

        // Level 2
        Category smallApp = saveCat("Small Appliances", "small-appliances", homeAppliances);
        Category largeApp = saveCat("Large Appliances", "large-appliances", homeAppliances);

        // Level 3 (Small App Leaves)
        List<Category> smallAppLeaves = List.of(
                saveCat("Blenders", "blenders", smallApp),
                saveCat("Juicers", "juicers", smallApp),
                saveCat("Air Fryers", "air-fryers", smallApp),
                saveCat("Rice Cookers", "rice-cookers", smallApp),
                saveCat("Kettles", "kettles", smallApp),
                saveCat("Irons", "irons", smallApp),
                saveCat("Coffee Makers", "coffee-makers", smallApp)
        );

        // Level 3 (Large App Leaves)
        List<Category> largeAppLeaves = List.of(
                saveCat("Washing Machines", "washing-machines", largeApp),
                saveCat("Fridges", "fridges", largeApp),
                saveCat("Generators & Inverters", "generators", largeApp),
                saveCat("Air Conditioners", "air-conditioners", largeApp)
        );

        // ==========================================
        // 3. ROOT: MOBILE PHONES & TABLETS
        // ==========================================
        System.out.println("📱 Seeding Mobile Phones & Tablets Hierarchy...");
        Category mobileRoot = saveCat("Mobile Phones & Tablets", "mobile-phones-tablets", null);

        // Level 2
        Category mobilePhones = saveCat("Mobile Phones", "mobile-phones", mobileRoot);
        Category tablets = saveCat("Tablets", "tablets", mobileRoot);
        Category accessories = saveCat("Mobile Accessories", "mobile-accessories", mobileRoot);
        Category topPhones = saveCat("Top Smartphones", "top-smartphones", mobileRoot);
        // Removed unused "Top Phone Brands" to keep it clean

        // Level 3: Mobile Phone Varieties
        Category smartphones = saveCat("Smartphones", "smartphones", mobilePhones);
        Category androidPhones = saveCat("Android Phones", "android-phones", smartphones);
        Category iphones = saveCat("iPhones", "iphones", smartphones);

        List<Category> mobilePhoneLeaves = List.of(
                androidPhones, iphones,
                saveCat("Basic Phones", "basic-phones", mobilePhones),
                saveCat("Refurbished Phones", "refurbished-phones", mobilePhones),
                saveCat("Rugged Phones", "rugged-phones", mobilePhones)
        );

        // Level 3: Tablet Leaves
        List<Category> tabletLeaves = List.of(
                saveCat("iPads", "ipads", tablets),
                saveCat("Android Tablets", "android-tablets", tablets),
                saveCat("Educational Tablets", "educational-tablets", tablets)
        );

        // Level 3: Accessory Leaves
        List<Category> accessoryLeaves = List.of(
                saveCat("Chargers", "chargers", accessories),
                saveCat("Power Banks", "power-banks", accessories),
                saveCat("Earphones & Headsets", "earphones-headsets", accessories),
                saveCat("Cables", "cables", accessories)
        );

        // Level 3: Specific Top Models
        List<Category> topPhoneLeaves = List.of(
                saveCat("iPhone 15 & 15 Pro Max", "iphone-15", topPhones),
                saveCat("Samsung Galaxy S24 & S24 Ultra", "samsung-s24", topPhones),
                saveCat("Tecno Spark 20 & 20 Pro", "tecno-spark-20", topPhones)
        );

        // ==========================================
        // 4. PRODUCT SEEDING
        // ==========================================
        System.out.println("📦 Generating Products...");

        // Seed Home Appliances
        for (Category leaf : smallAppLeaves) {
            for (int i = 1; i <= 3; i++) createProduct(leaf, "Nexus", i);
        }
        for (Category leaf : largeAppLeaves) {
            for (int i = 1; i <= 3; i++) createProduct(leaf, "Hisense", i);
        }

        // Seed Mobile & Tablets
        for (Category leaf : mobilePhoneLeaves) {
            for (int i = 1; i <= 4; i++) createProduct(leaf, "Samsung", i);
        }
        for (Category leaf : tabletLeaves) {
            for (int i = 1; i <= 3; i++) createProduct(leaf, "Apple", i);
        }
        for (Category leaf : accessoryLeaves) {
            for (int i = 1; i <= 5; i++) createProduct(leaf, "Binatone", i);
        }
        for (Category leaf : topPhoneLeaves) {
            for (int i = 1; i <= 2; i++) createProduct(leaf, "Apple", i);
        }

        System.out.println("✅ SEEDING COMPLETE!");
        // runFinalChecks(); // Commented out to avoid cluttering logs, enable if needed
    }

    private void createProduct(Category category, String brand, int index) {
        ProductRequest req = new ProductRequest();
        String name = brand + " " + category.getName() + " Model-" + index;

        req.setName(name);
        // Unique slug generation
        req.setSlug(name.toLowerCase().replace(" ", "-") + "-" + random.nextInt(10000));
        req.setPrice(new BigDecimal(15000 + random.nextInt(900000)));
        req.setCategorySlug(category.getSlug());
        req.setBrandSlug(brand.toLowerCase());
        req.setStockQuantity(20 + random.nextInt(100));

        // ✅ FIX: Create the Image List structure
        String imageUrl = getImageForCategory(category.getSlug());

        // Create a list with one primary image
        List<ProductImage> images = new ArrayList<>();
        images.add(new ProductImage(imageUrl, true));

        // (Optional) Add a second, non-primary image for effect
        images.add(new ProductImage("https://via.placeholder.com/600x600?text=Side+View", false));

        req.setImages(images);

        req.setAttributes(Arrays.asList(
                new ProductAttributeRequest("Condition", "New"),
                new ProductAttributeRequest("Warranty", "12 Months")
        ));

        productService.addOrUpdateProduct(req);
    }


    private Category saveCat(String name, String slug, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setParent(parent);

        // ✅ Lineage Logic
        if (parent != null) {
            String parentLineage = parent.getLineage() == null ? "," : parent.getLineage();
            c.setLineage(parentLineage + parent.getId() + ",");
        } else {
            c.setLineage(",");
        }

        return categoryRepo.save(c);
    }

    private String getImageForCategory(String slug) {
        slug = slug.toLowerCase();

        if (slug.contains("iphone")) return "https://images.unsplash.com/photo-1592286927505-1def25115558?auto=format&fit=crop&w=600";
        if (slug.contains("samsung") || slug.contains("android")) return "https://images.unsplash.com/photo-1610945431162-32753239611d?auto=format&fit=crop&w=600";
        if (slug.contains("pixel")) return "https://images.unsplash.com/photo-1598327105666-5b89351aff70?auto=format&fit=crop&w=600";

        if (slug.contains("tablet") || slug.contains("ipad")) return "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=600";

        if (slug.contains("laptop") || slug.contains("macbook")) return "https://images.unsplash.com/photo-1517336714731-489689fd1ca4?auto=format&fit=crop&w=600";

        if (slug.contains("watch")) return "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=600";
        if (slug.contains("headphone") || slug.contains("audio")) return "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=600";

        if (slug.contains("blender")) return "https://images.unsplash.com/photo-1570222094114-28a9d8890b7b?auto=format&fit=crop&w=600";
        if (slug.contains("fridge")) return "https://images.unsplash.com/photo-1571175443880-49e1d58b95da?auto=format&fit=crop&w=600";
        if (slug.contains("washing")) return "https://images.unsplash.com/photo-1626806775351-5c7c52b81b48?auto=format&fit=crop&w=600";
        if (slug.contains("tv") || slug.contains("televis")) return "https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?auto=format&fit=crop&w=600";
        if (slug.contains("iron")) return "https://images.unsplash.com/photo-1585787682283-a9d560c73796?auto=format&fit=crop&w=600";

        if (slug.contains("charger") || slug.contains("power")) return "https://images.unsplash.com/photo-1583863788434-e58a36330cf0?auto=format&fit=crop&w=600";

        // Fallback for anything else
        return "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600";
    }
}