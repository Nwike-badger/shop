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

        System.out.println("🚀 STARTING FULL ADMIN DATA SEEDER");

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
                "Apple", "Samsung", "Sony", "Nike", "Adidas",
                "Zara", "Logitech", "Dyson", "Felicity", "HP", "Dell", "Nespresso"
        );

        for (String name : brands) {
            Brand brand = new Brand();
            brand.setName(name);
            brand.setSlug(name.toLowerCase().replace(" ", "-"));
            brandRepo.save(brand);
        }
    }

    // ----------------------------------------------------------------

    private Category electronics, smartphones, laptops, audio;
    private Category fashion, men, sneakers, women;
    private Category home, kitchen, furniture;

    private void createCategoryTree() {
        electronics = saveCat("Electronics", "electronics", null, "https://images.unsplash.com/photo-1498049860654");
        smartphones = saveCat("Smartphones", "smartphones", electronics, "https://images.unsplash.com/photo-1511707171634");
        laptops = saveCat("Laptops", "laptops", electronics, "https://images.unsplash.com/photo-1496181133206");
        audio = saveCat("Audio", "audio", electronics, "https://images.unsplash.com/photo-1505740420928");

        fashion = saveCat("Fashion", "fashion", null, "https://images.unsplash.com/photo-1445205170230");
        men = saveCat("Men", "men", fashion, "https://images.unsplash.com/photo-1617137968427");
        sneakers = saveCat("Sneakers", "sneakers", men, "https://images.unsplash.com/photo-1552346154");
        women = saveCat("Women", "women", fashion, "https://images.unsplash.com/photo-1483985988355");

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
        createIphone();
        createMacbook();
        createSonyHeadphones();
        createNikeSneakers();
        createDress();
        createCoffeeMachine();
        createIrregularTShirt();
        createInactiveProduct();
    }

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
        // EDGE CASE: 10% Discounted Product at the Parent Level
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
        // EDGE CASE: Zero Stock Variant
        createProductWithVariants(
                "Nike Air Jordan 1 Retro", "jordan-1-retro", sneakers, "nike",
                new BigDecimal("250000"), null, BigDecimal.ZERO, true,
                "Classic Jordan silhouette.",
                mediaGallery(
                        new String[]{"https://images.unsplash.com/photo-1552346154"}, null
                ),
                List.of("sneakers", "nike"),
                Map.of("Material", "Leather"),
                Map.of("Size", List.of("42", "43", "44")),
                List.of(
                        new VariantConfig("AJ1-42", new BigDecimal("250000"), null, 4, Map.of("Size", "42"), true),
                        new VariantConfig("AJ1-44", new BigDecimal("250000"), null, 0, Map.of("Size", "44"), true) // OUT OF STOCK
                )
        );
    }

    private void createIrregularTShirt() {
        // EDGE CASE: Irregular variant combinations (No 'Blue - Small')
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
        // EDGE CASE: Inactive product (should not show up in regular customer searches)
        createProductNoVariants(
                "Dyson V15 Detect (Coming Soon)", "dyson-v15", home, "dyson",
                new BigDecimal("850000"), BigDecimal.ZERO, false, // isActive = false
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