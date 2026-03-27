package semicolon.africa.waylchub;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.dto.productDto.VariantRequest;
import semicolon.africa.waylchub.model.product.*;
import semicolon.africa.waylchub.repository.productRepository.*;
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

        System.out.println("🚀 STARTING FULL ADMIN DATA SEEDER (FASHION FOCUSED)");

        ensureSearchIndex();
        wipeDevDatabase();
        createBrands();
        createCategoryTree();
        seedProducts();

        System.out.println("✅ SEEDING COMPLETE");
    }

    // ----------------------------------------------------------------

    private void ensureSearchIndex() {
        mongoTemplate.indexOps(Product.class).ensureIndex(
                new TextIndexDefinition.TextIndexDefinitionBuilder()
                        .onField("name")
                        .onField("description")
                        .onField("brandName")
                        .named("text_search")
                        .build()
        );
    }

    private void wipeDevDatabase() {
        variantRepo.deleteAll();
        productRepo.deleteAll();
        categoryRepo.deleteAll();
        brandRepo.deleteAll();
    }

    // ----------------------------------------------------------------

    private void createBrands() {
        List<String> brands = List.of(
                "Apple", "Samsung", "Sony", "Nike", "Adidas", "Zara", "Logitech", "Dyson", "HP", "Nespresso",
                // New Fashion Brands
                "Gucci", "Levis", "H&M", "Ray-Ban", "Casio", "Puma", "ASOS"
        );

        for (String name : brands) {
            Brand brand = new Brand();
            brand.setName(name);
            brand.setSlug(name.toLowerCase().replace(" ", "-").replace("&", "and"));
            brandRepo.save(brand);
        }
    }

    // ----------------------------------------------------------------

    private Category electronics, smartphones, laptops, audio;
    private Category fashion, men, sneakers, women, accessories, watches;
    private Category home, kitchen, furniture;

    private void createCategoryTree() {
        electronics = saveCat("Electronics", "electronics", null, "https://images.unsplash.com/photo-1498049860654");
        smartphones = saveCat("Smartphones", "smartphones", electronics, "https://images.unsplash.com/photo-1511707171634");
        laptops = saveCat("Laptops", "laptops", electronics, "https://images.unsplash.com/photo-1496181133206");
        audio = saveCat("Audio", "audio", electronics, "https://images.unsplash.com/photo-1505740420928");

        // Expanded Fashion Categories
        fashion = saveCat("Fashion", "fashion", null, "https://images.unsplash.com/photo-1445205170230");
        men = saveCat("Men", "men", fashion, "https://images.unsplash.com/photo-1617137968427");
        sneakers = saveCat("Sneakers", "sneakers", men, "https://images.unsplash.com/photo-1552346154");
        women = saveCat("Women", "women", fashion, "https://images.unsplash.com/photo-1483985988355");
        accessories = saveCat("Accessories", "accessories", fashion, "https://images.unsplash.com/photo-1523779105320-d1cd346ff52b");
        watches = saveCat("Watches", "watches", accessories, "https://images.unsplash.com/photo-1524805444758-089113d48a6d");

        home = saveCat("Home", "home", null, "https://images.unsplash.com/photo-1484101403633");
        kitchen = saveCat("Kitchen", "kitchen", home, "https://images.unsplash.com/photo-1556911220");
        furniture = saveCat("Furniture", "furniture", home, "https://images.unsplash.com/photo-1555041469");
    }

    private Category saveCat(String name, String slug, Category parent, String img) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setParent(parent);
        c.setImageUrl(img);
        c.setLineage(parent != null
                ? (parent.getLineage() == null ? "," : parent.getLineage()) + parent.getId() + ","
                : ",");
        return categoryRepo.save(c);
    }

    // ----------------------------------------------------------------

    private void seedProducts() {
        // Tech (Kept for variety)
        createIphone();
        createMacbook();
        createSonyHeadphones();

        // Original Fashion
        createNikeSneakers();
        createDress();
        createIrregularTShirt();

        // New Fashion Products
        createLevisJeans();
        createGucciBelt();
        createAdidasUltraboost();
        createHandMBasicHoodie();
        createRayBanAviators();
        createCasioVintageWatch();
        createASOSEveningGown();
        createPumaTracksuit();
        createZaraWinterCoat();

        // Home / Edge cases
        createCoffeeMachine();
        createInactiveProduct();
    }

    // ----------------------------------------------------------------
    // NEW FASHION PRODUCTS
    // ----------------------------------------------------------------

    private void createLevisJeans() {
        // Standard variant matrix with CompareAtPrices (Discounts on variants)
        createProductWithVariants(
                "Levi's 501 Original Fit Jeans", "levis-501-original", men, "levis",
                new BigDecimal("45000"), null, BigDecimal.ZERO, true,
                "The blueprint for every pair of jeans in existence.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1542272604-787c3835535d", "https://images.unsplash.com/photo-1604198453847-0a6240472e34"}, null),
                List.of("fashion", "denim", "jeans", "casual"),
                Map.of("Material", "100% Cotton", "Fit", "Straight"),
                Map.of("Color", List.of("Blue", "Black"), "Waist", List.of("30", "32", "34")),
                List.of(
                        new VariantConfig("LV501-BLU-30", new BigDecimal("45000"), new BigDecimal("55000"), 12, Map.of("Color", "Blue", "Waist", "30"), true),
                        new VariantConfig("LV501-BLU-32", new BigDecimal("45000"), new BigDecimal("55000"), 20, Map.of("Color", "Blue", "Waist", "32"), true),
                        new VariantConfig("LV501-BLK-32", new BigDecimal("45000"), null, 8, Map.of("Color", "Black", "Waist", "32"), true)
                )
        );
    }

    private void createGucciBelt() {
        // Luxury item, high price, single stock variation
        createProductWithVariants(
                "Gucci GG Marmont Leather Belt", "gucci-gg-belt", accessories, "gucci",
                new BigDecimal("350000"), null, BigDecimal.ZERO, true,
                "Classic black leather belt with the iconic Double G buckle.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1553062407-98eeb64c6a62"}, null),
                List.of("fashion", "luxury", "accessories", "leather"),
                Map.of("Material", "Calfskin Leather", "Hardware", "Antiqued Brass"),
                Map.of("Size", List.of("85cm", "90cm", "95cm")),
                List.of(
                        new VariantConfig("GUC-BLT-85", new BigDecimal("350000"), null, 2, Map.of("Size", "85cm"), true),
                        new VariantConfig("GUC-BLT-90", new BigDecimal("350000"), null, 5, Map.of("Size", "90cm"), true)
                )
        );
    }

    private void createAdidasUltraboost() {
        // EDGE CASE: Out of stock heavily
        createProductWithVariants(
                "Adidas Ultraboost 1.0", "adidas-ultraboost-1", sneakers, "adidas",
                new BigDecimal("120000"), null, BigDecimal.ZERO, true,
                "High-performance running shoes with responsive cushioning.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1518002171953-a080ee817e1f"}, null),
                List.of("sneakers", "running", "adidas", "sports"),
                Map.of("Sole", "Rubber", "Upper", "Primeknit"),
                Map.of("Size", List.of("40", "41", "42")),
                List.of(
                        new VariantConfig("UB-40", new BigDecimal("120000"), null, 0, Map.of("Size", "40"), true), // OOS
                        new VariantConfig("UB-41", new BigDecimal("120000"), null, 0, Map.of("Size", "41"), true), // OOS
                        new VariantConfig("UB-42", new BigDecimal("120000"), null, 3, Map.of("Size", "42"), true)
                )
        );
    }

    private void createHandMBasicHoodie() {
        // 20% parent discount
        createProductWithVariants(
                "H&M Relaxed Fit Hoodie", "hm-relaxed-hoodie", men, "handm",
                new BigDecimal("25000"), null, new BigDecimal("20"), true,
                "Soft, comfortable cotton-blend hoodie for everyday wear.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1556821840-3a63f95609a7"}, null),
                List.of("fashion", "casual", "hoodie", "sale"),
                Map.of("Material", "80% Cotton, 20% Polyester"),
                Map.of("Color", List.of("Grey", "Black"), "Size", List.of("M", "L", "XL")),
                List.of(
                        new VariantConfig("HM-HD-GRY-M", new BigDecimal("25000"), null, 15, Map.of("Color", "Grey", "Size", "M"), true),
                        new VariantConfig("HM-HD-GRY-L", new BigDecimal("25000"), null, 25, Map.of("Color", "Grey", "Size", "L"), true),
                        new VariantConfig("HM-HD-BLK-XL", new BigDecimal("25000"), null, 10, Map.of("Color", "Black", "Size", "XL"), true)
                )
        );
    }

    private void createRayBanAviators() {
        // Simple product, no variants
        createProductNoVariants(
                "Ray-Ban Classic Aviator Sunglasses", "rayban-aviator-classic", accessories, "ray-ban",
                new BigDecimal("115000"), BigDecimal.ZERO, true,
                "Currently one of the most iconic sunglass models in the world.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1511499767150-a48a237f0083"}, null),
                List.of("fashion", "accessories", "sunglasses", "summer")
        );
    }

    private void createCasioVintageWatch() {
        // EDGE CASE: Completely out of stock parent product
        createProductWithVariants(
                "Casio Vintage Digital Watch", "casio-vintage-gold", watches, "casio",
                new BigDecimal("45000"), null, BigDecimal.ZERO, true,
                "Retro gold-tone digital watch.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1524805444758-089113d48a6d"}, null),
                List.of("fashion", "watch", "accessories", "vintage"),
                Map.of("Movement", "Digital", "Water Resistance", "30m"),
                Map.of("Color", List.of("Gold", "Silver")),
                List.of(
                        new VariantConfig("CAS-V-GLD", new BigDecimal("45000"), null, 0, Map.of("Color", "Gold"), true),
                        new VariantConfig("CAS-V-SLV", new BigDecimal("40000"), null, 0, Map.of("Color", "Silver"), true)
                )
        );
    }

    private void createASOSEveningGown() {
        // EDGE CASE: Irregular variant matrix
        createProductWithVariants(
                "ASOS Design Satin Maxi Dress", "asos-satin-maxi", women, "asos",
                new BigDecimal("65000"), null, BigDecimal.ZERO, true,
                "Stunning satin maxi dress with cowl neck.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d"}, null),
                List.of("fashion", "evening", "dress", "party"),
                Map.of("Material", "100% Polyester", "Care", "Dry clean only"),
                Map.of("Color", List.of("Emerald", "Navy"), "Size", List.of("UK 8", "UK 10", "UK 12")),
                List.of(
                        new VariantConfig("AS-EM-8", new BigDecimal("65000"), null, 5, Map.of("Color", "Emerald", "Size", "UK 8"), true),
                        new VariantConfig("AS-EM-12", new BigDecimal("65000"), null, 2, Map.of("Color", "Emerald", "Size", "UK 12"), true), // Skipped size 10
                        new VariantConfig("AS-NV-10", new BigDecimal("65000"), null, 4, Map.of("Color", "Navy", "Size", "UK 10"), true)
                )
        );
    }

    private void createPumaTracksuit() {
        // EDGE CASE: Product is globally inactive
        createProductWithVariants(
                "Puma T7 Tracksuit Set", "puma-t7-tracksuit", men, "puma",
                new BigDecimal("85000"), null, BigDecimal.ZERO, false, // INACTIVE
                "Classic T7 track jacket and pants combo. Dropping next season.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1515886657613-9f3515b0c78f"}, null),
                List.of("fashion", "sportswear", "tracksuit", "puma"),
                Map.of("Fit", "Regular", "Material", "Polyester"),
                Map.of("Size", List.of("M", "L", "XL")),
                List.of(
                        new VariantConfig("PM-TS-M", new BigDecimal("85000"), null, 10, Map.of("Size", "M"), true),
                        new VariantConfig("PM-TS-L", new BigDecimal("85000"), null, 10, Map.of("Size", "L"), true)
                )
        );
    }

    private void createZaraWinterCoat() {
        // Discounted winter clearance
        createProductWithVariants(
                "Zara Wool Blend Tailored Coat", "zara-wool-coat", women, "zara",
                new BigDecimal("120000"), new BigDecimal("180000"), new BigDecimal("33"), true,
                "Longline tailored coat perfect for winter layering.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1539533113208-f6df8cc8b543"}, null),
                List.of("fashion", "outerwear", "winter", "coat", "clearance"),
                Map.of("Material", "70% Wool, 30% Polyamide"),
                Map.of("Size", List.of("S", "M")),
                List.of(
                        new VariantConfig("ZR-CT-S", new BigDecimal("120000"), new BigDecimal("180000"), 4, Map.of("Size", "S"), true),
                        new VariantConfig("ZR-CT-M", new BigDecimal("120000"), new BigDecimal("180000"), 1, Map.of("Size", "M"), true)
                )
        );
    }

    // ----------------------------------------------------------------
    // ORIGINAL PRODUCTS (Preserved for system stability)
    // ----------------------------------------------------------------

    private void createIphone() {
        createProductWithVariants(
                "Apple iPhone 15 Pro", "iphone-15-pro", smartphones, "apple",
                new BigDecimal("1500000"), null, BigDecimal.ZERO, true,
                "Forged in titanium with the powerful A17 Pro chip.",
                mediaGallery(
                        new String[]{"https://images.unsplash.com/photo-1695048133142", "https://images.unsplash.com/photo-1591337676887"},
                        new String[]{"https://www.youtube.com/embed/xqyUdNxWazA"}
                ),
                List.of("smartphone", "apple", "premium"),
                Map.of("Chip", "A17 Pro", "Screen", "6.1-inch OLED"),
                Map.of("Color", List.of("Black", "Blue"), "Storage", List.of("128GB", "256GB", "1TB")),
                List.of(
                        new VariantConfig("IP15-BLK-128", new BigDecimal("1500000"), null, 10, Map.of("Color", "Black", "Storage", "128GB"), true),
                        new VariantConfig("IP15-BLU-256", new BigDecimal("1700000"), null, 5, Map.of("Color", "Blue", "Storage", "256GB"), true),
                        new VariantConfig("IP15-BLK-1TB", new BigDecimal("2200000"), null, 2, Map.of("Color", "Black", "Storage", "1TB"), true)
                )
        );
    }

    private void createMacbook() {
        createProductWithVariants(
                "MacBook Pro 16 M3 Max", "macbook-pro-m3-max", laptops, "apple",
                new BigDecimal("4500000"), new BigDecimal("5000000"), BigDecimal.ZERO, true,
                "The most powerful MacBook ever built.",
                mediaGallery(
                        new String[]{"https://images.unsplash.com/photo-1517336714731"},
                        new String[]{"https://www.youtube.com/embed/ctkW3V0Mh-k"}
                ),
                List.of("laptop", "professional"),
                Map.of("Processor", "M3 Max", "Display", "Liquid Retina XDR"),
                Map.of("Specs", List.of("36GB / 1TB", "128GB / 4TB")),
                List.of(
                        new VariantConfig("MBP-36", new BigDecimal("4500000"), null, 5, Map.of("Specs", "36GB / 1TB"), true),
                        new VariantConfig("MBP-128", new BigDecimal("7200000"), new BigDecimal("8000000"), 2, Map.of("Specs", "128GB / 4TB"), true)
                )
        );
    }

    private void createSonyHeadphones() {
        createProductWithVariants(
                "Sony WH-1000XM5", "sony-xm5", audio, "sony",
                new BigDecimal("450000"), null, new BigDecimal("10"), true,
                "Industry leading noise cancelling headphones.",
                mediaGallery(
                        new String[]{"https://images.unsplash.com/photo-1618366712010"},
                        new String[]{"https://www.youtube.com/embed/6v7bJ8l8q9Y"}
                ),
                List.of("audio", "noise-cancelling", "sale"),
                Map.of("Battery", "30 hours"),
                Map.of("Color", List.of("Black", "Silver")),
                List.of(
                        new VariantConfig("XM5-BLK", new BigDecimal("450000"), null, 20, Map.of("Color", "Black"), true)
                )
        );
    }

    private void createNikeSneakers() {
        createProductWithVariants(
                "Nike Air Jordan 1 Retro", "jordan-1-retro", sneakers, "nike",
                new BigDecimal("250000"), null, BigDecimal.ZERO, true,
                "Classic Jordan silhouette.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1552346154"}, null),
                List.of("sneakers", "nike"),
                Map.of("Material", "Leather"),
                Map.of("Size", List.of("42", "43", "44")),
                List.of(
                        new VariantConfig("AJ1-42", new BigDecimal("250000"), null, 4, Map.of("Size", "42"), true),
                        new VariantConfig("AJ1-44", new BigDecimal("250000"), null, 0, Map.of("Size", "44"), true)
                )
        );
    }

    private void createIrregularTShirt() {
        createProductWithVariants(
                "Zara Essential Basic Tee", "zara-basic-tee", men, "zara",
                new BigDecimal("15000"), null, BigDecimal.ZERO, true,
                "A comfortable, everyday cotton t-shirt.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1521572163474"}, null),
                List.of("fashion", "casual", "tshirt"),
                Map.of("Material", "100% Cotton"),
                Map.of("Color", List.of("Red", "Blue"), "Size", List.of("Small", "Medium", "Large")),
                List.of(
                        new VariantConfig("TEE-RED-S", new BigDecimal("15000"), null, 10, Map.of("Color", "Red", "Size", "Small"), true),
                        new VariantConfig("TEE-RED-M", new BigDecimal("15000"), null, 15, Map.of("Color", "Red", "Size", "Medium"), true),
                        new VariantConfig("TEE-BLU-M", new BigDecimal("15000"), null, 5, Map.of("Color", "Blue", "Size", "Medium"), true),
                        new VariantConfig("TEE-BLU-L", new BigDecimal("15000"), null, 8, Map.of("Color", "Blue", "Size", "Large"), true)
                )
        );
    }

    private void createInactiveProduct() {
        createProductNoVariants(
                "Dyson V15 Detect (Coming Soon)", "dyson-v15", home, "dyson",
                new BigDecimal("850000"), BigDecimal.ZERO, false,
                "Next generation vacuum cleaner. Launching next month.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1558317374-067fb5f30001"}, null),
                List.of("vacuum", "home", "cleaning")
        );
    }

    private void createDress() {
        createProductNoVariants(
                "Zara Floral Summer Dress", "zara-summer-dress", women, "zara",
                new BigDecimal("45000"), BigDecimal.ZERO, true,
                "Elegant summer dress perfect for warm climates.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1496747611176"}, null),
                List.of("fashion", "summer")
        );
    }

    private void createCoffeeMachine() {
        createProductNoVariants(
                "Nespresso Vertuo Machine", "nespresso-vertuo", kitchen, "nespresso",
                new BigDecimal("180000"), BigDecimal.ZERO, true,
                "Premium coffee machine with capsule technology.",
                mediaGallery(new String[]{"https://images.unsplash.com/photo-1626079958742"}, new String[]{"https://www.youtube.com/embed/5L3h0Fh0h7Q"}),
                List.of("coffee", "kitchen")
        );
    }

    // ----------------------------------------------------------------
    // Core Helpers
    // ----------------------------------------------------------------

    private void createProductNoVariants(
            String name, String slug, Category cat, String brandSlug,
            BigDecimal price, BigDecimal discount, boolean isActive,
            String description, List<ProductImage> media, List<String> tags
    ) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setSlug(slug);
        req.setBasePrice(price);
        req.setCategorySlug(cat.getSlug());
        req.setBrandSlug(brandSlug);
        req.setDescription(description);
        req.setImages(media);
        req.setTags(tags);
        req.setDiscount(discount);
        req.setIsActive(isActive);

        productService.createOrUpdateProduct(req);
    }

    private void createProductWithVariants(
            String name, String slug, Category cat, String brandSlug,
            BigDecimal basePrice, BigDecimal compareAt, BigDecimal discount, boolean isActive,
            String description, List<ProductImage> media, List<String> tags,
            Map<String, String> specs, Map<String, List<String>> options,
            List<VariantConfig> configs
    ) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setSlug(slug);
        req.setBasePrice(basePrice);
        req.setCompareAtPrice(compareAt);
        req.setCategorySlug(cat.getSlug());
        req.setBrandSlug(brandSlug);
        req.setDescription(description);
        req.setImages(media);
        req.setTags(tags);
        req.setSpecifications(specs);
        req.setVariantOptions(options);
        req.setDiscount(discount);
        req.setIsActive(isActive);

        Product product = productService.createOrUpdateProduct(req);

        for (VariantConfig c : configs) {
            VariantRequest v = new VariantRequest();
            v.setProductId(product.getId());
            v.setSku(c.sku);
            v.setPrice(c.price);
            v.setCompareAtPrice(c.compareAtPrice);
            v.setStockQuantity(c.stock);
            v.setAttributes(c.attributes);
            v.setImages(media);
            v.setIsActive(c.isActive);

            productService.saveVariant(v);
        }
    }

    private List<ProductImage> mediaGallery(String[] imageUrls, String[] videoUrls) {
        List<ProductImage> gallery = new ArrayList<>();
        if (imageUrls != null) {
            for (int i = 0; i < imageUrls.length; i++) {
                gallery.add(new ProductImage(imageUrls[i], i == 0, ProductImage.MediaType.IMAGE));
            }
        }
        if (videoUrls != null) {
            for (String vUrl : videoUrls) {
                gallery.add(new ProductImage(vUrl, false, ProductImage.MediaType.VIDEO));
            }
        }
        return gallery;
    }

    private record VariantConfig(
            String sku, BigDecimal price, BigDecimal compareAtPrice,
            int stock, Map<String, String> attributes, boolean isActive
    ) {}
}