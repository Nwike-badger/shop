package semicolon.africa.waylchub;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.dto.productDto.VariantRequest;
import semicolon.africa.waylchub.model.product.Brand;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductImage;
import semicolon.africa.waylchub.repository.productRepository.BrandRepository;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepo;
    private final BrandRepository brandRepo;
    private final ProductRepository productRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductService productService;
    private final MongoTemplate mongoTemplate;

    private final Random random = new Random();

    @Override
    public void run(String... args) {
        System.out.println("🌱 STARTING PRODUCTION-GRADE SEEDER...");

        try {
            mongoTemplate.indexOps(Product.class).ensureIndex(
                    new TextIndexDefinition.TextIndexDefinitionBuilder()
                            .onField("name")
                            .onField("description")
                            .onField("brandName")
                            .named("text_search")
                            .build()
            );
        } catch (Exception e) {
            System.out.println("⚠️ Index creation warning: " + e.getMessage());
        }

        variantRepo.deleteAll();
        productRepo.deleteAll();
        categoryRepo.deleteAll();
        brandRepo.deleteAll();

        createBrands();
        createCategoriesAndProducts();

        System.out.println("✅ SEEDING COMPLETE! You now have data for all sections.");
    }

    private void createBrands() {
        List<String> brands = List.of("Apple", "Samsung", "Oraimo", "Nike", "Adidas", "Lagos-Tailors", "Dangote", "Binatone");
        for (String b : brands) {
            Brand brand = new Brand();
            brand.setName(b);
            brand.setSlug(b.toLowerCase());
            brandRepo.save(brand);
        }
    }

    private void createCategoriesAndProducts() {
        // --- Categories ---
        Category electronics = saveCat("Electronics", "electronics", null, "https://images.unsplash.com/photo-1498049860654-af5a11528db3?auto=format&fit=crop&w=600&q=80");
        Category fashion = saveCat("Fashion", "fashion", null, "https://images.unsplash.com/photo-1445205170230-05328324f375?auto=format&fit=crop&w=600&q=80");
        Category home = saveCat("Home & Living", "home-living", null, "https://images.unsplash.com/photo-1484101403633-562f891dc89a?auto=format&fit=crop&w=600&q=80");

        Category phones = saveCat("Phones", "phones", electronics, null);
        Category men = saveCat("Men", "men-fashion", fashion, null);
        Category kitchen = saveCat("Kitchen", "kitchen", home, null);

        // --- SPECIFIC HIGH QUALITY PRODUCTS (Your existing ones) ---
        createComplexProduct(
                "iPhone 15 Pro Max", "iphone-15-pro-max", phones, "apple",
                new BigDecimal("1800000"),
                Map.of("Color", List.of("Titanium")),
                List.of(new VariantConfig("IP15-TI", new BigDecimal("1800000"), 10, Map.of("Color", "Titanium")))
        );

        createComplexProduct(
                "Nike Air Max 2025", "nike-air-max-2025", men, "nike",
                new BigDecimal("150000"),
                Map.of("Size", List.of("42", "43")),
                List.of(new VariantConfig("NK-42", new BigDecimal("150000"), 5, Map.of("Size", "42")))
        );

        // --- 🔥 THE FIX: GENERATE 35 FILLER PRODUCTS ---
        // This ensures indices 6-12 (Top Trends) and 12-22 (Explore) are filled
        generateFillerProducts(List.of(electronics, fashion, home, phones, men, kitchen));
    }

    private void generateFillerProducts(List<Category> categories) {
        String[] adjectives = {"Premium", "Luxury", "Durable", "Sleek", "Modern", "Classic", "Urban"};
        String[] nouns = {"Gadget", "Accessory", "Tool", "Device", "Outfit", "Kit"};

        for (int i = 1; i <= 35; i++) {
            Category randomCat = categories.get(random.nextInt(categories.size()));
            String name = adjectives[random.nextInt(adjectives.length)] + " " + nouns[random.nextInt(nouns.length)] + " " + i;

            ProductRequest req = new ProductRequest();
            req.setName(name);
            req.setSlug("gen-item-" + i + "-" + System.currentTimeMillis());
            req.setBasePrice(new BigDecimal(random.nextInt(45000) + 5000)); // Price between 5k and 50k
            req.setCategorySlug(randomCat.getSlug());
            req.setBrandSlug("apple");
            req.setDescription("This is a generated product to fill the store layout.");

            // Random Unsplash Image
            req.setImages(List.of(new ProductImage("https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=400&q=80", true)));

            // Randomly apply discount
            if(random.nextBoolean()) {
                req.setDiscount(new BigDecimal(random.nextInt(20) + 5)); // 5% to 25% discount
            }

            productService.createOrUpdateProduct(req);
        }
        System.out.println("⚡ Generated 35 filler products.");
    }

    // --- Helpers ---
    private Category saveCat(String name, String slug, Category parent, String img) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setParent(parent);
        c.setImageUrl(img == null ? "https://placehold.co/600x400" : img);
        c.setLineage(parent != null ? (parent.getLineage() == null ? "," : parent.getLineage()) + parent.getId() + "," : ",");
        return categoryRepo.save(c);
    }

    private void createComplexProduct(String name, String slug, Category cat, String brandSlug, BigDecimal basePrice, Map<String, List<String>> options, List<VariantConfig> configs) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setSlug(slug);
        req.setBasePrice(basePrice);
        req.setCategorySlug(cat.getSlug());
        req.setBrandSlug(brandSlug);
        req.setDescription("Description for " + name);
        req.setImages(List.of(new ProductImage("https://placehold.co/400", true)));
        req.setVariantOptions(options);
        Product p = productService.createOrUpdateProduct(req);

        for (VariantConfig c : configs) {
            VariantRequest v = new VariantRequest();
            v.setProductId(p.getId());
            v.setSku(c.sku);
            v.setPrice(c.price);
            v.setStockQuantity(c.stock);
            v.setAttributes(c.attributes);
            v.setImages(List.of(new ProductImage("https://placehold.co/400", true)));
            productService.saveVariant(v);
        }
    }

    private record VariantConfig(String sku, BigDecimal price, int stock, Map<String, String> attributes) {}
}