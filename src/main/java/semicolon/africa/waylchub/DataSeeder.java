//package semicolon.africa.waylchub;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.boot.CommandLineRunner;
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
///**
// * Seeds the catalog with ONLY streetwear Klinpikk currently produces in-house:
// * graphic tees, shorts, joggers, hoodies and jerseys, all under the single
// * "Klinpikk" brand. Every item below either appears on Klinpikk's most recent
// * wholesale invoice (Pro Forma Invoice #001754, ExploreABA Ltd, 16/06/2026) or
// * is an earlier in-house style of the same kind, kept in the catalog as
// * existing stock. No native wear, no footwear/accessories/fabrics, no
// * third-party brands, no "coming soon" placeholders — everything here is
// * something Klinpikk can actually cut, sew, and ship today.
// *
// * IMAGES: every photo below is a temporary Unsplash stock photo chosen to
// * match the garment type (free/commercial-use license). Swap in real
// * supplier/product photos before go-live — every image URL is isolated in
// * its own mediaGallery(...) call so it's a one-line change per product.
// *
// * NOTE: this wipes the database on every run (same destructive-seeder pattern
// * as before). Comment out the @Component / run() call before going live.
// */
//@Component
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
//        System.out.println("🚀 STARTING KLINPIKK CATALOG SEEDER");
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
//    // BRAND — single brand for now. Add more here once other tailors /
//    // production partners come on board (e.g. each partner becomes their
//    // own Brand).
//    // ================================================================
//
//    private static final String BRAND_SLUG = "klinpikk";
//
//    private void createBrands() {
//        Brand brand = new Brand();
//        brand.setName("Klinpikk");
//        brand.setSlug(BRAND_SLUG);
//        brandRepo.save(brand);
//        System.out.println("✅ Brand created: Klinpikk");
//    }
//
//    // ================================================================
//    // CATEGORIES — only categories that map to garments Klinpikk
//    // actually cuts and sews today. No native wear, no footwear/
//    // accessories, no raw fabric — those come later.
//    // ================================================================
//
//    private Category fashion;
//    private Category tshirts, shorts, joggersTrousers, hoodiesJackets, jerseys;
//
//    private void createCategoryTree() {
//
//        fashion = saveCat("Fashion", "fashion", null,
//                "https://images.unsplash.com/photo-1445205170230-053b83016050?w=800&q=80");
//
//        tshirts = saveCat("T-Shirts & Tops", "t-shirts", fashion,
//                "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&q=80");
//
//        shorts = saveCat("Shorts", "shorts", fashion,
//                "https://images.unsplash.com/photo-1511794322962-129ddbd0af38?w=800&q=80");
//
//        joggersTrousers = saveCat("Joggers & Trousers", "joggers-trousers", fashion,
//                "https://images.unsplash.com/photo-1580906853305-5702e648164e?w=800&q=80");
//
//        hoodiesJackets = saveCat("Hoodies & Jackets", "hoodies-jackets", fashion,
//                "https://images.unsplash.com/photo-1607860087860-c46e865f6ab0?w=800&q=80");
//
//        jerseys = saveCat("Jerseys", "jerseys", fashion,
//                "https://images.unsplash.com/photo-1655089131279-8029e8a21ac6?w=800&q=80");
//
//        System.out.println("✅ Category tree created.");
//    }
//
//    // ================================================================
//    // PRODUCTS — 14 garments, all Klinpikk.
//    // 9 map directly to line items on Pro Forma Invoice #001754
//    // (ExploreABA Ltd, 16/06/2026) — tagged "invoice-001754" below.
//    // 5 are earlier in-house styles kept in the catalog as existing
//    // stock, in the same categories — tagged "older-stock" below.
//    // ================================================================
//
//    private void seedProducts() {
//        seedTShirtsAndTops();
//        seedShorts();
//        seedJoggersAndTrousers();
//        seedHoodiesAndJackets();
//        seedJerseys();
//        System.out.println("✅ All products seeded.");
//    }
//
//    // ----------------------------------------------------------------
//    // T-SHIRTS & TOPS
//    // ----------------------------------------------------------------
//
//    private void seedTShirtsAndTops() {
//
//        // 1. FROM INVOICE — "Bundle of Klinpapi Tshirt Kollection (Size&Colour-Variant)"
//        createProductWithVariants(
//                "Klinpikk Klinpapi Tee Kollection",
//                "klinpikk-klinpapi-tee-kollection",
//                tshirts, BRAND_SLUG,
//                new BigDecimal("12000"), null, BigDecimal.ZERO, true,
//                "Signature Klinpapi graphic tee, cut and sewn in-house on soft cotton jersey. Part of the core Klinpapi print series.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1636047250452-6772f6144b3d?w=800&q=80"
//                }, null),
//                List.of("tshirt", "klinpapi", "streetwear", "invoice-001754"),
//                Map.of("Material", "100% Cotton", "Fit", "Regular", "Series", "Klinpapi Kollection"),
//                Map.of("Colour", List.of("Black", "White", "Navy"), "Size", List.of("S", "M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-KPP-BLK-M", new BigDecimal("12000"), null, 14, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-KPP-BLK-L", new BigDecimal("12000"), null, 12, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-KPP-WHT-M", new BigDecimal("12000"), null, 13, Map.of("Colour", "White", "Size", "M"), true),
//                        new VariantConfig("KP-KPP-WHT-S", new BigDecimal("12000"), null, 9, Map.of("Colour", "White", "Size", "S"), true),
//                        new VariantConfig("KP-KPP-NVY-L", new BigDecimal("12000"), null, 8, Map.of("Colour", "Navy", "Size", "L"), true),
//                        new VariantConfig("KP-KPP-NVY-XL", new BigDecimal("12000"), null, 6, Map.of("Colour", "Navy", "Size", "XL"), true)
//                )
//        );
//
//        // 2. FROM INVOICE — "Bundle of Premium Plain Acidwash T-Shirt"
//        createProductWithVariants(
//                "Klinpikk Premium Plain Acidwash Tee",
//                "klinpikk-premium-acidwash-tee",
//                tshirts, BRAND_SLUG,
//                new BigDecimal("10000"), null, BigDecimal.ZERO, true,
//                "Heavyweight cotton tee finished with an acid-wash dye for a faded, worn-in look. Plain front, no print.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1542239898-08fcea4abd2e?w=800&q=80"
//                }, null),
//                List.of("tshirt", "acidwash", "streetwear", "invoice-001754"),
//                Map.of("Material", "Heavyweight Cotton", "Fit", "Regular", "Finish", "Acid Wash"),
//                Map.of("Colour", List.of("Black Acidwash", "Blue Acidwash", "Grey Acidwash"), "Size", List.of("S", "M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-ACW-BLK-M", new BigDecimal("10000"), null, 12, Map.of("Colour", "Black Acidwash", "Size", "M"), true),
//                        new VariantConfig("KP-ACW-BLK-L", new BigDecimal("10000"), null, 10, Map.of("Colour", "Black Acidwash", "Size", "L"), true),
//                        new VariantConfig("KP-ACW-BLU-M", new BigDecimal("10000"), null, 9, Map.of("Colour", "Blue Acidwash", "Size", "M"), true),
//                        new VariantConfig("KP-ACW-GRY-S", new BigDecimal("10000"), null, 7, Map.of("Colour", "Grey Acidwash", "Size", "S"), true),
//                        new VariantConfig("KP-ACW-GRY-XL", new BigDecimal("10000"), null, 5, Map.of("Colour", "Grey Acidwash", "Size", "XL"), true)
//                )
//        );
//
//        // 3. FROM INVOICE — "Bundle of Premium Oracloot Skid Kollection (Size&Colour-Variant)"
//        createProductWithVariants(
//                "Klinpikk Oracloot Skid Tee Kollection",
//                "klinpikk-oracloot-skid-tee-kollection",
//                tshirts, BRAND_SLUG,
//                new BigDecimal("12000"), null, BigDecimal.ZERO, true,
//                "Premium graphic tee from the Oracloot Skid print series, cut and sewn in-house on soft cotton jersey.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1696086152586-09a0855dbc9c?w=800&q=80"
//                }, null),
//                List.of("tshirt", "oracloot-skid", "streetwear", "invoice-001754"),
//                Map.of("Material", "100% Cotton", "Fit", "Regular", "Series", "Oracloot Skid Kollection"),
//                Map.of("Colour", List.of("Black", "White", "Sand"), "Size", List.of("S", "M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-ORS-BLK-M", new BigDecimal("12000"), null, 11, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-ORS-BLK-L", new BigDecimal("12000"), null, 9, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-ORS-WHT-M", new BigDecimal("12000"), null, 10, Map.of("Colour", "White", "Size", "M"), true),
//                        new VariantConfig("KP-ORS-SND-S", new BigDecimal("12000"), null, 6, Map.of("Colour", "Sand", "Size", "S"), true),
//                        new VariantConfig("KP-ORS-SND-XL", new BigDecimal("12000"), null, 4, Map.of("Colour", "Sand", "Size", "XL"), true)
//                )
//        );
//
//        // 10. OLDER STOCK — similar style, not on current invoice
//        createProductWithVariants(
//                "Klinpikk Oversized Graphic Tee",
//                "klinpikk-oversized-graphic-tee",
//                tshirts, BRAND_SLUG,
//                new BigDecimal("11000"), null, BigDecimal.ZERO, true,
//                "Drop-shoulder oversized tee in heavyweight cotton, from an earlier Klinpikk in-house run. Kept in stock while new print runs are finalised.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&q=80"
//                }, null),
//                List.of("tshirt", "oversized", "streetwear", "older-stock"),
//                Map.of("Material", "Heavyweight Cotton", "Fit", "Oversized"),
//                Map.of("Colour", List.of("Black", "Off-White"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-OVR-BLK-M", new BigDecimal("11000"), null, 6, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-OVR-BLK-L", new BigDecimal("11000"), null, 5, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-OVR-OWH-XL", new BigDecimal("11000"), null, 3, Map.of("Colour", "Off-White", "Size", "XL"), true)
//                )
//        );
//
//        // 14. OLDER STOCK — similar style, not on current invoice
//        createProductWithVariants(
//                "Klinpikk Classic Polo Shirt",
//                "klinpikk-classic-polo-shirt",
//                tshirts, BRAND_SLUG,
//                new BigDecimal("13000"), null, BigDecimal.ZERO, true,
//                "Pique cotton polo shirt from an earlier Klinpikk in-house run. Clean and versatile, kept in stock alongside the newer tee kollections.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1625910513399-c9fcba54338c?w=800&q=80"
//                }, null),
//                List.of("polo", "streetwear", "older-stock"),
//                Map.of("Material", "Pique Cotton", "Fit", "Regular", "Collar", "Ribbed"),
//                Map.of("Colour", List.of("Black", "Grey"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-POL-BLK-M", new BigDecimal("13000"), null, 5, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-POL-BLK-L", new BigDecimal("13000"), null, 4, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-POL-GRY-XL", new BigDecimal("13000"), null, 3, Map.of("Colour", "Grey", "Size", "XL"), true)
//                )
//        );
//    }
//
//    // ----------------------------------------------------------------
//    // SHORTS
//    // ----------------------------------------------------------------
//
//    private void seedShorts() {
//
//        // 4. FROM INVOICE — "Bundle Of Grigo Kimo Shorts (Variant Colours)"
//        createProductWithVariants(
//                "Klinpikk Grigo Kimo Shorts",
//                "klinpikk-grigo-kimo-shorts",
//                shorts, BRAND_SLUG,
//                new BigDecimal("15000"), null, BigDecimal.ZERO, true,
//                "Cargo-style Grigo Kimo shorts, cut and sewn in-house with a relaxed fit and side pockets.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1511794322962-129ddbd0af38?w=800&q=80"
//                }, null),
//                List.of("shorts", "grigo-kimo", "streetwear", "invoice-001754"),
//                Map.of("Material", "Cotton Twill", "Fit", "Relaxed", "Pockets", "Cargo Side Pockets"),
//                Map.of("Colour", List.of("Khaki", "Black", "Olive"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-GKS-KHK-M", new BigDecimal("15000"), null, 10, Map.of("Colour", "Khaki", "Size", "M"), true),
//                        new VariantConfig("KP-GKS-KHK-L", new BigDecimal("15000"), null, 8, Map.of("Colour", "Khaki", "Size", "L"), true),
//                        new VariantConfig("KP-GKS-BLK-L", new BigDecimal("15000"), null, 9, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-GKS-OLV-XL", new BigDecimal("15000"), null, 5, Map.of("Colour", "Olive", "Size", "XL"), true)
//                )
//        );
//
//        // 6. FROM INVOICE — "Bundle of Grigo Kimo Combat Short"
//        createProductWithVariants(
//                "Klinpikk Grigo Kimo Combat Shorts",
//                "klinpikk-grigo-kimo-combat-shorts",
//                shorts, BRAND_SLUG,
//                new BigDecimal("16000"), null, BigDecimal.ZERO, true,
//                "Multi-pocket combat shorts from the Grigo Kimo line, cut and sewn in-house on durable cotton twill.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1758267928031-a87e5a5c6c5b?w=800&q=80"
//                }, null),
//                List.of("shorts", "combat", "grigo-kimo", "streetwear", "invoice-001754"),
//                Map.of("Material", "Cotton Twill", "Fit", "Relaxed", "Pockets", "6-Pocket Combat"),
//                Map.of("Colour", List.of("Black", "Khaki", "Grey"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-CMB-BLK-M", new BigDecimal("16000"), null, 9, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-CMB-BLK-L", new BigDecimal("16000"), null, 7, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-CMB-KHK-L", new BigDecimal("16000"), null, 6, Map.of("Colour", "Khaki", "Size", "L"), true),
//                        new VariantConfig("KP-CMB-GRY-XL", new BigDecimal("16000"), null, 4, Map.of("Colour", "Grey", "Size", "XL"), true)
//                )
//        );
//    }
//
//    // ----------------------------------------------------------------
//    // JOGGERS & TROUSERS
//    // ----------------------------------------------------------------
//
//    private void seedJoggersAndTrousers() {
//
//        // 7. FROM INVOICE — "Bundle of Grimo Kimo Joggers Sweatpant"
//        // (spelled "Grimo" once on the invoice vs "Grigo" elsewhere — treated
//        // as the same Grigo Kimo line; rename here if that's not the case)
//        createProductWithVariants(
//                "Klinpikk Grigo Kimo Joggers",
//                "klinpikk-grigo-kimo-joggers",
//                joggersTrousers, BRAND_SLUG,
//                new BigDecimal("18000"), null, BigDecimal.ZERO, true,
//                "Tapered fleece joggers from the Grigo Kimo line, cut and sewn in-house with an elasticated cuff and drawstring waist.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1580906853305-5702e648164e?w=800&q=80"
//                }, null),
//                List.of("joggers", "sweatpants", "grigo-kimo", "streetwear", "invoice-001754"),
//                Map.of("Material", "Cotton Fleece", "Fit", "Tapered", "Waist", "Drawstring"),
//                Map.of("Colour", List.of("Black", "Grey", "Navy"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-GKJ-BLK-M", new BigDecimal("18000"), null, 10, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-GKJ-BLK-L", new BigDecimal("18000"), null, 8, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-GKJ-GRY-L", new BigDecimal("18000"), null, 7, Map.of("Colour", "Grey", "Size", "L"), true),
//                        new VariantConfig("KP-GKJ-NVY-XL", new BigDecimal("18000"), null, 5, Map.of("Colour", "Navy", "Size", "XL"), true)
//                )
//        );
//
//        // 11. OLDER STOCK — similar style, not on current invoice
//        createProductWithVariants(
//                "Klinpikk Cargo Trousers",
//                "klinpikk-cargo-trousers",
//                joggersTrousers, BRAND_SLUG,
//                new BigDecimal("17000"), null, BigDecimal.ZERO, true,
//                "Full-length utility cargo trousers from an earlier Klinpikk in-house run, kept in stock alongside the newer Grigo Kimo joggers.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1758267928064-f159a683385d?w=800&q=80"
//                }, null),
//                List.of("trousers", "cargo", "streetwear", "older-stock"),
//                Map.of("Material", "Cotton Twill", "Fit", "Straight", "Pockets", "Cargo"),
//                Map.of("Colour", List.of("Khaki", "Black"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-CRG-KHK-M", new BigDecimal("17000"), null, 6, Map.of("Colour", "Khaki", "Size", "M"), true),
//                        new VariantConfig("KP-CRG-KHK-L", new BigDecimal("17000"), null, 5, Map.of("Colour", "Khaki", "Size", "L"), true),
//                        new VariantConfig("KP-CRG-BLK-XL", new BigDecimal("17000"), null, 3, Map.of("Colour", "Black", "Size", "XL"), true)
//                )
//        );
//    }
//
//    // ----------------------------------------------------------------
//    // HOODIES & JACKETS
//    // ----------------------------------------------------------------
//
//    private void seedHoodiesAndJackets() {
//
//        // 8. FROM INVOICE — "16 Packs of Hoodie (Unbranded)"
//        createProductWithVariants(
//                "Klinpikk Plain Hoodie (Unbranded)",
//                "klinpikk-plain-hoodie-unbranded",
//                hoodiesJackets, BRAND_SLUG,
//                new BigDecimal("10000"), null, BigDecimal.ZERO, true,
//                "Blank fleece pullover hoodie, cut and sewn in-house with no branding — a plain canvas ready for custom print or embroidery orders.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1607860087860-c46e865f6ab0?w=800&q=80"
//                }, null),
//                List.of("hoodie", "unbranded", "blank", "invoice-001754"),
//                Map.of("Material", "Cotton Fleece", "Fit", "Regular", "Branding", "None"),
//                Map.of("Colour", List.of("Black", "Grey", "Navy"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-HOD-UNB-BLK-M", new BigDecimal("10000"), null, 16, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-HOD-UNB-BLK-L", new BigDecimal("10000"), null, 14, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-HOD-UNB-GRY-L", new BigDecimal("10000"), null, 12, Map.of("Colour", "Grey", "Size", "L"), true),
//                        new VariantConfig("KP-HOD-UNB-NVY-XL", new BigDecimal("10000"), null, 8, Map.of("Colour", "Navy", "Size", "XL"), true)
//                )
//        );
//
//        // 9. FROM INVOICE — "20 Packs of Hoodie (Branded)"
//        createProductWithVariants(
//                "Klinpikk Logo Hoodie (Branded)",
//                "klinpikk-logo-hoodie-branded",
//                hoodiesJackets, BRAND_SLUG,
//                new BigDecimal("12000"), null, BigDecimal.ZERO, true,
//                "Fleece pullover hoodie with the Klinpikk logo printed on the chest, cut and sewn in-house.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1680292783974-a9a336c10366?w=800&q=80"
//                }, null),
//                List.of("hoodie", "branded", "klinpikk-logo", "invoice-001754"),
//                Map.of("Material", "Cotton Fleece", "Fit", "Regular", "Branding", "Klinpikk Chest Print"),
//                Map.of("Colour", List.of("Black", "Grey", "Navy"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-HOD-BRD-BLK-M", new BigDecimal("12000"), null, 20, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-HOD-BRD-BLK-L", new BigDecimal("12000"), null, 17, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-HOD-BRD-GRY-L", new BigDecimal("12000"), null, 14, Map.of("Colour", "Grey", "Size", "L"), true),
//                        new VariantConfig("KP-HOD-BRD-NVY-XL", new BigDecimal("12000"), null, 10, Map.of("Colour", "Navy", "Size", "XL"), true)
//                )
//        );
//
//        // 12. OLDER STOCK — similar style, not on current invoice
//        createProductWithVariants(
//                "Klinpikk Zip-Up Hoodie",
//                "klinpikk-zip-up-hoodie",
//                hoodiesJackets, BRAND_SLUG,
//                new BigDecimal("19000"), null, BigDecimal.ZERO, true,
//                "Full-zip fleece hoodie from an earlier Klinpikk in-house run, kept in stock alongside the newer pullover styles.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1590759483822-b2fee5aa6bd3?w=800&q=80"
//                }, null),
//                List.of("hoodie", "zip-up", "streetwear", "older-stock"),
//                Map.of("Material", "Cotton Fleece", "Fit", "Regular", "Closure", "Full Zip"),
//                Map.of("Colour", List.of("Black", "Grey"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-ZIP-BLK-M", new BigDecimal("19000"), null, 5, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-ZIP-BLK-L", new BigDecimal("19000"), null, 4, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-ZIP-GRY-XL", new BigDecimal("19000"), null, 3, Map.of("Colour", "Grey", "Size", "XL"), true)
//                )
//        );
//
//        // 13. OLDER STOCK — similar style, not on current invoice
//        createProductWithVariants(
//                "Klinpikk Track Jacket",
//                "klinpikk-track-jacket",
//                hoodiesJackets, BRAND_SLUG,
//                new BigDecimal("22000"), null, BigDecimal.ZERO, true,
//                "Lightweight full-zip track jacket from an earlier Klinpikk in-house run, kept in stock as a layering piece alongside the hoodies.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1610392462690-84766bd687ca?w=800&q=80"
//                }, null),
//                List.of("jacket", "track-jacket", "streetwear", "older-stock"),
//                Map.of("Material", "Polyester Tricot", "Fit", "Regular", "Closure", "Full Zip"),
//                Map.of("Colour", List.of("Black", "Navy"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-TRK-BLK-M", new BigDecimal("22000"), null, 4, Map.of("Colour", "Black", "Size", "M"), true),
//                        new VariantConfig("KP-TRK-BLK-L", new BigDecimal("22000"), null, 3, Map.of("Colour", "Black", "Size", "L"), true),
//                        new VariantConfig("KP-TRK-NVY-XL", new BigDecimal("22000"), null, 2, Map.of("Colour", "Navy", "Size", "XL"), true)
//                )
//        );
//    }
//
//    // ----------------------------------------------------------------
//    // JERSEYS
//    // ----------------------------------------------------------------
//
//    private void seedJerseys() {
//
//        // 5. FROM INVOICE — "Bundle of klinSport Jersey"
//        createProductWithVariants(
//                "Klinpikk KlinSport Jersey",
//                "klinpikk-klinsport-jersey",
//                jerseys, BRAND_SLUG,
//                new BigDecimal("20000"), null, BigDecimal.ZERO, true,
//                "Breathable mesh sports jersey from the KlinSport line, cut and sewn in-house for training or everyday wear.",
//                mediaGallery(new String[]{
//                        "https://images.unsplash.com/photo-1655089131279-8029e8a21ac6?w=800&q=80"
//                }, null),
//                List.of("jersey", "klinsport", "sportswear", "invoice-001754"),
//                Map.of("Material", "Mesh Polyester", "Fit", "Athletic", "Series", "KlinSport"),
//                Map.of("Colour", List.of("Blue/Red", "Black/White", "Green/Gold"), "Size", List.of("M", "L", "XL")),
//                List.of(
//                        new VariantConfig("KP-KSP-BLR-M", new BigDecimal("20000"), null, 8, Map.of("Colour", "Blue/Red", "Size", "M"), true),
//                        new VariantConfig("KP-KSP-BLR-L", new BigDecimal("20000"), null, 7, Map.of("Colour", "Blue/Red", "Size", "L"), true),
//                        new VariantConfig("KP-KSP-BLW-L", new BigDecimal("20000"), null, 6, Map.of("Colour", "Black/White", "Size", "L"), true),
//                        new VariantConfig("KP-KSP-GRG-XL", new BigDecimal("20000"), null, 4, Map.of("Colour", "Green/Gold", "Size", "XL"), true)
//                )
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