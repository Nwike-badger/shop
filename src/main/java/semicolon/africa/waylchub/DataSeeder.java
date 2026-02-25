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

    @Override
    public void run(String... args) {
        System.out.println("💎 STARTING PREMIUM DATA SEEDER...");

        // 1. Setup Search Index
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
            System.out.println("⚠️ Index warning: " + e.getMessage());
        }

        // 2. Wipe Clean (Dev Mode Only)
        variantRepo.deleteAll();
        productRepo.deleteAll();
        categoryRepo.deleteAll();
        brandRepo.deleteAll();

        // 3. Create Brands
        createBrands();

        // 4. Create High-Quality Catalogue
        createPremiumCatalogue();

        System.out.println("✅ PREMIUM SEEDING COMPLETE! Ready for UI Polish.");
    }

    private void createBrands() {
        List<String> brands = List.of("Apple", "Samsung", "Sony", "Nike", "Adidas", "Zara", "Dyson", "Nespresso", "Herman Miller", "Logitech");
        for (String b : brands) {
            Brand brand = new Brand();
            brand.setName(b);
            brand.setSlug(b.toLowerCase().replace(" ", "-"));
            brandRepo.save(brand);
        }
    }

    private void createPremiumCatalogue() {
        // --- 1. ELECTRONICS TREE ---
        Category electronics = saveCat("Electronics", "electronics", null, "https://images.unsplash.com/photo-1498049860654-af5a11528db3?w=600&q=80");
        Category phones = saveCat("Smartphones", "smartphones", electronics, "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=600");
        Category audio = saveCat("Audio & Sound", "audio", electronics, "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600");
        Category laptops = saveCat("Laptops & Computers", "laptops", electronics, "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=600");

        // --- 2. FASHION TREE ---
        Category fashion = saveCat("Fashion", "fashion", null, "https://images.unsplash.com/photo-1445205170230-05328324f375?w=600");
        Category men = saveCat("Men's Wear", "men", fashion, "https://images.unsplash.com/photo-1617137968427-85924c800a22?w=600");
        Category sneakers = saveCat("Sneakers", "sneakers", men, "https://images.unsplash.com/photo-1552346154-21d32810aba3?w=600");
        Category women = saveCat("Women's Wear", "women", fashion, "https://images.unsplash.com/photo-1483985988355-763728e1935b?w=600");

        // --- 3. HOME TREE ---
        Category home = saveCat("Home & Living", "home", null, "https://images.unsplash.com/photo-1484101403633-562f891dc89a?w=600");
        Category kitchen = saveCat("Kitchen Appliances", "kitchen", home, "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?w=600");
        Category furniture = saveCat("Furniture", "furniture", home, "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=600");


        // --- 💎 PRODUCTS (10-12 High Quality Items) ---

        // 1. iPhone 15 Pro Max
        createProductWithVariants(
                "iPhone 15 Pro Max", "iphone-15-pro-max", phones, "apple",
                new BigDecimal("1850000"),
                "Forged in titanium. The most powerful iPhone ever featuring the A17 Pro chip and a customizable Action button.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=800&q=90", true),
                        new ProductImage("https://images.unsplash.com/photo-1696446701796-da61225697cc?w=800&q=90", false)
                ),
                Map.of("Color", List.of("Natural Titanium", "Black Titanium"), "Storage", List.of("256GB", "512GB")),
                List.of(
                        new VariantConfig("IP15-NAT-256", new BigDecimal("1850000"), 5, Map.of("Color", "Natural Titanium", "Storage", "256GB")),
                        new VariantConfig("IP15-BLK-512", new BigDecimal("2100000"), 3, Map.of("Color", "Black Titanium", "Storage", "512GB"))
                )
        );

        // 2. Samsung S24 Ultra
        createProductWithVariants(
                "Samsung Galaxy S24 Ultra", "samsung-s24-ultra", phones, "samsung",
                new BigDecimal("1750000"),
                "Galaxy AI is here. Welcome to the era of mobile AI. With extreme zoom and S-Pen included.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1610945415295-d9bbf067e59c?w=800&q=90", true), // Placeholder for S24 logic
                        new ProductImage("https://images.unsplash.com/photo-1706606991536-e3250b731057?w=800&q=90", false)
                ),
                Map.of("Color", List.of("Titanium Gray", "Violet")),
                List.of(
                        new VariantConfig("S24U-GRY", new BigDecimal("1750000"), 10, Map.of("Color", "Titanium Gray"))
                )
        );

        // 3. Sony Headphones (Low Stock Test)
        createProductWithVariants(
                "Sony WH-1000XM5 Wireless Headphones", "sony-xm5", audio, "sony",
                new BigDecimal("450000"),
                "Industry-leading noise cancellation with two processors controlling eight microphones.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?w=800&q=90", true),
                        new ProductImage("https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=800&q=90", false)
                ),
                Map.of("Color", List.of("Black", "Silver")),
                List.of(
                        new VariantConfig("XM5-BLK", new BigDecimal("450000"), 2, Map.of("Color", "Black")) // Only 2 left!
                )
        );

        // 4. MacBook Pro M3
        createProductWithVariants(
                "MacBook Pro 14 M3 Max", "macbook-pro-m3", laptops, "apple",
                new BigDecimal("3500000"),
                "Mind-blowing. Head-turning. The M3 Max chip brings serious speed and capability.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1517336714731-489689fd1ca4?w=800&q=90", true),
                        new ProductImage("https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?w=800&q=90", false)
                ),
                Map.of("RAM", List.of("36GB")),
                List.of(
                        new VariantConfig("MBP-M3-36", new BigDecimal("3500000"), 5, Map.of("RAM", "36GB"))
                )
        );

        // 5. Nike Air Jordan
        createProductWithVariants(
                "Air Jordan 1 Retro High OG", "jordan-1-retro", sneakers, "nike",
                new BigDecimal("250000"),
                "Familiar but always fresh, the Air Jordan 1 Retro High OG is remastered for today's sneakerhead culture.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1552346154-21d32810aba3?w=800&q=90", true),
                        new ProductImage("https://images.unsplash.com/photo-1608231387042-66d1773070a5?w=800&q=90", false)
                ),
                Map.of("Size", List.of("42", "43", "44", "45")),
                List.of(
                        new VariantConfig("AJ1-42", new BigDecimal("250000"), 4, Map.of("Size", "42")),
                        new VariantConfig("AJ1-43", new BigDecimal("250000"), 6, Map.of("Size", "43")),
                        new VariantConfig("AJ1-44", new BigDecimal("250000"), 0, Map.of("Size", "44")) // Out of Stock Test
                )
        );

        // 6. Adidas Yeezy
        createProductWithVariants(
                "Adidas Yeezy Boost 350 V2", "yeezy-350-v2", sneakers, "adidas",
                new BigDecimal("320000"),
                "The Yeezy Boost 350 V2 features an upper composed of re-engineered Primeknit.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1620799140408-ed5341cd2431?w=800&q=90", true)
                ),
                Map.of("Size", List.of("40", "41")),
                List.of(
                        new VariantConfig("YZY-40", new BigDecimal("320000"), 10, Map.of("Size", "40"))
                )
        );

        // 7. Summer Dress
        createProductWithVariants(
                "Zara Floral Summer Maxi Dress", "zara-floral-dress", women, "zara",
                new BigDecimal("45000"),
                "Elegant and breezy, perfect for the Nigerian weather. Made from 100% organic cotton.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=800&q=90", true),
                        new ProductImage("https://images.unsplash.com/photo-1496747611176-843222e1e57c?w=800&q=90", false)
                ),
                Map.of("Size", List.of("S", "M", "L")),
                List.of(
                        new VariantConfig("DRS-S", new BigDecimal("45000"), 15, Map.of("Size", "S"))
                )
        );

        // 8. Nespresso Machine
        createProductWithVariants(
                "Nespresso Vertuo Next", "nespresso-vertuo", kitchen, "nespresso",
                new BigDecimal("180000"),
                "Nespresso Vertuo Next Coffee and Espresso Machine by De'Longhi.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1626079958742-999333947b99?w=800&q=90", true)
                ),
                Map.of("Color", List.of("Black", "Red")),
                List.of(
                        new VariantConfig("NES-BLK", new BigDecimal("180000"), 8, Map.of("Color", "Black"))
                )
        );

        // 9. Herman Miller Chair
        createProductWithVariants(
                "Herman Miller Aeron Chair", "aeron-chair", furniture, "herman-miller",
                new BigDecimal("1200000"),
                "The benchmark for ergonomic seating. Remastered for the modern office.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1505843490538-5133c6c7d0e1?w=800&q=90", true)
                ),
                Map.of("Size", List.of("B (Medium)")),
                List.of(
                        new VariantConfig("AERON-B", new BigDecimal("1200000"), 3, Map.of("Size", "B (Medium)"))
                )
        );

        // 10. Dyson Vacuum
        createProductWithVariants(
                "Dyson V15 Detect", "dyson-v15", home, "dyson",
                new BigDecimal("750000"),
                "Dyson's most powerful, intelligent cordless vacuum. Laser reveals microscopic dust.",
                List.of(
                        new ProductImage("https://images.unsplash.com/photo-1558317374-a3593912094c?w=800&q=90", true)
                ),
                null,
                List.of(
                        new VariantConfig("DYS-V15", new BigDecimal("750000"), 6, Map.of())
                )
        );
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

    private void createProductWithVariants(String name, String slug, Category cat, String brandSlug, BigDecimal basePrice, String description, List<ProductImage> images, Map<String, List<String>> options, List<VariantConfig> configs) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setSlug(slug);
        req.setBasePrice(basePrice);
        req.setCategorySlug(cat.getSlug());
        req.setBrandSlug(brandSlug);
        req.setDescription(description);
        req.setImages(images);
        req.setVariantOptions(options); // Can be null
        req.setDiscount(BigDecimal.ZERO); // Default no discount

        Product p = productService.createOrUpdateProduct(req);

        for (VariantConfig c : configs) {
            VariantRequest v = new VariantRequest();
            v.setProductId(p.getId());
            v.setSku(c.sku);
            v.setPrice(c.price);
            v.setStockQuantity(c.stock);
            v.setAttributes(c.attributes);
            v.setImages(images); // Share parent images for now
            productService.saveVariant(v);
        }
    }

    private record VariantConfig(String sku, BigDecimal price, int stock, Map<String, String> attributes) {}
}