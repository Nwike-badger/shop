package semicolon.africa.waylchub;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
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

    private final Random random = new Random();

    @Override
    public void run(String... args) {
        System.out.println("🌱 STARTING PRODUCTION-GRADE SEEDER...");

        // 1. WIPE DATA
        variantRepo.deleteAll();
        productRepo.deleteAll();
        categoryRepo.deleteAll();
        brandRepo.deleteAll();

        // 2. CREATE BRANDS
        List<String> brands = List.of("Apple", "Samsung", "Oraimo", "Nike", "Adidas", "Lagos-Tailors", "Dangote", "Binatone");
        for (String b : brands) {
            Brand brand = new Brand();
            brand.setName(b);
            brand.setSlug(b.toLowerCase());
            brandRepo.save(brand);
        }

        // 3. CREATE CATEGORY TREE
        // Root: Electronics
        Category electronics = saveCat("Electronics", "electronics", null, "img-elec.jpg");
        Category phones = saveCat("Phones & Tablets", "phones-tablets", electronics, "img-phone.jpg");
        Category iphones = saveCat("iPhones", "iphones", phones, "img-iphone.jpg");
        Category android = saveCat("Android", "android-phones", phones, "img-android.jpg");
        Category audio = saveCat("Audio", "audio", electronics, "img-audio.jpg");

        // Root: Fashion
        Category fashion = saveCat("Fashion", "fashion", null, "img-fashion.jpg");
        Category men = saveCat("Men", "men-fashion", fashion, "img-men.jpg");
        Category menShoes = saveCat("Sneakers", "men-sneakers", men, "img-sneaker.jpg");
        Category nativeWear = saveCat("Native Wear", "native-wear", men, "img-native.jpg");

        // 4. CREATE PRODUCTS

        // SCENARIO A: Complex Tech Product (iPhone 15 - Variants: Color + Storage)
        createComplexProduct(
                "iPhone 15 Pro Max", "iphone-15-pro-max", iphones, "apple",
                new BigDecimal("1800000"),
                Map.of("Color", List.of("Titanium", "Blue"), "Storage", List.of("256GB", "512GB")),
                List.of(
                        // Variant 1
                        new VariantConfig("SKU-IP15-TI-256", new BigDecimal("1800000"), 10, Map.of("Color", "Titanium", "Storage", "256GB")),
                        // Variant 2
                        new VariantConfig("SKU-IP15-BL-256", new BigDecimal("1800000"), 5, Map.of("Color", "Blue", "Storage", "256GB")),
                        // Variant 3 (Higher Price)
                        new VariantConfig("SKU-IP15-TI-512", new BigDecimal("2100000"), 3, Map.of("Color", "Titanium", "Storage", "512GB"))
                )
        );

        // SCENARIO B: Fashion Product (Sneakers - Variants: Size)
        createComplexProduct(
                "Adidas Ultraboost", "adidas-ultraboost", menShoes, "adidas",
                new BigDecimal("120000"),
                Map.of("Size", List.of("42", "43", "44", "45")),
                List.of(
                        new VariantConfig("SKU-AD-42", new BigDecimal("120000"), 5, Map.of("Size", "42")),
                        new VariantConfig("SKU-AD-43", new BigDecimal("120000"), 0, Map.of("Size", "43")), // Out of stock test
                        new VariantConfig("SKU-AD-44", new BigDecimal("120000"), 2, Map.of("Size", "44"))
                )
        );

        // SCENARIO C: Simple Product (Native Wear - Just one "Default" variant implied or explicit)
        createComplexProduct(
                "Senator Suit - Navy Blue", "senator-blue", nativeWear, "lagos-tailors",
                new BigDecimal("45000"),
                Map.of("Size", List.of("L", "XL")),
                List.of(
                        new VariantConfig("SKU-SEN-L", new BigDecimal("45000"), 20, Map.of("Size", "L")),
                        new VariantConfig("SKU-SEN-XL", new BigDecimal("45000"), 15, Map.of("Size", "XL"))
                )
        );

        // SCENARIO D: Accessories (Simple, no variations really, but mapped as 1 variant)
        createComplexProduct(
                "Oraimo Freepods 4", "oraimo-freepods-4", audio, "oraimo",
                new BigDecimal("28000"),
                Map.of("Color", List.of("Black")),
                List.of(new VariantConfig("SKU-ORA-BLK", new BigDecimal("28000"), 100, Map.of("Color", "Black")))
        );

        System.out.println("✅ SEEDING COMPLETE! Front-end ready.");
    }

    // ================= HELPER METHODS =================

    private void createComplexProduct(
            String name, String slug, Category cat, String brandSlug,
            BigDecimal basePrice,
            Map<String, List<String>> options,
            List<VariantConfig> variantConfigs
    ) {
        // 1. Create Parent Product
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setSlug(slug);
        req.setBasePrice(basePrice);
        req.setCategorySlug(cat.getSlug());
        req.setBrandSlug(brandSlug);
        req.setDescription("Premium quality " + name + ". Durable and authentic.");
        req.setImages(List.of(new ProductImage("https://placehold.co/400?text=" + name.replaceAll(" ", "+"), true)));

        // Map definitions (e.g., Color: [Red, Blue])
        req.setVariantOptions(options);

        // Save Parent (This triggers lineage calculation)
        Product savedProduct = productService.createOrUpdateProduct(req);

        // 2. Create Variants
        for (VariantConfig config : variantConfigs) {
            VariantRequest vReq = new VariantRequest();
            vReq.setProductId(savedProduct.getId());
            vReq.setSku(config.sku);
            vReq.setPrice(config.price);
            vReq.setStockQuantity(config.stock);
            vReq.setAttributes(config.attributes);

            // Generate variant image
            vReq.setImages(List.of(new ProductImage("https://placehold.co/400?text=Variant", true)));

            productService.saveVariant(vReq);
        }
    }

    private Category saveCat(String name, String slug, Category parent, String img) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setParent(parent);
        c.setImageUrl("https://placehold.co/400?text=" + slug);
        c.setFeatured(true);
        c.setDisplayOrder(1);

        // Manual lineage logic for seeder (Service handles this usually, but good to be explicit here)
        if (parent != null) {
            String parentLineage = parent.getLineage() == null ? "," : parent.getLineage();
            c.setLineage(parentLineage + parent.getId() + ",");
        } else {
            c.setLineage(",");
        }
        return categoryRepo.save(c);
    }

    // Simple inner class to hold variant data for the loop
    private record VariantConfig(String sku, BigDecimal price, int stock, Map<String, String> attributes) {}
}