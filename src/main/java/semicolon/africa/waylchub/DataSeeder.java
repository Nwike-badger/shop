//package semicolon.africa.waylchub;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//import semicolon.africa.waylchub.dto.productDto.ProductRequest;
//import semicolon.africa.waylchub.dto.productDto.VariantRequest;
//import semicolon.africa.waylchub.model.product.*;
//import semicolon.africa.waylchub.repository.productRepository.*;
//import semicolon.africa.waylchub.service.productService.ProductService;
//
//import java.math.BigDecimal;
//import java.util.*;
//
//@Component
//@Profile("!prod")
//@RequiredArgsConstructor
//public class DataSeeder implements CommandLineRunner {
//
//    private final CategoryRepository categoryRepo;
//    private final BrandRepository brandRepo;
//    private final ProductRepository productRepo;
//    private final ProductVariantRepository variantRepo;
//    private final ProductService productService;
//
//    @Override
//    public void run(String... args) {
//        System.out.println("🚀 STARTING FULL FASHION DATA SEEDER");
//        wipeDevDatabase();
//        createBrands();
//        createCategoryTree();
//        seedProducts();
//        System.out.println("✅ SEEDING COMPLETE");
//    }
//
//    // ================================================================
//    // WIPE
//    // ================================================================
//
//    private void wipeDevDatabase() {
//        variantRepo.deleteAll();
//        productRepo.deleteAll();
//        categoryRepo.deleteAll();
//        brandRepo.deleteAll();
//        System.out.println("🗑️  Database wiped.");
//    }
//
//    // ================================================================
//    // BRANDS
//    // ================================================================
//
//    private void createBrands() {
//        List<String> brands = List.of(
//                "Nike", "Adidas", "Puma", "New Balance",
//                "Gucci", "Zara", "H&M", "Levi's",
//                "Ray-Ban", "Casio", "ASOS", "Lacoste",
//                "Tommy Hilfiger", "Calvin Klein", "Michael Kors",
//                "Fossil", "Ankara Republic", "BedLuxe", "FabricHouse"
//        );
//        for (String name : brands) {
//            Brand brand = new Brand();
//            brand.setName(name);
//            brand.setSlug(
//                    name.toLowerCase()
//                            .replace(" ", "-")
//                            .replace("'", "")
//                            .replace("&", "and")
//            );
//            brandRepo.save(brand);
//        }
//        System.out.println("✅ Brands created: " + brands.size());
//    }
//
//    // ================================================================
//    // CATEGORIES
//    // ================================================================
//
//    // ── Root ─────────────────────────────────────────────────────────
//    private Category fashion;
//
//    // ── Men ──────────────────────────────────────────────────────────
//    private Category men, menTees, menJeans, menSneakers, menBlazer, menSport;
//
//    // ── Women ────────────────────────────────────────────────────────
//    private Category women, womenDresses, womenTops, womenSkirts, womenOuter, womenActivewear;
//
//    // ── Accessories ──────────────────────────────────────────────────
//    private Category accessories, watches, sunglasses, bags, belts, hats;
//
//    // ── Footwear ─────────────────────────────────────────────────────
//    private Category footwear, womenHeels, boots, sandals;
//
//    // ── African & Native Wear ─────────────────────────────────────────
//    private Category africanWear, agbada, ankara, kaftan, iroAndBuba;
//
//    // ── Fabrics ───────────────────────────────────────────────────────
//    private Category fabrics, ankaraFabric, laceFabric, asoOke;
//
//    // ── Home Textiles ─────────────────────────────────────────────────
//    private Category homeTextiles, bedsheets, duvetSets, pillows;
//
//    private void createCategoryTree() {
//
//        // ── Root ──────────────────────────────────────────────────────
//        fashion = saveCat("Fashion", "fashion", null,
//                "https://images.unsplash.com/photo-1445205170230-053b83016050?w=800&q=80");
//
//        // ── Men ───────────────────────────────────────────────────────
//        men = saveCat("Men", "men", fashion,
//                "https://images.unsplash.com/photo-1617137968427-85924c800a22?w=800&q=80");
//
//        menTees = saveCat("T-Shirts & Tops", "men-tees", men,
//                "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&q=80");
//
//        menJeans = saveCat("Jeans & Trousers", "men-jeans", men,
//                "https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&q=80");
//
//        menSneakers = saveCat("Sneakers", "men-sneakers", men,
//                "https://images.unsplash.com/photo-1552346154-21d32810aba3?w=800&q=80");
//
//        menBlazer = saveCat("Blazers & Suits", "men-blazers", men,
//                "https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=800&q=80");
//
//        menSport = saveCat("Sportswear", "men-sportswear", men,
//                "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&q=80");
//
//        // ── Women ─────────────────────────────────────────────────────
//        women = saveCat("Women", "women", fashion,
//                "https://images.unsplash.com/photo-1483985988355-763728e1935b?w=800&q=80");
//
//        womenDresses = saveCat("Dresses", "women-dresses", women,
//                "https://images.unsplash.com/photo-1496747611176-843222e1e57c?w=800&q=80");
//
//        womenTops = saveCat("Tops & Blouses", "women-tops", women,
//                "https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=800&q=80");
//
//        womenSkirts = saveCat("Skirts", "women-skirts", women,
//                "https://images.unsplash.com/photo-1583496661160-fb5218b5f2b9?w=800&q=80");
//
//        womenOuter = saveCat("Jackets & Coats", "women-outerwear", women,
//                "https://images.unsplash.com/photo-1539533113208-f6df8cc8b543?w=800&q=80");
//
//        womenActivewear = saveCat("Activewear", "women-activewear", women,
//                "https://images.unsplash.com/photo-1538805060514-97d9cc17730c?w=800&q=80");
//
//        // ── Accessories ───────────────────────────────────────────────
//        accessories = saveCat("Accessories", "accessories", fashion,
//                "https://images.unsplash.com/photo-1523779105320-d1cd346ff52b?w=800&q=80");
//
//        watches = saveCat("Watches", "watches", accessories,
//                "https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=800&q=80");
//
//        sunglasses = saveCat("Sunglasses", "sunglasses", accessories,
//                "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=800&q=80");
//
//        bags = saveCat("Bags & Purses", "bags", accessories,
//                "https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=800&q=80");
//
//        belts = saveCat("Belts", "belts", accessories,
//                "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=800&q=80");
//
//        hats = saveCat("Hats & Caps", "hats", accessories,
//                "https://images.unsplash.com/photo-1521369909029-2afed882baee?w=800&q=80");
//
//        // ── Footwear ──────────────────────────────────────────────────
//        footwear = saveCat("Footwear", "footwear", fashion,
//                "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&q=80");
//
//        womenHeels = saveCat("Heels & Pumps", "women-heels", footwear,
//                "https://images.unsplash.com/photo-1543163521-1bf539c55dd2?w=800&q=80");
//
//        boots = saveCat("Boots", "boots", footwear,
//                "https://images.unsplash.com/photo-1520639888713-7851133b1ed0?w=800&q=80");
//
//        sandals = saveCat("Sandals & Slides", "sandals", footwear,
//                "https://images.unsplash.com/photo-1603487742131-4160ec999306?w=800&q=80");
//
//        // ── African & Native Wear ──────────────────────────────────────
//        africanWear = saveCat("African & Native Wear", "african-wear", fashion,
//                "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80");
//
//        agbada = saveCat("Agbada & Senators", "agbada", africanWear,
//                "https://images.unsplash.com/photo-1529156069898-49953e39b3ac?w=800&q=80");
//
//        ankara = saveCat("Ankara Styles", "ankara-styles", africanWear,
//                "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80");
//
//        kaftan = saveCat("Kaftans", "kaftans", africanWear,
//                "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?w=800&q=80");
//
//        iroAndBuba = saveCat("Iro & Buba", "iro-buba", africanWear,
//                "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80");
//
//        // ── Fabrics ────────────────────────────────────────────────────
//        fabrics = saveCat("Fabrics", "fabrics", fashion,
//                "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80");
//
//        ankaraFabric = saveCat("Ankara Fabric", "ankara-fabric", fabrics,
//                "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?w=800&q=80");
//
//        laceFabric = saveCat("Lace Fabric", "lace-fabric", fabrics,
//                "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80");
//
//        asoOke = saveCat("Aso-Oke", "aso-oke", fabrics,
//                "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?w=800&q=80");
//
//        // ── Home Textiles ──────────────────────────────────────────────
//        homeTextiles = saveCat("Home Textiles", "home-textiles", fashion,
//                "https://images.unsplash.com/photo-1631729371254-42c2892f0e6e?w=800&q=80");
//
//        bedsheets = saveCat("Bedsheets", "bedsheets", homeTextiles,
//                "https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=800&q=80");
//
//        duvetSets = saveCat("Duvet Sets", "duvet-sets", homeTextiles,
//                "https://images.unsplash.com/photo-1631729371254-42c2892f0e6e?w=800&q=80");
//
//        pillows = saveCat("Pillows & Cushions", "pillows", homeTextiles,
//                "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=800&q=80");
//
//        System.out.println("✅ Category tree created.");
//    }
//
//    // ================================================================
//    // SEED PRODUCTS
//    // ================================================================
//
//    private void seedProducts() {
//        seedMenProducts();
//        seedWomenProducts();
//        seedAccessoryProducts();
//        seedFootwearProducts();
//        seedAfricanWearProducts();
//        seedFabricProducts();
//        seedHomeTextileProducts();
//        System.out.println("✅ All products seeded.");
//    }
//
//    // ================================================================
//    // MEN'S PRODUCTS (10 products)
//    // ================================================================
//
//    private void seedMenProducts() {
//
//        // 1. Nike Air Jordan 1 Retro
//        createProductWithVariants(
//                "Nike Air Jordan 1 Retro High OG",
//                "jordan-1-retro-high",
//                menSneakers, "nike",
//                new BigDecimal("250000"), null, BigDecimal.ZERO, true,
//                "The shoe that started it all. Classic silhouette with premium leather and iconic colourways that never go out of style.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1552346154-21d32810aba3?w=800&q=80",
//                        "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&q=80"
//                }, null),
//                List.of("sneakers", "nike", "jordan", "limited"),
//                Map.of("Material", "Full-Grain Leather", "Sole", "Vulcanised Rubber", "Closure", "Lace-Up"),
//                Map.of("Colour", List.of("Chicago Red/White", "Shadow Black"), "Size", List.of("40", "41", "42", "43", "44", "45")),
//                List.of(
//                        new VariantConfig("AJ1-RED-40", new BigDecimal("250000"), null, 5, Map.of("Colour", "Chicago Red/White", "Size", "40"), true),
//                        new VariantConfig("AJ1-RED-42", new BigDecimal("250000"), null, 8, Map.of("Colour", "Chicago Red/White", "Size", "42"), true),
//                        new VariantConfig("AJ1-RED-44", new BigDecimal("250000"), null, 3, Map.of("Colour", "Chicago Red/White", "Size", "44"), true),
//                        new VariantConfig("AJ1-BLK-41", new BigDecimal("265000"), null, 4, Map.of("Colour", "Shadow Black", "Size", "41"), true),
//                        new VariantConfig("AJ1-BLK-43", new BigDecimal("265000"), null, 2, Map.of("Colour", "Shadow Black", "Size", "43"), true),
//                        new VariantConfig("AJ1-BLK-45", new BigDecimal("265000"), null, 1, Map.of("Colour", "Shadow Black", "Size", "45"), true)
//                )
//        );
//
//        // 2. New Balance 990v5
//        createProductWithVariants(
//                "New Balance 990v5 Made in USA",
//                "new-balance-990v5",
//                menSneakers, "new-balance",
//                new BigDecimal("195000"), null, BigDecimal.ZERO, true,
//                "Premium USA-crafted running and lifestyle sneaker. The 990 series is a cultural icon across four decades.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1491553895911-0055eca6402d?w=800&q=80"
//                }, null),
//                List.of("sneakers", "new-balance", "made-in-usa", "lifestyle"),
//                Map.of("Upper", "Mesh & Suede", "Midsole", "ENCAP + C-CAP", "Origin", "USA"),
//                Map.of("Colour", List.of("Grey/White", "Navy/Silver"), "Size", List.of("40", "41", "42", "43", "44")),
//                List.of(
//                        new VariantConfig("NB990-GW-40", new BigDecimal("195000"), null, 6, Map.of("Colour", "Grey/White", "Size", "40"), true),
//                        new VariantConfig("NB990-GW-42", new BigDecimal("195000"), null, 10, Map.of("Colour", "Grey/White", "Size", "42"), true),
//                        new VariantConfig("NB990-GW-44", new BigDecimal("195000"), null, 4, Map.of("Colour", "Grey/White", "Size", "44"), true),
//                        new VariantConfig("NB990-NS-41", new BigDecimal("195000"), null, 5, Map.of("Colour", "Navy/Silver", "Size", "41"), true),
//                        new VariantConfig("NB990-NS-43", new BigDecimal("195000"), null, 3, Map.of("Colour", "Navy/Silver", "Size", "43"), true)
//                )
//        );
//
//        // 3. Adidas Ultraboost 23
//        createProductWithVariants(
//                "Adidas Ultraboost 23 Running Shoes",
//                "adidas-ultraboost-23",
//                menSneakers, "adidas",
//                new BigDecimal("135000"), null, BigDecimal.ZERO, true,
//                "Responsive Boost midsole technology returns energy with every stride. Built for performance and everyday style.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1518002171953-a080ee817e1f?w=800&q=80"
//                }, null),
//                List.of("sneakers", "adidas", "running", "ultraboost"),
//                Map.of("Upper", "Primeknit+", "Midsole", "Boost Foam", "Outsole", "Continental™ Rubber"),
//                Map.of("Colour", List.of("Core Black", "Cloud White", "Solar Red"), "Size", List.of("40", "41", "42", "43", "44")),
//                List.of(
//                        new VariantConfig("UB23-BLK-40", new BigDecimal("135000"), null, 0, Map.of("Colour", "Core Black", "Size", "40"), true),  // OOS
//                        new VariantConfig("UB23-BLK-42", new BigDecimal("135000"), null, 5, Map.of("Colour", "Core Black", "Size", "42"), true),
//                        new VariantConfig("UB23-WHT-41", new BigDecimal("135000"), null, 7, Map.of("Colour", "Cloud White", "Size", "41"), true),
//                        new VariantConfig("UB23-WHT-43", new BigDecimal("135000"), null, 4, Map.of("Colour", "Cloud White", "Size", "43"), true),
//                        new VariantConfig("UB23-RED-42", new BigDecimal("140000"), null, 3, Map.of("Colour", "Solar Red", "Size", "42"), true)
//                )
//        );
//
//        // 4. Levi's 501 Jeans
//        createProductWithVariants(
//                "Levi's 501 Original Fit Jeans",
//                "levis-501-original",
//                menJeans, "levis",
//                new BigDecimal("45000"), null, BigDecimal.ZERO, true,
//                "The original straight-leg jean since 1873. Button fly, five-pocket styling, 100% cotton denim.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&q=80",
//                        "https://images.unsplash.com/photo-1604198453847-0a6240472e34?w=800&q=80"
//                }, null),
//                List.of("jeans", "denim", "levis", "casual"),
//                Map.of("Material", "100% Cotton Denim", "Fit", "Straight", "Rise", "Mid", "Closure", "Button Fly"),
//                Map.of("Colour", List.of("Mid Blue Wash", "Dark Blue Wash", "Jet Black"), "Waist", List.of("28", "30", "32", "34", "36")),
//                List.of(
//                        new VariantConfig("LV501-MB-30", new BigDecimal("45000"), new BigDecimal("55000"), 15, Map.of("Colour", "Mid Blue Wash", "Waist", "30"), true),
//                        new VariantConfig("LV501-MB-32", new BigDecimal("45000"), new BigDecimal("55000"), 20, Map.of("Colour", "Mid Blue Wash", "Waist", "32"), true),
//                        new VariantConfig("LV501-DB-32", new BigDecimal("45000"), new BigDecimal("55000"), 12, Map.of("Colour", "Dark Blue Wash", "Waist", "32"), true),
//                        new VariantConfig("LV501-DB-34", new BigDecimal("45000"), null, 9, Map.of("Colour", "Dark Blue Wash", "Waist", "34"), true),
//                        new VariantConfig("LV501-BLK-30", new BigDecimal("45000"), null, 8, Map.of("Colour", "Jet Black", "Waist", "30"), true),
//                        new VariantConfig("LV501-BLK-34", new BigDecimal("45000"), null, 5, Map.of("Colour", "Jet Black", "Waist", "34"), true)
//                )
//        );
//
//        // 5. Calvin Klein Slim Chinos
//        createProductWithVariants(
//                "Calvin Klein Slim Fit Stretch Chinos",
//                "ck-slim-chinos",
//                menJeans, "calvin-klein",
//                new BigDecimal("38000"), null, BigDecimal.ZERO, true,
//                "Modern slim-fit chinos with a hint of stretch. Smart casual at its finest.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1473966968600-fa801b869a1a?w=800&q=80"
//                }, null),
//                List.of("chinos", "calvin-klein", "smart-casual", "office"),
//                Map.of("Material", "97% Cotton, 3% Elastane", "Fit", "Slim", "Closure", "Zip Fly"),
//                Map.of("Colour", List.of("Khaki", "Navy", "Olive", "Stone"), "Waist", List.of("30", "32", "34", "36")),
//                List.of(
//                        new VariantConfig("CK-CH-KHK-30", new BigDecimal("38000"), null, 10, Map.of("Colour", "Khaki", "Waist", "30"), true),
//                        new VariantConfig("CK-CH-KHK-32", new BigDecimal("38000"), null, 14, Map.of("Colour", "Khaki", "Waist", "32"), true),
//                        new VariantConfig("CK-CH-NVY-32", new BigDecimal("38000"), null, 9, Map.of("Colour", "Navy", "Waist", "32"), true),
//                        new VariantConfig("CK-CH-OLV-34", new BigDecimal("38000"), null, 6, Map.of("Colour", "Olive", "Waist", "34"), true),
//                        new VariantConfig("CK-CH-STN-32", new BigDecimal("38000"), null, 8, Map.of("Colour", "Stone", "Waist", "32"), true)
//                )
//        );
//
//        // 6. Lacoste Classic Polo
//        createProductWithVariants(
//                "Lacoste L.12.12 Classic Piqué Polo",
//                "lacoste-l1212-polo",
//                menTees, "lacoste",
//                new BigDecimal("55000"), null, BigDecimal.ZERO, true,
//                "The original polo shirt. Slim-fit piqué cotton with iconic embroidered crocodile. Born on the tennis court in 1933.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1581655353564-df123a1eb820?w=800&q=80"
//                }, null),
//                List.of("polo", "lacoste", "classic", "smart-casual"),
//                Map.of("Material", "100% Piqué Cotton", "Fit", "Slim", "Collar", "Ribbed"),
//                Map.of("Colour", List.of("White", "Navy Blue", "Forest Green", "Burgundy Red"), "Size", List.of("S", "M", "L", "XL", "XXL")),
//                List.of(
//                        new VariantConfig("LAC-WH-M", new BigDecimal("55000"), null, 12, Map.of("Colour", "White", "Size", "M"), true),
//                        new VariantConfig("LAC-WH-L", new BigDecimal("55000"), null, 10, Map.of("Colour", "White", "Size", "L"), true),
//                        new VariantConfig("LAC-WH-XL", new BigDecimal("55000"), null, 6, Map.of("Colour", "White", "Size", "XL"), true),
//                        new VariantConfig("LAC-NVY-M", new BigDecimal("55000"), null, 8, Map.of("Colour", "Navy Blue", "Size", "M"), true),
//                        new VariantConfig("LAC-GRN-L", new BigDecimal("55000"), null, 5, Map.of("Colour", "Forest Green", "Size", "L"), true),
//                        new VariantConfig("LAC-RED-XL", new BigDecimal("55000"), null, 4, Map.of("Colour", "Burgundy Red", "Size", "XL"), true)
//                )
//        );
//
//        // 7. Tommy Hilfiger Oxford Shirt
//        createProductWithVariants(
//                "Tommy Hilfiger Classic Oxford Shirt",
//                "tommy-oxford-button-down",
//                menTees, "tommy-hilfiger",
//                new BigDecimal("42000"), null, BigDecimal.ZERO, true,
//                "Timeless Oxford weave button-down with signature embroidered flag logo. From boardroom to weekend.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&q=80"
//                }, null),
//                List.of("shirt", "tommy-hilfiger", "oxford", "classic"),
//                Map.of("Material", "100% Cotton Oxford", "Collar", "Button-Down", "Cuffs", "Single Button"),
//                Map.of("Colour", List.of("White", "Blue Stripe", "Pink", "Light Blue"), "Size", List.of("S", "M", "L", "XL")),
//                List.of(
//                        new VariantConfig("TH-OX-WHT-M", new BigDecimal("42000"), null, 10, Map.of("Colour", "White", "Size", "M"), true),
//                        new VariantConfig("TH-OX-WHT-L", new BigDecimal("42000"), null, 8, Map.of("Colour", "White", "Size", "L"), true),
//                        new VariantConfig("TH-OX-BLS-M", new BigDecimal("42000"), null, 7, Map.of("Colour", "Blue Stripe", "Size", "M"), true),
//                        new VariantConfig("TH-OX-PNK-L", new BigDecimal("42000"), null, 5, Map.of("Colour", "Pink", "Size", "L"), true),
//                        new VariantConfig("TH-OX-LBL-XL", new BigDecimal("42000"), null, 4, Map.of("Colour", "Light Blue", "Size", "XL"), true)
//                )
//        );
//
//        // 8. H&M Relaxed Hoodie
//        createProductWithVariants(
//                "H&M Relaxed Fit Pullover Hoodie",
//                "hm-relaxed-hoodie",
//                menTees, "handm",
//                new BigDecimal("25000"), null, new BigDecimal("20"), true,
//                "Soft cotton-blend hoodie with kangaroo pocket and adjustable drawstring hood. A wardrobe essential.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=800&q=80"
//                }, null),
//                List.of("hoodie", "casual", "sale", "hm", "essentials"),
//                Map.of("Material", "80% Cotton, 20% Polyester", "Fit", "Relaxed"),
//                Map.of("Colour", List.of("Charcoal Grey", "Jet Black", "Dusty Blue", "Stone Beige"), "Size", List.of("S", "M", "L", "XL", "XXL")),
//                List.of(
//                        new VariantConfig("HM-HD-CGY-M", new BigDecimal("25000"), null, 20, Map.of("Colour", "Charcoal Grey", "Size", "M"), true),
//                        new VariantConfig("HM-HD-CGY-L", new BigDecimal("25000"), null, 25, Map.of("Colour", "Charcoal Grey", "Size", "L"), true),
//                        new VariantConfig("HM-HD-BLK-L", new BigDecimal("25000"), null, 18, Map.of("Colour", "Jet Black", "Size", "L"), true),
//                        new VariantConfig("HM-HD-BLK-XL", new BigDecimal("25000"), null, 10, Map.of("Colour", "Jet Black", "Size", "XL"), true),
//                        new VariantConfig("HM-HD-DBL-M", new BigDecimal("25000"), null, 8, Map.of("Colour", "Dusty Blue", "Size", "M"), true),
//                        new VariantConfig("HM-HD-STN-XXL", new BigDecimal("25000"), null, 6, Map.of("Colour", "Stone Beige", "Size", "XXL"), true)
//                )
//        );
//
//        // 9. Zara Essential Tee
//        createProductWithVariants(
//                "Zara Essential Premium Cotton Tee",
//                "zara-essential-premium-tee",
//                menTees, "zara",
//                new BigDecimal("15000"), null, BigDecimal.ZERO, true,
//                "Heavyweight 200gsm premium cotton t-shirt. Minimal branding, versatile colourways, built to last.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&q=80"
//                }, null),
//                List.of("tshirt", "basics", "zara", "essentials"),
//                Map.of("Material", "100% Cotton", "Weight", "200gsm", "Fit", "Regular"),
//                Map.of("Colour", List.of("White", "Black", "Sage Green", "Terracotta", "Ecru"), "Size", List.of("XS", "S", "M", "L", "XL")),
//                List.of(
//                        new VariantConfig("ZR-TEE-WHT-M", new BigDecimal("15000"), null, 30, Map.of("Colour", "White", "Size", "M"), true),
//                        new VariantConfig("ZR-TEE-WHT-L", new BigDecimal("15000"), null, 25, Map.of("Colour", "White", "Size", "L"), true),
//                        new VariantConfig("ZR-TEE-BLK-M", new BigDecimal("15000"), null, 28, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("ZR-TEE-BLK-L", new BigDecimal("15000"), null, 20, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("ZR-TEE-SGN-L", new BigDecimal("15000"), null, 15, Map.of("Colour", "Sage Green", "Size", "L"), true),
//                        new VariantConfig("ZR-TEE-TER-M", new BigDecimal("15000"), null, 12, Map.of("Colour", "Terracotta", "Size", "M"), true)
//                )
//        );
//
//        // 10. Puma T7 Tracksuit (inactive - coming soon)
//        createProductWithVariants(
//                "Puma T7 Iconic Tracksuit Set",
//                "puma-t7-tracksuit",
//                menSport, "puma",
//                new BigDecimal("95000"), null, BigDecimal.ZERO, false, // INACTIVE
//                "Classic Puma T7 track jacket and matching pants. Heritage sportswear at its finest. Dropping next season.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&q=80"
//                }, null),
//                List.of("tracksuit", "puma", "sportswear", "coming-soon"),
//                Map.of("Material", "100% Polyester", "Fit", "Regular", "Pieces", "2"),
//                Map.of("Colour", List.of("Black/Gold", "Navy/White"), "Size", List.of("S", "M", "L", "XL")),
//                List.of(
//                        new VariantConfig("PM-T7-BG-M", new BigDecimal("95000"), null, 0, Map.of("Colour", "Black/Gold", "Size", "M"), true),
//                        new VariantConfig("PM-T7-BG-L", new BigDecimal("95000"), null, 0, Map.of("Colour", "Black/Gold", "Size", "L"), true),
//                        new VariantConfig("PM-T7-NW-L", new BigDecimal("95000"), null, 0, Map.of("Colour", "Navy/White", "Size", "L"), true)
//                )
//        );
//    }
//
//    // ================================================================
//    // WOMEN'S PRODUCTS (8 products)
//    // ================================================================
//
//    private void seedWomenProducts() {
//
//        // 11. Zara Floral Midi Dress
//        createProductWithVariants(
//                "Zara Floral Print Wrap Midi Dress",
//                "zara-floral-wrap-midi-dress",
//                womenDresses, "zara",
//                new BigDecimal("48000"), null, BigDecimal.ZERO, true,
//                "Elegant floral wrap midi dress with V-neck and flutter sleeves. Perfect for any occasion, day or night.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1496747611176-843222e1e57c?w=800&q=80",
//                        "https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?w=800&q=80"
//                }, null),
//                List.of("dress", "zara", "floral", "midi", "summer"),
//                Map.of("Material", "100% Viscose", "Length", "Midi", "Neckline", "V-Neck"),
//                Map.of("Colour", List.of("Floral Pink", "Floral Blue", "Floral Yellow"), "Size", List.of("XS", "S", "M", "L", "XL")),
//                List.of(
//                        new VariantConfig("ZR-WMD-PNK-S", new BigDecimal("48000"), null, 8, Map.of("Colour", "Floral Pink", "Size", "S"), true),
//                        new VariantConfig("ZR-WMD-PNK-M", new BigDecimal("48000"), null, 10, Map.of("Colour", "Floral Pink", "Size", "M"), true),
//                        new VariantConfig("ZR-WMD-BLU-S", new BigDecimal("48000"), null, 6, Map.of("Colour", "Floral Blue", "Size", "S"), true),
//                        new VariantConfig("ZR-WMD-BLU-M", new BigDecimal("48000"), null, 7, Map.of("Colour", "Floral Blue", "Size", "M"), true),
//                        new VariantConfig("ZR-WMD-YLW-L", new BigDecimal("48000"), null, 4, Map.of("Colour", "Floral Yellow", "Size", "L"), true)
//                )
//        );
//
//        // 12. ASOS Satin Maxi
//        createProductWithVariants(
//                "ASOS Design Cowl-Neck Satin Maxi Dress",
//                "asos-cowl-satin-maxi",
//                womenDresses, "asos",
//                new BigDecimal("65000"), null, BigDecimal.ZERO, true,
//                "Show-stopping satin maxi with cowl neck and open back. Designed for the spotlight. Fully lined for comfort.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?w=800&q=80"
//                }, null),
//                List.of("dress", "maxi", "evening", "satin", "party"),
//                Map.of("Material", "100% Polyester Satin", "Lining", "Fully Lined", "Neckline", "Cowl"),
//                Map.of("Colour", List.of("Emerald Green", "Champagne", "Midnight Navy", "Crimson Red"), "Size", List.of("UK 6", "UK 8", "UK 10", "UK 12", "UK 14")),
//                List.of(
//                        new VariantConfig("AS-SMD-EM-8", new BigDecimal("65000"), null, 5, Map.of("Colour", "Emerald Green", "Size", "UK 8"), true),
//                        new VariantConfig("AS-SMD-EM-10", new BigDecimal("65000"), null, 3, Map.of("Colour", "Emerald Green", "Size", "UK 10"), true),
//                        new VariantConfig("AS-SMD-CH-8", new BigDecimal("65000"), null, 4, Map.of("Colour", "Champagne", "Size", "UK 8"), true),
//                        new VariantConfig("AS-SMD-NV-10", new BigDecimal("65000"), null, 6, Map.of("Colour", "Midnight Navy", "Size", "UK 10"), true),
//                        new VariantConfig("AS-SMD-CR-12", new BigDecimal("65000"), null, 2, Map.of("Colour", "Crimson Red", "Size", "UK 12"), true)
//                )
//        );
//
//        // 13. Zara Wool Coat
//        createProductWithVariants(
//                "Zara Longline Wool Blend Tailored Coat",
//                "zara-longline-wool-coat",
//                womenOuter, "zara",
//                new BigDecimal("128000"), new BigDecimal("185000"), new BigDecimal("31"), true,
//                "Structured longline coat in premium wool blend. A statement piece for the discerning wardrobe.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1539533113208-f6df8cc8b543?w=800&q=80"
//                }, null),
//                List.of("coat", "winter", "outerwear", "sale", "wool"),
//                Map.of("Material", "65% Wool, 35% Polyamide", "Lining", "Fully Lined", "Length", "Longline"),
//                Map.of("Colour", List.of("Camel", "Black", "Ivory"), "Size", List.of("XS", "S", "M", "L")),
//                List.of(
//                        new VariantConfig("ZR-CT-CAM-S", new BigDecimal("128000"), new BigDecimal("185000"), 5, Map.of("Colour", "Camel", "Size", "S"), true),
//                        new VariantConfig("ZR-CT-CAM-M", new BigDecimal("128000"), new BigDecimal("185000"), 4, Map.of("Colour", "Camel", "Size", "M"), true),
//                        new VariantConfig("ZR-CT-BLK-S", new BigDecimal("128000"), new BigDecimal("185000"), 6, Map.of("Colour", "Black", "Size", "S"), true),
//                        new VariantConfig("ZR-CT-BLK-M", new BigDecimal("128000"), new BigDecimal("185000"), 3, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("ZR-CT-IVY-S", new BigDecimal("128000"), new BigDecimal("185000"), 2, Map.of("Colour", "Ivory", "Size", "S"), true)
//                )
//        );
//
//        // 14. H&M Pleated Skirt
//        createProductWithVariants(
//                "H&M Pleated Satin Midi Skirt",
//                "hm-pleated-satin-midi-skirt",
//                womenSkirts, "handm",
//                new BigDecimal("22000"), null, BigDecimal.ZERO, true,
//                "Flowy pleated satin midi skirt with elasticated waistband. Effortlessly elegant.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1583496661160-fb5218b5f2b9?w=800&q=80"
//                }, null),
//                List.of("skirt", "satin", "midi", "hm", "evening"),
//                Map.of("Material", "100% Polyester Satin", "Waistband", "Elasticated", "Length", "Midi"),
//                Map.of("Colour", List.of("Dusty Rose", "Champagne Gold", "Sage Green", "Midnight Blue"), "Size", List.of("XS", "S", "M", "L", "XL")),
//                List.of(
//                        new VariantConfig("HM-SKT-DR-S", new BigDecimal("22000"), null, 12, Map.of("Colour", "Dusty Rose", "Size", "S"), true),
//                        new VariantConfig("HM-SKT-DR-M", new BigDecimal("22000"), null, 15, Map.of("Colour", "Dusty Rose", "Size", "M"), true),
//                        new VariantConfig("HM-SKT-CG-S", new BigDecimal("22000"), null, 8, Map.of("Colour", "Champagne Gold", "Size", "S"), true),
//                        new VariantConfig("HM-SKT-SG-M", new BigDecimal("22000"), null, 7, Map.of("Colour", "Sage Green", "Size", "M"), true),
//                        new VariantConfig("HM-SKT-MNB-L", new BigDecimal("22000"), null, 5, Map.of("Colour", "Midnight Blue", "Size", "L"), true)
//                )
//        );
//
//        // 15. Nike Pro Tights
//        createProductWithVariants(
//                "Nike Pro High-Waisted Training Tights",
//                "nike-pro-hw-tights",
//                womenActivewear, "nike",
//                new BigDecimal("35000"), null, BigDecimal.ZERO, true,
//                "Supportive high-waisted tights with Dri-FIT technology. Sculpting fit for training, yoga, or daily wear.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1538805060514-97d9cc17730c?w=800&q=80"
//                }, null),
//                List.of("leggings", "tights", "nike", "activewear", "gym"),
//                Map.of("Material", "83% Polyester, 17% Spandex", "Technology", "Dri-FIT", "Waist", "High-Waisted"),
//                Map.of("Colour", List.of("Black", "Dark Smoke Grey", "Deep Plum"), "Size", List.of("XS", "S", "M", "L", "XL")),
//                List.of(
//                        new VariantConfig("NK-PT-BLK-S", new BigDecimal("35000"), null, 15, Map.of("Colour", "Black", "Size", "S"), true),
//                        new VariantConfig("NK-PT-BLK-M", new BigDecimal("35000"), null, 20, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("NK-PT-BLK-L", new BigDecimal("35000"), null, 12, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("NK-PT-DSG-M", new BigDecimal("35000"), null, 10, Map.of("Colour", "Dark Smoke Grey", "Size", "M"), true),
//                        new VariantConfig("NK-PT-DPL-S", new BigDecimal("35000"), null, 6, Map.of("Colour", "Deep Plum", "Size", "S"), true)
//                )
//        );
//
//        // 16. Gucci Silk Blouse (luxury no variants)
//        createProductNoVariants(
//                "Gucci Silk Crepe de Chine Blouse",
//                "gucci-silk-crepe-blouse",
//                womenTops, "gucci",
//                new BigDecimal("420000"), BigDecimal.ZERO, true,
//                "Luxurious silk blouse with interlocking GG embroidery on the cuff. A lasting wardrobe investment.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=800&q=80"
//                }, null),
//                List.of("blouse", "gucci", "luxury", "silk", "designer")
//        );
//
//        // 17. ASOS Ruched Bodycon Mini
//        createProductWithVariants(
//                "ASOS Design Ruched Bodycon Mini Dress",
//                "asos-ruched-bodycon-mini",
//                womenDresses, "asos",
//                new BigDecimal("32000"), null, BigDecimal.ZERO, true,
//                "Figure-hugging ruched bodycon mini dress. Stretchy fabric, easy to style for nights out.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?w=800&q=80"
//                }, null),
//                List.of("dress", "mini", "bodycon", "asos", "nightout"),
//                Map.of("Material", "92% Polyester, 8% Elastane", "Fit", "Bodycon"),
//                Map.of("Colour", List.of("Black", "Toffee Brown", "Forest Green"), "Size", List.of("UK 6", "UK 8", "UK 10", "UK 12")),
//                List.of(
//                        new VariantConfig("AS-RMD-BLK-8", new BigDecimal("32000"), null, 10, Map.of("Colour", "Black", "Size", "UK 8"), true),
//                        new VariantConfig("AS-RMD-BLK-10", new BigDecimal("32000"), null, 8, Map.of("Colour", "Black", "Size", "UK 10"), true),
//                        new VariantConfig("AS-RMD-TOF-8", new BigDecimal("32000"), null, 5, Map.of("Colour", "Toffee Brown", "Size", "UK 8"), true),
//                        new VariantConfig("AS-RMD-FGN-10", new BigDecimal("32000"), null, 4, Map.of("Colour", "Forest Green", "Size", "UK 10"), true)
//                )
//        );
//
//        // 18. Calvin Klein Linen Blazer
//        createProductWithVariants(
//                "Calvin Klein Relaxed Linen Blazer",
//                "ck-relaxed-linen-blazer",
//                womenOuter, "calvin-klein",
//                new BigDecimal("88000"), null, BigDecimal.ZERO, true,
//                "Effortlessly chic linen blazer in a relaxed single-button fit. Elevated basics at their finest.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1539533113208-f6df8cc8b543?w=800&q=80"
//                }, null),
//                List.of("blazer", "calvin-klein", "office", "linen", "smart"),
//                Map.of("Material", "55% Linen, 45% Viscose", "Fit", "Relaxed", "Closure", "Single Button"),
//                Map.of("Colour", List.of("Off White", "Camel", "Black"), "Size", List.of("XS", "S", "M", "L")),
//                List.of(
//                        new VariantConfig("CK-BLZ-OW-S", new BigDecimal("88000"), null, 5, Map.of("Colour", "Off White", "Size", "S"), true),
//                        new VariantConfig("CK-BLZ-OW-M", new BigDecimal("88000"), null, 4, Map.of("Colour", "Off White", "Size", "M"), true),
//                        new VariantConfig("CK-BLZ-CAM-M", new BigDecimal("88000"), null, 3, Map.of("Colour", "Camel", "Size", "M"), true),
//                        new VariantConfig("CK-BLZ-BLK-L", new BigDecimal("88000"), null, 4, Map.of("Colour", "Black", "Size", "L"), true)
//                )
//        );
//    }
//
//    // ================================================================
//    // ACCESSORIES (7 products)
//    // ================================================================
//
//    private void seedAccessoryProducts() {
//
//        // 19. Gucci GG Belt
//        createProductWithVariants(
//                "Gucci GG Marmont Leather Belt",
//                "gucci-gg-marmont-belt",
//                belts, "gucci",
//                new BigDecimal("350000"), null, BigDecimal.ZERO, true,
//                "Iconic double G buckle belt in Italian calfskin leather. The epitome of understated luxury.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=800&q=80"
//                }, null),
//                List.of("belt", "gucci", "luxury", "leather", "designer"),
//                Map.of("Material", "Calfskin Leather", "Hardware", "Antiqued Brass", "Origin", "Italy", "Width", "3cm"),
//                Map.of("Size", List.of("75cm", "80cm", "85cm", "90cm", "95cm")),
//                List.of(
//                        new VariantConfig("GUC-BLT-75", new BigDecimal("350000"), null, 2, Map.of("Size", "75cm"), true),
//                        new VariantConfig("GUC-BLT-80", new BigDecimal("350000"), null, 3, Map.of("Size", "80cm"), true),
//                        new VariantConfig("GUC-BLT-85", new BigDecimal("350000"), null, 5, Map.of("Size", "85cm"), true),
//                        new VariantConfig("GUC-BLT-90", new BigDecimal("350000"), null, 4, Map.of("Size", "90cm"), true),
//                        new VariantConfig("GUC-BLT-95", new BigDecimal("350000"), null, 2, Map.of("Size", "95cm"), true)
//                )
//        );
//
//        // 20. Ray-Ban Aviators
//        createProductWithVariants(
//                "Ray-Ban Classic Aviator RB3025 Sunglasses",
//                "rayban-aviator-rb3025",
//                sunglasses, "ray-ban",
//                new BigDecimal("115000"), null, BigDecimal.ZERO, true,
//                "The world's most iconic sunglasses. Teardrop frame, metal construction, 100% UV protection.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=800&q=80"
//                }, null),
//                List.of("sunglasses", "ray-ban", "aviator", "classic", "uv-protection"),
//                Map.of("Frame", "Metal", "Lens", "Glass", "UV Protection", "100%", "Polarised", "Available"),
//                Map.of("Lens Colour", List.of("Gold/G-15 Green", "Gold/Brown", "Silver/Blue Mirror")),
//                List.of(
//                        new VariantConfig("RB-AV-GG", new BigDecimal("115000"), null, 8, Map.of("Lens Colour", "Gold/G-15 Green"), true),
//                        new VariantConfig("RB-AV-GB", new BigDecimal("115000"), null, 6, Map.of("Lens Colour", "Gold/Brown"), true),
//                        new VariantConfig("RB-AV-SBM", new BigDecimal("125000"), null, 4, Map.of("Lens Colour", "Silver/Blue Mirror"), true)
//                )
//        );
//
//        // 21. Casio Vintage Watch
//        createProductWithVariants(
//                "Casio Vintage Digital Watch A168W",
//                "casio-a168w-vintage",
//                watches, "casio",
//                new BigDecimal("45000"), null, BigDecimal.ZERO, true,
//                "Retro digital watch with stainless steel band and LED backlight. A timeless cult classic.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=800&q=80"
//                }, null),
//                List.of("watch", "casio", "vintage", "digital", "retro"),
//                Map.of("Movement", "Digital Quartz", "Water Resistance", "30m", "Band", "Stainless Steel"),
//                Map.of("Finish", List.of("Gold", "Silver", "Rose Gold", "Black")),
//                List.of(
//                        new VariantConfig("CAS-GLD", new BigDecimal("45000"), null, 8, Map.of("Finish", "Gold"), true),
//                        new VariantConfig("CAS-SLV", new BigDecimal("40000"), null, 10, Map.of("Finish", "Silver"), true),
//                        new VariantConfig("CAS-RG", new BigDecimal("50000"), null, 4, Map.of("Finish", "Rose Gold"), true),
//                        new VariantConfig("CAS-BLK", new BigDecimal("45000"), null, 6, Map.of("Finish", "Black"), true)
//                )
//        );
//
//        // 22. Fossil Gen 6 Smartwatch
//        createProductWithVariants(
//                "Fossil Gen 6 Hybrid Smartwatch",
//                "fossil-gen6-hybrid-smartwatch",
//                watches, "fossil",
//                new BigDecimal("185000"), null, BigDecimal.ZERO, true,
//                "Style meets smarts. Analog display with built-in health tracking, GPS, and smartphone notifications.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800&q=80"
//                }, null),
//                List.of("watch", "smartwatch", "fossil", "tech-fashion", "hybrid"),
//                Map.of("Display", "Analog + PMOLED", "Battery Life", "Up to 2 weeks", "Water Resistance", "30m", "Connectivity", "Bluetooth"),
//                Map.of("Case", List.of("Smoke Stainless Steel", "Champagne Gold"), "Strap", List.of("Leather", "Stainless Mesh")),
//                List.of(
//                        new VariantConfig("FSL-SMK-LTH", new BigDecimal("185000"), null, 5, Map.of("Case", "Smoke Stainless Steel", "Strap", "Leather"), true),
//                        new VariantConfig("FSL-SMK-MSH", new BigDecimal("195000"), null, 3, Map.of("Case", "Smoke Stainless Steel", "Strap", "Stainless Mesh"), true),
//                        new VariantConfig("FSL-CGD-LTH", new BigDecimal("185000"), null, 4, Map.of("Case", "Champagne Gold", "Strap", "Leather"), true),
//                        new VariantConfig("FSL-CGD-MSH", new BigDecimal("195000"), null, 2, Map.of("Case", "Champagne Gold", "Strap", "Stainless Mesh"), true)
//                )
//        );
//
//        // 23. Michael Kors Saffiano Tote
//        createProductWithVariants(
//                "Michael Kors Jet Set Large Saffiano Tote",
//                "mk-jetset-saffiano-tote",
//                bags, "michael-kors",
//                new BigDecimal("280000"), null, BigDecimal.ZERO, true,
//                "Structured saffiano leather tote with zip closure and multiple interior pockets. A power bag.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=800&q=80",
//                        "https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=800&q=80"
//                }, null),
//                List.of("bag", "tote", "michael-kors", "designer", "leather"),
//                Map.of("Material", "Saffiano Leather", "Closure", "Zip Top", "Hardware", "Gold-Tone", "Pockets", "Multiple"),
//                Map.of("Colour", List.of("Black", "Luggage Brown", "Navy", "Pale Pink")),
//                List.of(
//                        new VariantConfig("MK-JT-BLK", new BigDecimal("280000"), null, 6, Map.of("Colour", "Black"), true),
//                        new VariantConfig("MK-JT-LUG", new BigDecimal("280000"), null, 4, Map.of("Colour", "Luggage Brown"), true),
//                        new VariantConfig("MK-JT-NVY", new BigDecimal("280000"), null, 3, Map.of("Colour", "Navy"), true),
//                        new VariantConfig("MK-JT-PPK", new BigDecimal("280000"), null, 3, Map.of("Colour", "Pale Pink"), true)
//                )
//        );
//
//        // 24. ASOS Crossbody
//        createProductNoVariants(
//                "ASOS Mini Croc-Effect Crossbody Bag",
//                "asos-mini-croc-crossbody",
//                bags, "asos",
//                new BigDecimal("32000"), BigDecimal.ZERO, true,
//                "Compact croc-effect crossbody with adjustable chain strap. Fits your phone, cards, and keys.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&q=80"
//                }, null),
//                List.of("bag", "crossbody", "asos", "everyday", "mini")
//        );
//
//        // 25. New Era Cap
//        createProductWithVariants(
//                "New Era 59FIFTY Fitted Baseball Cap",
//                "new-era-5950-fitted-cap",
//                hats, "new-balance",
//                new BigDecimal("28000"), null, BigDecimal.ZERO, true,
//                "Iconic fitted baseball cap in structured high crown design. The cap of choice for streetwear and sports culture.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1521369909029-2afed882baee?w=800&q=80"
//                }, null),
//                List.of("hat", "cap", "streetwear", "fitted"),
//                Map.of("Material", "100% Wool", "Crown", "High Structured", "Brim", "Flat"),
//                Map.of("Colour", List.of("Black", "Navy", "Forest Green", "Red"), "Size", List.of("S/M", "M/L", "L/XL")),
//                List.of(
//                        new VariantConfig("NE-CAP-BLK-SM", new BigDecimal("28000"), null, 10, Map.of("Colour", "Black", "Size", "S/M"), true),
//                        new VariantConfig("NE-CAP-BLK-ML", new BigDecimal("28000"), null, 12, Map.of("Colour", "Black", "Size", "M/L"), true),
//                        new VariantConfig("NE-CAP-NVY-ML", new BigDecimal("28000"), null, 8, Map.of("Colour", "Navy", "Size", "M/L"), true),
//                        new VariantConfig("NE-CAP-GRN-LXL", new BigDecimal("28000"), null, 5, Map.of("Colour", "Forest Green", "Size", "L/XL"), true),
//                        new VariantConfig("NE-CAP-RED-ML", new BigDecimal("28000"), null, 6, Map.of("Colour", "Red", "Size", "M/L"), true)
//                )
//        );
//    }
//
//    // ================================================================
//    // FOOTWEAR (4 products)
//    // ================================================================
//
//    private void seedFootwearProducts() {
//
//        // 26. Adidas Slides
//        createProductWithVariants(
//                "Adidas Adilette Comfort Slides",
//                "adidas-adilette-comfort-slides",
//                sandals, "adidas",
//                new BigDecimal("18000"), null, BigDecimal.ZERO, true,
//                "Cushioned Cloudfoam midsole slides for ultimate comfort. Pool, beach, or street.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1603487742131-4160ec999306?w=800&q=80"
//                }, null),
//                List.of("sandals", "slides", "adidas", "casual"),
//                Map.of("Upper", "Synthetic", "Midsole", "Cloudfoam"),
//                Map.of("Colour", List.of("Black/White", "White/Black", "Navy/Gold"), "Size", List.of("38", "39", "40", "41", "42", "43", "44")),
//                List.of(
//                        new VariantConfig("ADL-BW-38", new BigDecimal("18000"), null, 8, Map.of("Colour", "Black/White", "Size", "38"), true),
//                        new VariantConfig("ADL-BW-40", new BigDecimal("18000"), null, 12, Map.of("Colour", "Black/White", "Size", "40"), true),
//                        new VariantConfig("ADL-BW-42", new BigDecimal("18000"), null, 10, Map.of("Colour", "Black/White", "Size", "42"), true),
//                        new VariantConfig("ADL-WB-40", new BigDecimal("18000"), null, 8, Map.of("Colour", "White/Black", "Size", "40"), true),
//                        new VariantConfig("ADL-WB-42", new BigDecimal("18000"), null, 7, Map.of("Colour", "White/Black", "Size", "42"), true),
//                        new VariantConfig("ADL-NG-41", new BigDecimal("20000"), null, 4, Map.of("Colour", "Navy/Gold", "Size", "41"), true)
//                )
//        );
//
//        // 27. Ankle Boots
//        createProductWithVariants(
//                "Zara Leather Ankle Block-Heel Boots",
//                "zara-leather-ankle-boots",
//                boots, "zara",
//                new BigDecimal("85000"), null, BigDecimal.ZERO, true,
//                "Classic leather ankle boots with block heel and side zip. From office to evening in one step.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1520639888713-7851133b1ed0?w=800&q=80"
//                }, null),
//                List.of("boots", "ankle-boots", "zara", "heels", "leather"),
//                Map.of("Material", "Genuine Leather Upper", "Heel Height", "5cm", "Closure", "Side Zip"),
//                Map.of("Colour", List.of("Black", "Tan Brown", "Burgundy"), "Size", List.of("36", "37", "38", "39", "40", "41")),
//                List.of(
//                        new VariantConfig("ZR-BOT-BLK-37", new BigDecimal("85000"), null, 5, Map.of("Colour", "Black", "Size", "37"), true),
//                        new VariantConfig("ZR-BOT-BLK-38", new BigDecimal("85000"), null, 7, Map.of("Colour", "Black", "Size", "38"), true),
//                        new VariantConfig("ZR-BOT-BLK-40", new BigDecimal("85000"), null, 4, Map.of("Colour", "Black", "Size", "40"), true),
//                        new VariantConfig("ZR-BOT-TAN-38", new BigDecimal("85000"), null, 3, Map.of("Colour", "Tan Brown", "Size", "38"), true),
//                        new VariantConfig("ZR-BOT-BRG-39", new BigDecimal("85000"), null, 2, Map.of("Colour", "Burgundy", "Size", "39"), true)
//                )
//        );
//
//        // 28. Women's Court Heels
//        createProductWithVariants(
//                "Zara Pointed-Toe Stiletto Court Shoes",
//                "zara-pointed-stiletto-courts",
//                womenHeels, "zara",
//                new BigDecimal("65000"), null, BigDecimal.ZERO, true,
//                "Sleek pointed-toe stilettos in faux leather. Timeless silhouette, effortlessly elevated.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1543163521-1bf539c55dd2?w=800&q=80"
//                }, null),
//                List.of("heels", "stiletto", "court-shoes", "zara", "occasion"),
//                Map.of("Material", "Faux Leather", "Heel Height", "9cm", "Toe", "Pointed"),
//                Map.of("Colour", List.of("Black", "Nude Beige", "Red"), "Size", List.of("36", "37", "38", "39", "40")),
//                List.of(
//                        new VariantConfig("ZR-HEL-BLK-37", new BigDecimal("65000"), null, 6, Map.of("Colour", "Black", "Size", "37"), true),
//                        new VariantConfig("ZR-HEL-BLK-38", new BigDecimal("65000"), null, 7, Map.of("Colour", "Black", "Size", "38"), true),
//                        new VariantConfig("ZR-HEL-NUD-38", new BigDecimal("65000"), null, 5, Map.of("Colour", "Nude Beige", "Size", "38"), true),
//                        new VariantConfig("ZR-HEL-NUD-39", new BigDecimal("65000"), null, 4, Map.of("Colour", "Nude Beige", "Size", "39"), true),
//                        new VariantConfig("ZR-HEL-RED-37", new BigDecimal("65000"), null, 3, Map.of("Colour", "Red", "Size", "37"), true)
//                )
//        );
//
//        // 29. Puma Softride Sandals
//        createProductNoVariants(
//                "Puma Softride Pro Sandals",
//                "puma-softride-pro-sandals",
//                sandals, "puma",
//                new BigDecimal("22000"), BigDecimal.ZERO, true,
//                "Ultra-soft SOFTRIDE midsole sandals. Cushioned, adjustable, and built for all-day comfort.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1603487742131-4160ec999306?w=800&q=80"
//                }, null),
//                List.of("sandals", "puma", "comfort", "casual")
//        );
//    }
//
//    // ================================================================
//    // AFRICAN & NATIVE WEAR (6 products)
//    // ================================================================
//
//    private void seedAfricanWearProducts() {
//
//        // 30. Royal Agbada
//        createProductWithVariants(
//                "Premium 3-Piece Royal Agbada Set",
//                "premium-royal-agbada-3piece",
//                agbada, "ankara-republic",
//                new BigDecimal("95000"), null, BigDecimal.ZERO, true,
//                "Exquisitely hand-stitched 3-piece agbada set with intricate stonework embroidery. For weddings, owambe, and grand occasions.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1529156069898-49953e39b3ac?w=800&q=80",
//                        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80"
//                }, null),
//                List.of("agbada", "native", "owambe", "wedding", "nigerian-fashion"),
//                Map.of("Pieces", "3 (Agbada, Under-shirt, Trousers)", "Embroidery", "Stonework + Thread", "Fabric", "Guinea Brocade"),
//                Map.of("Colour", List.of("Royal Blue", "Wine Red", "Ash Grey", "Champagne Gold", "Forest Green"), "Size", List.of("M", "L", "XL", "XXL", "XXXL")),
//                List.of(
//                        new VariantConfig("AGB-RBL-L", new BigDecimal("95000"), null, 5, Map.of("Colour", "Royal Blue", "Size", "L"), true),
//                        new VariantConfig("AGB-RBL-XL", new BigDecimal("95000"), null, 4, Map.of("Colour", "Royal Blue", "Size", "XL"), true),
//                        new VariantConfig("AGB-WNE-L", new BigDecimal("95000"), null, 4, Map.of("Colour", "Wine Red", "Size", "L"), true),
//                        new VariantConfig("AGB-WNE-XXL", new BigDecimal("95000"), null, 3, Map.of("Colour", "Wine Red", "Size", "XXL"), true),
//                        new VariantConfig("AGB-ASH-XL", new BigDecimal("95000"), null, 3, Map.of("Colour", "Ash Grey", "Size", "XL"), true),
//                        new VariantConfig("AGB-GLD-L", new BigDecimal("110000"), null, 3, Map.of("Colour", "Champagne Gold", "Size", "L"), true),
//                        new VariantConfig("AGB-FGN-XXL", new BigDecimal("95000"), null, 2, Map.of("Colour", "Forest Green", "Size", "XXL"), true)
//                )
//        );
//
//        // 31. Senator Kaftan Set
//        createProductWithVariants(
//                "Premium Embroidered Senator Kaftan Set",
//                "embroidered-senator-kaftan",
//                agbada, "ankara-republic",
//                new BigDecimal("45000"), null, BigDecimal.ZERO, true,
//                "2-piece senator suit with intricate collar and cuff embroidery. The go-to native for every Nigerian man's wardrobe.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1529156069898-49953e39b3ac?w=800&q=80"
//                }, null),
//                List.of("senator", "native", "casual-native", "nigerian", "kaftan"),
//                Map.of("Pieces", "2 (Kaftan Top, Trousers)", "Embroidery", "Collar & Cuff", "Fabric", "Cotton Linen"),
//                Map.of("Colour", List.of("White", "Sky Blue", "Forest Green", "Black", "Deep Purple"), "Size", List.of("M", "L", "XL", "XXL", "XXXL")),
//                List.of(
//                        new VariantConfig("SEN-WHT-M", new BigDecimal("45000"), null, 8, Map.of("Colour", "White", "Size", "M"), true),
//                        new VariantConfig("SEN-WHT-L", new BigDecimal("45000"), null, 12, Map.of("Colour", "White", "Size", "L"), true),
//                        new VariantConfig("SEN-WHT-XL", new BigDecimal("45000"), null, 10, Map.of("Colour", "White", "Size", "XL"), true),
//                        new VariantConfig("SEN-BLU-L", new BigDecimal("45000"), null, 6, Map.of("Colour", "Sky Blue", "Size", "L"), true),
//                        new VariantConfig("SEN-GRN-XL", new BigDecimal("45000"), null, 5, Map.of("Colour", "Forest Green", "Size", "XL"), true),
//                        new VariantConfig("SEN-BLK-XXL", new BigDecimal("45000"), null, 4, Map.of("Colour", "Black", "Size", "XXL"), true)
//                )
//        );
//
//        // 32. Ankara Jumpsuit (Women)
//        createProductWithVariants(
//                "Vibrant Ankara Wide-Leg Halter Jumpsuit",
//                "ankara-wide-leg-halter-jumpsuit",
//                ankara, "ankara-republic",
//                new BigDecimal("38000"), null, BigDecimal.ZERO, true,
//                "Bold Ankara print wide-leg jumpsuit with halter neck. Statement-making and festival-ready.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80"
//                }, null),
//                List.of("ankara", "jumpsuit", "african-print", "women", "owambe"),
//                Map.of("Material", "Ankara Cotton Print", "Neckline", "Halter", "Leg", "Wide"),
//                Map.of("Print", List.of("Kente Mix", "Blue Geometric", "Red Tribal", "Purple Floral"), "Size", List.of("S", "M", "L", "XL", "XXL")),
//                List.of(
//                        new VariantConfig("ANK-JP-KNT-S", new BigDecimal("38000"), null, 5, Map.of("Print", "Kente Mix", "Size", "S"), true),
//                        new VariantConfig("ANK-JP-KNT-M", new BigDecimal("38000"), null, 7, Map.of("Print", "Kente Mix", "Size", "M"), true),
//                        new VariantConfig("ANK-JP-BLG-M", new BigDecimal("38000"), null, 4, Map.of("Print", "Blue Geometric", "Size", "M"), true),
//                        new VariantConfig("ANK-JP-RED-L", new BigDecimal("38000"), null, 3, Map.of("Print", "Red Tribal", "Size", "L"), true),
//                        new VariantConfig("ANK-JP-PFL-XL", new BigDecimal("40000"), null, 2, Map.of("Print", "Purple Floral", "Size", "XL"), true)
//                )
//        );
//
//        // 33. Iro & Buba Set
//        createProductWithVariants(
//                "Premium Ankara Iro & Buba 3-Piece Set",
//                "ankara-iro-buba-set",
//                iroAndBuba, "ankara-republic",
//                new BigDecimal("58000"), null, BigDecimal.ZERO, true,
//                "Traditional women's Iro, Buba, and matching Gele head tie in premium Ankara print. Owambe perfection.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80"
//                }, null),
//                List.of("iro-buba", "ankara", "nigerian", "owambe", "women", "gele"),
//                Map.of("Pieces", "3 (Iro, Buba, Gele)", "Material", "Premium Ankara Cotton 100%", "Gele", "Included"),
//                Map.of("Print", List.of("Purple Festival", "Orange & Gold", "Green & Gold Kente", "Blue Royal"), "Size", List.of("S", "M", "L", "XL", "XXL")),
//                List.of(
//                        new VariantConfig("IRO-PRP-M", new BigDecimal("58000"), null, 4, Map.of("Print", "Purple Festival", "Size", "M"), true),
//                        new VariantConfig("IRO-PRP-L", new BigDecimal("58000"), null, 5, Map.of("Print", "Purple Festival", "Size", "L"), true),
//                        new VariantConfig("IRO-ORG-M", new BigDecimal("58000"), null, 3, Map.of("Print", "Orange & Gold", "Size", "M"), true),
//                        new VariantConfig("IRO-GKT-XL", new BigDecimal("62000"), null, 2, Map.of("Print", "Green & Gold Kente", "Size", "XL"), true),
//                        new VariantConfig("IRO-BLR-L", new BigDecimal("58000"), null, 4, Map.of("Print", "Blue Royal", "Size", "L"), true)
//                )
//        );
//
//        // 34. Men's Kaftan
//        createProductWithVariants(
//                "Premium Men's Embroidered Cotton Kaftan",
//                "mens-embroidered-cotton-kaftan",
//                kaftan, "ankara-republic",
//                new BigDecimal("32000"), null, BigDecimal.ZERO, true,
//                "Lightweight breathable cotton kaftan with elegant neckline and sleeve embroidery. Comfortable for any occasion.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?w=800&q=80"
//                }, null),
//                List.of("kaftan", "native", "men", "nigerian", "casual"),
//                Map.of("Material", "Soft Cotton Linen Blend", "Embroidery", "Neckline & Cuff"),
//                Map.of("Colour", List.of("White", "Cream", "Pale Blue", "Mint Green", "Lavender"), "Size", List.of("M", "L", "XL", "XXL", "XXXL")),
//                List.of(
//                        new VariantConfig("KFT-WHT-L", new BigDecimal("32000"), null, 10, Map.of("Colour", "White", "Size", "L"), true),
//                        new VariantConfig("KFT-WHT-XL", new BigDecimal("32000"), null, 8, Map.of("Colour", "White", "Size", "XL"), true),
//                        new VariantConfig("KFT-CRM-L", new BigDecimal("32000"), null, 7, Map.of("Colour", "Cream", "Size", "L"), true),
//                        new VariantConfig("KFT-BLU-XL", new BigDecimal("32000"), null, 5, Map.of("Colour", "Pale Blue", "Size", "XL"), true),
//                        new VariantConfig("KFT-MNT-XXL", new BigDecimal("32000"), null, 4, Map.of("Colour", "Mint Green", "Size", "XXL"), true)
//                )
//        );
//
//        // 35. Women's Abaya
//        createProductWithVariants(
//                "Luxury Chiffon Abaya with Embroidered Trim",
//                "luxury-chiffon-abaya-embroidered",
//                kaftan, "ankara-republic",
//                new BigDecimal("55000"), null, BigDecimal.ZERO, true,
//                "Flowing full-length chiffon abaya with hand-embroidered cuffs and hem. Modest, elegant, and graceful.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80"
//                }, null),
//                List.of("abaya", "kaftan", "modest-fashion", "women", "occasion"),
//                Map.of("Material", "Premium Chiffon", "Embroidery", "Cuff & Hem", "Length", "Full Length"),
//                Map.of("Colour", List.of("Black", "Midnight Blue", "Dusty Rose", "Ivory"), "Size", List.of("S", "M", "L", "XL", "XXL")),
//                List.of(
//                        new VariantConfig("ABY-BLK-M", new BigDecimal("55000"), null, 6, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("ABY-BLK-L", new BigDecimal("55000"), null, 7, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("ABY-MNB-L", new BigDecimal("55000"), null, 4, Map.of("Colour", "Midnight Blue", "Size", "L"), true),
//                        new VariantConfig("ABY-DR-M", new BigDecimal("55000"), null, 3, Map.of("Colour", "Dusty Rose", "Size", "M"), true),
//                        new VariantConfig("ABY-IVY-XL", new BigDecimal("55000"), null, 2, Map.of("Colour", "Ivory", "Size", "XL"), true)
//                )
//        );
//    }
//
//    // ================================================================
//    // FABRICS (4 products)
//    // ================================================================
//
//    private void seedFabricProducts() {
//
//        // 36. Premium Ankara Fabric
//        createProductWithVariants(
//                "Premium Authentic Ankara Cotton Fabric",
//                "premium-authentic-ankara-fabric",
//                ankaraFabric, "fabrichouse",
//                new BigDecimal("12000"), null, BigDecimal.ZERO, true,
//                "100% cotton authentic Ankara fabric. Vivid, colourfast prints, soft hand-feel. Perfect for dressmaking and tailoring.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?w=800&q=80"
//                }, null),
//                List.of("fabric", "ankara", "cotton", "african-print", "tailoring"),
//                Map.of("Material", "100% Cotton", "Width", "45 inches", "Origin", "Nigeria/Ghana"),
//                Map.of("Yards", List.of("3 yards", "6 yards", "12 yards"), "Print", List.of("Kente Mix", "Blue Geo", "Red & Gold", "Green Abstract", "Orange Tribal")),
//                List.of(
//                        new VariantConfig("ANK-FAB-KNT-3Y", new BigDecimal("6500"), null, 25, Map.of("Yards", "3 yards", "Print", "Kente Mix"), true),
//                        new VariantConfig("ANK-FAB-KNT-6Y", new BigDecimal("12000"), null, 20, Map.of("Yards", "6 yards", "Print", "Kente Mix"), true),
//                        new VariantConfig("ANK-FAB-BLG-6Y", new BigDecimal("12000"), null, 18, Map.of("Yards", "6 yards", "Print", "Blue Geo"), true),
//                        new VariantConfig("ANK-FAB-RG-6Y", new BigDecimal("12000"), null, 15, Map.of("Yards", "6 yards", "Print", "Red & Gold"), true),
//                        new VariantConfig("ANK-FAB-KNT-12Y", new BigDecimal("22000"), null, 10, Map.of("Yards", "12 yards", "Print", "Kente Mix"), true),
//                        new VariantConfig("ANK-FAB-OTR-12Y", new BigDecimal("22000"), null, 8, Map.of("Yards", "12 yards", "Print", "Orange Tribal"), true)
//                )
//        );
//
//        // 37. French Lace Fabric
//        createProductWithVariants(
//                "Imported French Lace Fabric — Aso-Ebi Grade A",
//                "french-lace-aso-ebi-grade-a",
//                laceFabric, "fabrichouse",
//                new BigDecimal("85000"), null, BigDecimal.ZERO, true,
//                "Premium grade French lace fabric. Dense pattern, lustrous finish. The number-one choice for weddings and aso-ebi.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80"
//                }, null),
//                List.of("fabric", "lace", "french-lace", "aso-ebi", "wedding", "luxury"),
//                Map.of("Origin", "France / UAE", "Width", "52 inches", "Quality", "Grade A"),
//                Map.of("Colour", List.of("Royal Purple", "Champagne Gold", "Sky Blue", "Ivory White", "Wine Red"), "Yards", List.of("5 yards", "10 yards")),
//                List.of(
//                        new VariantConfig("LCE-PRP-5Y", new BigDecimal("85000"), null, 5, Map.of("Colour", "Royal Purple", "Yards", "5 yards"), true),
//                        new VariantConfig("LCE-PRP-10Y", new BigDecimal("165000"), null, 3, Map.of("Colour", "Royal Purple", "Yards", "10 yards"), true),
//                        new VariantConfig("LCE-GLD-5Y", new BigDecimal("85000"), null, 6, Map.of("Colour", "Champagne Gold", "Yards", "5 yards"), true),
//                        new VariantConfig("LCE-BLU-5Y", new BigDecimal("85000"), null, 4, Map.of("Colour", "Sky Blue", "Yards", "5 yards"), true),
//                        new VariantConfig("LCE-IVY-10Y", new BigDecimal("165000"), null, 2, Map.of("Colour", "Ivory White", "Yards", "10 yards"), true),
//                        new VariantConfig("LCE-WNE-5Y", new BigDecimal("90000"), null, 3, Map.of("Colour", "Wine Red", "Yards", "5 yards"), true)
//                )
//        );
//
//        // 38. Aso-Oke Bundle
//        createProductWithVariants(
//                "Handwoven Premium Aso-Oke Bundle Set",
//                "premium-handwoven-aso-oke",
//                asoOke, "fabrichouse",
//                new BigDecimal("65000"), null, BigDecimal.ZERO, true,
//                "Authentic handwoven Aso-Oke bundle set for gele, ipele, and iro. Traditional craftsmanship for royal occasions.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?w=800&q=80"
//                }, null),
//                List.of("aso-oke", "fabric", "gele", "traditional", "wedding"),
//                Map.of("Weave", "Handwoven", "Set Includes", "Gele, Ipele, Iro", "Origin", "Iseyin, Oyo State"),
//                Map.of("Colour", List.of("Gold & Red", "Purple & Gold", "Blue & Silver", "Green & Gold")),
//                List.of(
//                        new VariantConfig("ASO-GR", new BigDecimal("65000"), null, 8, Map.of("Colour", "Gold & Red"), true),
//                        new VariantConfig("ASO-PG", new BigDecimal("65000"), null, 6, Map.of("Colour", "Purple & Gold"), true),
//                        new VariantConfig("ASO-BS", new BigDecimal("65000"), null, 5, Map.of("Colour", "Blue & Silver"), true),
//                        new VariantConfig("ASO-GG", new BigDecimal("70000"), null, 4, Map.of("Colour", "Green & Gold"), true)
//                )
//        );
//
//        // 39. Swiss Voile Fabric (no variants, sold per yard)
//        createProductNoVariants(
//                "Swiss Voile Embroidered Cotton Fabric",
//                "swiss-voile-embroidered",
//                laceFabric, "fabrichouse",
//                new BigDecimal("8500"), BigDecimal.ZERO, true,
//                "Lightweight Swiss voile with delicate embroidery. 100% cotton, breathable. Ideal for elegant blouses and casual native styles. Sold per yard.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80"
//                }, null),
//                List.of("fabric", "voile", "swiss-voile", "cotton", "casual")
//        );
//    }
//
//    // ================================================================
//    // HOME TEXTILES (5 products)
//    // ================================================================
//
//    private void seedHomeTextileProducts() {
//
//        // 40. Egyptian Cotton Bedsheet Set
//        createProductWithVariants(
//                "BedLuxe 400TC Egyptian Cotton Bedsheet Set",
//                "bedluxe-400tc-egyptian-cotton-set",
//                bedsheets, "bedluxe",
//                new BigDecimal("55000"), null, BigDecimal.ZERO, true,
//                "Hotel-grade 400 thread count Egyptian cotton. Silky soft, breathable, and hypoallergenic. Sleep like royalty.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=800&q=80"
//                }, null),
//                List.of("bedsheets", "egyptian-cotton", "luxury-home", "bedluxe", "400tc"),
//                Map.of("Thread Count", "400TC", "Material", "100% Egyptian Cotton", "Set Includes", "1 Flat, 1 Fitted, 2 Pillowcases"),
//                Map.of("Colour", List.of("Crisp White", "Soft Grey", "Navy Blue", "Blush Pink", "Sage Green"), "Size", List.of("Single", "Double", "Queen", "King")),
//                List.of(
//                        new VariantConfig("BLX-400-WHT-Q", new BigDecimal("55000"), null, 10, Map.of("Colour", "Crisp White", "Size", "Queen"), true),
//                        new VariantConfig("BLX-400-WHT-K", new BigDecimal("65000"), null, 8, Map.of("Colour", "Crisp White", "Size", "King"), true),
//                        new VariantConfig("BLX-400-GRY-Q", new BigDecimal("55000"), null, 7, Map.of("Colour", "Soft Grey", "Size", "Queen"), true),
//                        new VariantConfig("BLX-400-NVY-K", new BigDecimal("65000"), null, 5, Map.of("Colour", "Navy Blue", "Size", "King"), true),
//                        new VariantConfig("BLX-400-PNK-D", new BigDecimal("45000"), null, 6, Map.of("Colour", "Blush Pink", "Size", "Double"), true),
//                        new VariantConfig("BLX-400-SGN-Q", new BigDecimal("55000"), null, 4, Map.of("Colour", "Sage Green", "Size", "Queen"), true),
//                        new VariantConfig("BLX-400-WHT-S", new BigDecimal("35000"), null, 12, Map.of("Colour", "Crisp White", "Size", "Single"), true)
//                )
//        );
//
//        // 41. Microfibre Bedsheet Set (budget, on sale)
//        createProductWithVariants(
//                "BedLuxe Ultra-Soft Microfibre Bedsheet Set",
//                "bedluxe-microfibre-bedsheet-set",
//                bedsheets, "bedluxe",
//                new BigDecimal("28000"), null, new BigDecimal("15"), true,
//                "1800 series ultra-soft microfibre. Wrinkle-resistant, fade-resistant, machine washable. Excellent value.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1631729371254-42c2892f0e6e?w=800&q=80"
//                }, null),
//                List.of("bedsheets", "microfibre", "affordable", "sale", "bedluxe"),
//                Map.of("Material", "1800 Series Microfibre", "Set Includes", "1 Flat, 1 Fitted, 2 Pillowcases"),
//                Map.of("Colour", List.of("White", "Light Grey", "Teal", "Burgundy", "Sand Beige"), "Size", List.of("Single", "Double", "Queen", "King")),
//                List.of(
//                        new VariantConfig("BLX-MF-WHT-Q", new BigDecimal("28000"), null, 15, Map.of("Colour", "White", "Size", "Queen"), true),
//                        new VariantConfig("BLX-MF-WHT-K", new BigDecimal("32000"), null, 10, Map.of("Colour", "White", "Size", "King"), true),
//                        new VariantConfig("BLX-MF-GRY-Q", new BigDecimal("28000"), null, 12, Map.of("Colour", "Light Grey", "Size", "Queen"), true),
//                        new VariantConfig("BLX-MF-TL-D", new BigDecimal("24000"), null, 10, Map.of("Colour", "Teal", "Size", "Double"), true),
//                        new VariantConfig("BLX-MF-BRG-K", new BigDecimal("32000"), null, 6, Map.of("Colour", "Burgundy", "Size", "King"), true),
//                        new VariantConfig("BLX-MF-SND-S", new BigDecimal("18000"), null, 15, Map.of("Colour", "Sand Beige", "Size", "Single"), true)
//                )
//        );
//
//        // 42. Jacquard Duvet Set
//        createProductWithVariants(
//                "BedLuxe Jacquard Duvet Cover Set",
//                "bedluxe-jacquard-duvet-cover",
//                duvetSets, "bedluxe",
//                new BigDecimal("72000"), null, BigDecimal.ZERO, true,
//                "Luxurious woven jacquard duvet cover with matching pillowcases. Elevates any bedroom instantly.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1631729371254-42c2892f0e6e?w=800&q=80",
//                        "https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=800&q=80"
//                }, null),
//                List.of("duvet", "jacquard", "luxury-home", "bedluxe"),
//                Map.of("Material", "Woven Jacquard Polyester", "Set Includes", "1 Duvet Cover, 2 Pillowcases"),
//                Map.of("Colour", List.of("Ivory Gold", "Silver Grey", "Midnight Blue", "Rose Blush"), "Size", List.of("Double", "Queen", "King")),
//                List.of(
//                        new VariantConfig("BLX-JQ-IVG-Q", new BigDecimal("72000"), null, 6, Map.of("Colour", "Ivory Gold", "Size", "Queen"), true),
//                        new VariantConfig("BLX-JQ-IVG-K", new BigDecimal("82000"), null, 4, Map.of("Colour", "Ivory Gold", "Size", "King"), true),
//                        new VariantConfig("BLX-JQ-SGR-Q", new BigDecimal("72000"), null, 5, Map.of("Colour", "Silver Grey", "Size", "Queen"), true),
//                        new VariantConfig("BLX-JQ-MNB-K", new BigDecimal("82000"), null, 3, Map.of("Colour", "Midnight Blue", "Size", "King"), true),
//                        new VariantConfig("BLX-JQ-RSB-D", new BigDecimal("65000"), null, 4, Map.of("Colour", "Rose Blush", "Size", "Double"), true)
//                )
//        );
//
//        // 43. Memory Foam Pillow Pair
//        createProductWithVariants(
//                "BedLuxe Memory Foam Support Pillow Pair",
//                "bedluxe-memory-foam-pillow-pair",
//                pillows, "bedluxe",
//                new BigDecimal("22000"), null, BigDecimal.ZERO, true,
//                "Pair of adaptive memory foam pillows with breathable bamboo-blend cover. Ergonomic support for perfect sleep.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=800&q=80"
//                }, null),
//                List.of("pillow", "memory-foam", "sleep", "bedluxe"),
//                Map.of("Material", "Memory Foam", "Cover", "Bamboo-Blend", "Count", "Pack of 2"),
//                Map.of("Firmness", List.of("Soft", "Medium", "Firm")),
//                List.of(
//                        new VariantConfig("BLX-PIL-SOFT", new BigDecimal("22000"), null, 10, Map.of("Firmness", "Soft"), true),
//                        new VariantConfig("BLX-PIL-MED", new BigDecimal("22000"), null, 14, Map.of("Firmness", "Medium"), true),
//                        new VariantConfig("BLX-PIL-FIRM", new BigDecimal("22000"), null, 8, Map.of("Firmness", "Firm"), true)
//                )
//        );
//
//        // 44. Throw Pillow Set (decorative, no variants)
//        createProductNoVariants(
//                "BedLuxe Velvet Decorative Throw Pillow Set",
//                "bedluxe-velvet-throw-cushions",
//                pillows, "bedluxe",
//                new BigDecimal("14500"), BigDecimal.ZERO, true,
//                "Set of 2 plush velvet throw cushions. Adds instant texture and warmth to any sofa or bed. Removable zippered cover.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=800&q=80"
//                }, null),
//                List.of("pillow", "cushion", "velvet", "decor", "bedluxe")
//        );
//    }
//
//    // ================================================================
//    // HELPERS
//    // ================================================================
//
//    private void createProductNoVariants(
//            String name, String slug, Category cat, String brandSlug,
//            BigDecimal price, BigDecimal discount, boolean isActive,
//            String description, List<ProductImage> media, List<String> tags
//    ) {
//        ProductRequest req = new ProductRequest();
//        req.setName(name);
//        req.setSlug(slug);
//        req.setBasePrice(price);
//        req.setCategorySlug(cat.getSlug());
//        req.setBrandSlug(brandSlug);
//        req.setDescription(description);
//        req.setImages(media);
//        req.setTags(tags);
//        req.setDiscount(discount);
//        req.setIsActive(isActive);
//        productService.createOrUpdateProduct(req);
//    }
//
//    private void createProductWithVariants(
//            String name, String slug, Category cat, String brandSlug,
//            BigDecimal basePrice, BigDecimal compareAt, BigDecimal discount, boolean isActive,
//            String description, List<ProductImage> media, List<String> tags,
//            Map<String, String> specs, Map<String, List<String>> options,
//            List<VariantConfig> configs
//    ) {
//        ProductRequest req = new ProductRequest();
//        req.setName(name);
//        req.setSlug(slug);
//        req.setBasePrice(basePrice);
//        req.setCompareAtPrice(compareAt);
//        req.setCategorySlug(cat.getSlug());
//        req.setBrandSlug(brandSlug);
//        req.setDescription(description);
//        req.setImages(media);
//        req.setTags(tags);
//        req.setSpecifications(specs);
//        req.setVariantOptions(options);
//        req.setDiscount(discount);
//        req.setIsActive(isActive);
//
//        Product product = productService.createOrUpdateProduct(req);
//
//        for (VariantConfig c : configs) {
//            VariantRequest v = new VariantRequest();
//            v.setProductId(product.getId());
//            v.setSku(c.sku());
//            v.setPrice(c.price());
//            v.setCompareAtPrice(c.compareAtPrice());
//            v.setStockQuantity(c.stock());
//            v.setAttributes(c.attributes());
//            v.setImages(media);
//            v.setIsActive(c.isActive());
//            productService.saveVariant(v);
//        }
//    }
//
//    private List<ProductImage> mediaGallery(String[] imageUrls, String[] videoUrls) {
//        List<ProductImage> gallery = new ArrayList<>();
//        if (imageUrls != null) {
//            for (int i = 0; i < imageUrls.length; i++) {
//                gallery.add(new ProductImage(imageUrls[i], i == 0, ProductImage.MediaType.IMAGE));
//            }
//        }
//        if (videoUrls != null) {
//            for (String vUrl : videoUrls) {
//                gallery.add(new ProductImage(vUrl, false, ProductImage.MediaType.VIDEO));
//            }
//        }
//        return gallery;
//    }
//
//    private Category saveCat(String name, String slug, Category parent, String img) {
//        Category c = new Category();
//        c.setName(name);
//        c.setSlug(slug);
//        c.setParent(parent);
//        c.setImageUrl(img);
//        c.setLineage(parent != null
//                ? (parent.getLineage() == null ? "," : parent.getLineage()) + parent.getId() + ","
//                : ",");
//        return categoryRepo.save(c);
//    }
//
//    private record VariantConfig(
//            String sku,
//            BigDecimal price,
//            BigDecimal compareAtPrice,
//            int stock,
//            Map<String, String> attributes,
//            boolean isActive
//    ) {}
//}