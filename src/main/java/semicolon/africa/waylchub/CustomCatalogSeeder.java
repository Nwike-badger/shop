//package semicolon.africa.waylchub.seeder;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//import semicolon.africa.waylchub.model.customOrder.CustomCategory;
//import semicolon.africa.waylchub.model.customOrder.CustomStyle;
//import semicolon.africa.waylchub.repository.customOrderRepository.CustomCategoryRepository;
//import semicolon.africa.waylchub.repository.customOrderRepository.CustomStyleRepository;
//
//import java.math.BigDecimal;
//import java.util.List;
//
///**
// * Seeds the custom_categories and custom_styles collections with the same
// * 11 archetypes the original frontend had hardcoded — but with real Unsplash
// * images for the cover and per-style photos.
// *
// *  IMPORTANT: this seeder is NON-DESTRUCTIVE.
// *  - If custom_categories already has rows, the WHOLE seeder is a no-op.
// *  - If a category exists but its styles don't, only the missing styles are inserted.
// *  - Once admin starts editing, this seeder must NOT undo their work.
// *
// *  To re-seed from scratch: drop both collections in MongoDB Atlas and restart.
// *
// *  Why CommandLineRunner not @PostConstruct: CommandLineRunner runs after the
// *  full Spring context is up, so the repositories are guaranteed available.
// *  @PostConstruct can fire before MongoDB connection is ready.
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class CustomCatalogSeeder implements CommandLineRunner {
//
//    private final CustomCategoryRepository categoryRepository;
//    private final CustomStyleRepository styleRepository;
//
//    @Override
//    public void run(String... args) {
//        // ── DEMO MODE — always wipe and reseed ───────────────────────────────────
//        // Intentionally destructive so the catalog always matches this file.
//        //
//        // When going live, do ONE of:
//        //   a) Comment out the 2 deleteAll() lines and uncomment the guard below
//        //   b) Add @Profile("dev") to the @Component annotation on this class
//        //   c) Comment out the entire @Component annotation
//        // ────────────────────────────────────────────────────────────────────
//
//        log.info("[CustomCatalogSeeder] DEMO: wiping collections for fresh seed");
//        styleRepository.deleteAll();
//        categoryRepository.deleteAll();
//
//        // ── Production guard (uncomment when going live) ─────────────────────
//        // long existing = categoryRepository.count();
//        // if (existing > 0) {
//        //     log.info("[CustomCatalogSeeder] Live mode: {} categories exist, skipping", existing);
//        //     return;
//        // }
//        // ────────────────────────────────────────────────────────────────────
//
//        seedCategories();
//        seedAllStyles();
//        log.info("[CustomCatalogSeeder] Done. {} categories, {} styles seeded",
//                categoryRepository.count(), styleRepository.count());
//    }
//
//    // ─── Categories ──────────────────────────────────────────────────────
//
//    private void seedCategories() {
//        List<CustomCategory> categories = List.of(
//                cat("agbada", "Agbada", "Flowing grandeur, ceremonial weight",
//                        "The three-piece statement — outer robe, inner shirt, and trouser. For owambe, weddings, and arrival.",
//                        "men", 35000, "14-21 days", "#0d4d2a", "menFull",
//                        "https://images.unsplash.com/photo-1594465919760-1f8be21f70b8?w=1200&q=80",
//                        10),
//
//                cat("senator", "Senator", "Tailored authority",
//                        "Two-piece long-sleeve top and trouser. The everyday formal for Sundays, offices, and dinners.",
//                        "men", 22000, "7-14 days", "#1a3a52", "menFull",
//                        "https://images.unsplash.com/photo-1617137968427-85924c800a22?w=1200&q=80",
//                        20),
//
//                cat("suit", "Suit", "Sharp Western tailoring",
//                        "Two or three-piece western suit. Single or double-breasted, made for you.",
//                        "men", 45000, "14-21 days", "#1c1c1c", "menFull",
//                        "https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=1200&q=80",
//                        30),
//
//                cat("kaftan", "Kaftan", "Effortless and elevated",
//                        "Long flowing top, often paired with matching trouser. Comfortable enough for home, sharp enough for events.",
//                        "unisex", 18000, "7-10 days", "#7a4419", "unisexUpperLong",
//                        "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?w=1200&q=80",
//                        40),
//
//                cat("shirt", "Shirt", "Built for your shoulders",
//                        "Formal or casual. Native style, mandarin, or full Western collar. Long or short sleeve.",
//                        "unisex", 12000, "5-7 days", "#2c5f7c", "unisexUpperShort",
//                        "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=1200&q=80",
//                        50),
//
//                cat("trouser", "Trouser", "The right break, the right rise",
//                        "Western-cut trousers, native-cut, or cropped. Pleated or flat-front, your call.",
//                        "unisex", 10000, "5-7 days", "#4a3a2a", "unisexLower",
//                        "https://images.unsplash.com/photo-1473966968600-fa801b869a1a?w=1200&q=80",
//                        60),
//
//                cat("dress", "Dress", "Made to your shape",
//                        "Day, evening, or occasion. Ankara prints, lace, plain — long, midi, or mini.",
//                        "women", 18000, "7-14 days", "#7a2848", "womenFull",
//                        "https://images.unsplash.com/photo-1496747611176-843222e1e57c?w=1200&q=80",
//                        70),
//
//                cat("iro-buba", "Iro & Buba", "Heritage, perfectly cut",
//                        "The wrapper and blouse pairing. Traditional elegance with custom embroidery options.",
//                        "women", 25000, "10-14 days", "#5c2d2d", "womenUpperLower",
//                        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200&q=80",
//                        80),
//
//                cat("skirt-blouse", "Skirt & Blouse", "Your two pieces, your way",
//                        "Mix and match. Pencil, A-line, or flared skirt with a blouse cut to your bust.",
//                        "women", 20000, "7-14 days", "#6a3a5a", "womenUpperLower",
//                        "https://images.unsplash.com/photo-1583496661160-fb5218b5f2b9?w=1200&q=80",
//                        90),
//
//                cat("jumpsuit", "Jumpsuit", "One piece, all power",
//                        "Wide-leg, slim, or culotte. Sleeveless, capped, or full-sleeve — fit to your torso and inseam exactly.",
//                        "women", 22000, "10-14 days", "#3a4a6a", "womenFull",
//                        "https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?w=1200&q=80",
//                        100)
//        );
//
//        categoryRepository.saveAll(categories);
//        log.info("[CustomCatalogSeeder] {} categories seeded", categories.size());
//    }
//
//    private CustomCategory cat(String slug, String name, String tagline, String description,
//                               String genderHint, int priceFrom, String leadTime, String accent,
//                               String measurementSet, String coverImageUrl, int sortOrder) {
//        return CustomCategory.builder()
//                .slug(slug)
//                .name(name)
//                .tagline(tagline)
//                .description(description)
//                .genderHint(genderHint)
//                .priceFrom(BigDecimal.valueOf(priceFrom))
//                .leadTime(leadTime)
//                .accent(accent)
//                .measurementSet(measurementSet)
//                .coverImageUrl(coverImageUrl)
//                .sortOrder(sortOrder)
//                .active(true)
//                .build();
//    }
//
//    // ─── Styles ──────────────────────────────────────────────────────────
//
//    private void seedAllStyles() {
//        // 4 styles per category — matches the original gallery size
//        seedStylesFor("agbada", List.of(
//                style("agbada-classic-royal", "Classic Royal", "Cream / Gold embroidery",
//                        "https://images.unsplash.com/photo-1594465919760-1f8be21f70b8?w=800&q=80", 10),
//                style("agbada-modern-slim", "Modern Slim", "Navy / Tonal stitching",
//                        "https://images.unsplash.com/photo-1617127365659-2a08d24e7eb1?w=800&q=80", 20),
//                style("agbada-emir-cut", "Emir Cut", "White / Heavy embroidery",
//                        "https://images.unsplash.com/photo-1610824352934-c10d87b700cc?w=800&q=80", 30),
//                style("agbada-minimal-sheen", "Minimal Sheen", "Black / No embroidery",
//                        "https://images.unsplash.com/photo-1625591341337-13156a92d3b6?w=800&q=80", 40)
//        ));
//
//        seedStylesFor("senator", List.of(
//                style("senator-mandarin", "Mandarin Collar", "Charcoal",
//                        "https://images.unsplash.com/photo-1617137968427-85924c800a22?w=800&q=80", 10),
//                style("senator-piped", "Piped Detail", "Navy / Cream piping",
//                        "https://images.unsplash.com/photo-1593030668930-8130abedd2f8?w=800&q=80", 20),
//                style("senator-buttoned", "Full-Button Front", "Olive",
//                        "https://images.unsplash.com/photo-1559563458-527698bf5295?w=800&q=80", 30),
//                style("senator-short", "Short Sleeve", "Beige",
//                        "https://images.unsplash.com/photo-1611312449412-6cefac5dc3e4?w=800&q=80", 40)
//        ));
//
//        seedStylesFor("suit", List.of(
//                style("suit-2btn", "Two-Button", "Charcoal",
//                        "https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=800&q=80", 10),
//                style("suit-3pc", "Three-Piece", "Navy / Waistcoat",
//                        "https://images.unsplash.com/photo-1593032465175-481ac7f401a0?w=800&q=80", 20),
//                style("suit-double", "Double-Breasted", "Black",
//                        "https://images.unsplash.com/photo-1621072156002-e2fccdc0b176?w=800&q=80", 30),
//                style("suit-summer", "Summer Linen", "Sand",
//                        "https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=800&q=80", 40)
//        ));
//
//        seedStylesFor("kaftan", List.of(
//                style("kaftan-classic", "Classic Long", "Mandarin collar",
//                        "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?w=800&q=80", 10),
//                style("kaftan-vneck", "V-Neck Embroidered", "Chest detail",
//                        "https://images.unsplash.com/photo-1586424919478-baa53b71fa70?w=800&q=80", 20),
//                style("kaftan-knee", "Knee Length", "Casual cut",
//                        "https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&q=80", 30),
//                style("kaftan-cape", "Cape-Style", "Modern silhouette",
//                        "https://images.unsplash.com/photo-1582719188393-bb71ca45dbb9?w=800&q=80", 40)
//        ));
//
//        seedStylesFor("shirt", List.of(
//                style("shirt-classic", "Classic Western", "Pointed collar",
//                        "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&q=80", 10),
//                style("shirt-mandarin", "Mandarin Collar", "No collar fold",
//                        "https://images.unsplash.com/photo-1622445275576-721325763afe?w=800&q=80", 20),
//                style("shirt-native-embroidered", "Native Embroidered", "Chest detail",
//                        "https://images.unsplash.com/photo-1598033129183-c4f50c736f10?w=800&q=80", 30),
//                style("shirt-short-sleeve", "Short Sleeve", "Casual",
//                        "https://images.unsplash.com/photo-1581655353564-df123a1eb820?w=800&q=80", 40)
//        ));
//
//        seedStylesFor("trouser", List.of(
//                style("trouser-flat-front", "Flat Front", "Slim taper",
//                        "https://images.unsplash.com/photo-1473966968600-fa801b869a1a?w=800&q=80", 10),
//                style("trouser-pleated", "Pleated", "Classic break",
//                        "https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&q=80", 20),
//                style("trouser-cropped", "Cropped Ankle", "Modern cut",
//                        "https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?w=800&q=80", 30),
//                style("trouser-wide-leg", "Wide Leg", "Relaxed",
//                        "https://images.unsplash.com/photo-1604176354204-9268737828e4?w=800&q=80", 40)
//        ));
//
//        seedStylesFor("dress", List.of(
//                style("dress-aline-midi", "A-Line Midi", "Flared from waist",
//                        "https://images.unsplash.com/photo-1496747611176-843222e1e57c?w=800&q=80", 10),
//                style("dress-bodycon", "Bodycon", "Fitted throughout",
//                        "https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?w=800&q=80", 20),
//                style("dress-mermaid", "Mermaid", "Fitted then flared",
//                        "https://images.unsplash.com/photo-1539008835657-9e8e9680c956?w=800&q=80", 30),
//                style("dress-empire", "Empire Waist", "Under-bust seam",
//                        "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&q=80", 40)
//        ));
//
//        seedStylesFor("iro-buba", List.of(
//                style("iro-classic-gele", "Classic with Gele", "Aso-oke trim",
//                        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80", 10),
//                style("iro-modern", "Modern Cut", "Tailored buba",
//                        "https://images.unsplash.com/photo-1610030469983-98e550d6193c?w=800&q=80", 20),
//                style("iro-embroidered", "Heavy Embroidery", "Beaded neckline",
//                        "https://images.unsplash.com/photo-1610030181087-540017dc9d61?w=800&q=80", 30),
//                style("iro-pleated", "Pleated Iro", "Structured drape",
//                        "https://images.unsplash.com/photo-1531835551805-16d864c8d311?w=800&q=80", 40)
//        ));
//
//        seedStylesFor("skirt-blouse", List.of(
//                style("skb-pencil", "Pencil Skirt", "Fitted blouse",
//                        "https://images.unsplash.com/photo-1583496661160-fb5218b5f2b9?w=800&q=80", 10),
//                style("skb-aline", "A-Line Skirt", "Peplum top",
//                        "https://images.unsplash.com/photo-1566479179817-c0a4234a4f5f?w=800&q=80", 20),
//                style("skb-flared", "Flared Skirt", "Crop blouse",
//                        "https://images.unsplash.com/photo-1577900232427-18219b9166a0?w=800&q=80", 30),
//                style("skb-midi-offshoulder", "Midi + Off-shoulder", "Elegant",
//                        "https://images.unsplash.com/photo-1568252542512-9fe8fe9c87bb?w=800&q=80", 40)
//        ));
//
//        seedStylesFor("jumpsuit", List.of(
//                style("jumpsuit-wide-leg", "Wide Leg", "Belted waist",
//                        "https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?w=800&q=80", 10),
//                style("jumpsuit-slim", "Slim Leg", "V-neck",
//                        "https://images.unsplash.com/photo-1539008835657-9e8e9680c956?w=800&q=80", 20),
//                style("jumpsuit-culotte", "Culotte", "Cropped",
//                        "https://images.unsplash.com/photo-1586790170083-2f9ceadc732d?w=800&q=80", 30),
//                style("jumpsuit-offshoulder", "Off-Shoulder", "Statement",
//                        "https://images.unsplash.com/photo-1571513722275-4b41940f54b8?w=800&q=80", 40)
//        ));
//    }
//
//    /**
//     * Seeds styles for a category only if none exist yet under that category.
//     * Lets admin add new styles without the seeder later overwriting them.
//     */
//    private void seedStylesFor(String categorySlug, List<CustomStyle> styles) {
//        long existing = styleRepository.countByCategorySlug(categorySlug);
//        if (existing > 0) {
//            log.info("[CustomCatalogSeeder] Skipping styles for {} — {} already exist",
//                    categorySlug, existing);
//            return;
//        }
//        styles.forEach(s -> s.setCategorySlug(categorySlug));
//        styleRepository.saveAll(styles);
//        log.info("[CustomCatalogSeeder] {} styles seeded for {}", styles.size(), categorySlug);
//    }
//
//    private CustomStyle style(String slug, String name, String tone, String imageUrl, int sortOrder) {
//        return CustomStyle.builder()
//                .slug(slug)
//                .name(name)
//                .tone(tone)
//                .imageUrl(imageUrl)
//                .sortOrder(sortOrder)
//                .active(true)
//                .build();
//    }
//}