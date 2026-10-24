package semicolon.africa.waylchub;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.dto.productDto.*;
import semicolon.africa.waylchub.model.product.*;
import semicolon.africa.waylchub.repository.productRepository.*;
import semicolon.africa.waylchub.service.productService.BrandService;
import semicolon.africa.waylchub.service.productService.CategoryService;
import semicolon.africa.waylchub.service.productService.ProductService;
import semicolon.africa.waylchub.service.storage.StorageService;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static semicolon.africa.waylchub.config.CacheConfig.PRODUCTS_LIST_CACHE;
import static semicolon.africa.waylchub.config.CacheConfig.PRODUCT_DETAIL_CACHE;

/**
 * Declarative catalog sync. Reads brands.json, categories.json and products.json
 * from exploreaba.images.path and makes the database match.
 * Upserts by slug; "delete": true removes. Fields left out of an entry are unchanged.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "exploreaba.seeder.enabled", havingValue = "true")
public class ExploreAbaCatalogSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepo;
    private final BrandRepository brandRepo;
    private final ProductRepository productRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final StorageService storage;
    private final ObjectMapper mapper;
    private final MongoTemplate mongoTemplate;
    private final CacheManager cacheManager;

    @Value("${exploreaba.images.path}") private String imagesPath;
    @Value("${cloudinary.root-folder:exploreaba/products}") private String productsFolder;
    @Value("${exploreaba.seeder.only:}") private String only;                 // brands,categories,products
    @Value("${exploreaba.seeder.products:}") private String productsFilter;   // product slugs
    @Value("${exploreaba.seeder.upload-images:false}") private boolean uploadAll;

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp");

    // ───────────────────────── JSON shapes ─────────────────────────

    record BrandEntry(String name, String slug, String description, String logo, String logoUrl,
                      String website, Boolean delete) {}

    record CategoryEntry(String name, String slug, String parent, String description,
                         String image, String imageUrl, Boolean featured, Integer displayOrder,
                         Boolean active, Boolean delete) {}

    record VariantTweak(String colour, String size, BigDecimal price, Integer stock, Boolean active) {}

    record ProductEntry(String name, String slug, String renameTo, String brand, String category,
                        BigDecimal price, BigDecimal discount, BigDecimal compareAtPrice,
                        String description, List<String> tags, Map<String, String> specs,
                        List<String> imageUrls, Boolean reuploadImages,
                        List<String> colours, List<String> sizes,
                        String skuPrefix, Integer stockPerVariant, Boolean resetStock,
                        Boolean pruneVariants, Boolean active,
                        List<VariantTweak> tweaks, Boolean delete) {}

    // ───────────────────────── entry point ─────────────────────────

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🚀 EXPLOREABA CATALOG SYNC");
        Path root = Paths.get(imagesPath);
        Set<String> sections = parse(only);
        Set<String> slugFilter = parse(productsFilter);
        try {
            if (sections.isEmpty() || sections.contains("brands"))     syncBrands(root);
            if (sections.isEmpty() || sections.contains("categories")) syncCategories(root);
            if (sections.isEmpty() || sections.contains("products"))   syncProducts(root, slugFilter);
        } finally {
            clearCaches();
        }
        System.out.println("✅ SYNC COMPLETE — set exploreaba.seeder.enabled=false now.");
    }

    // ───────────────────────── brands ─────────────────────────

    private void syncBrands(Path root) {
        BrandEntry[] list = read(root.resolve("brands.json"), BrandEntry[].class);
        if (list == null) return;
        for (BrandEntry b : list) {
            if (b.slug() == null) continue;
            try {
                Optional<Brand> existing = brandRepo.findBySlug(b.slug());
                if (Boolean.TRUE.equals(b.delete())) {
                    if (existing.isPresent()) {
                        brandService.deleteBrand(b.slug()); // refuses if products still use it
                        System.out.println("🗑️  Deleted brand: " + b.slug());
                    }
                    continue;
                }
                String logo = resolveImage(root, pick(b.logo(), b.logoUrl()), "exploreaba/brands", b.slug());
                BrandRequest r = new BrandRequest();
                r.setSlug(b.slug());
                if (existing.isPresent()) {
                    Brand cur = existing.get();
                    r.setName(pick(b.name(), cur.getName()));
                    r.setDescription(pick(b.description(), cur.getDescription()));
                    r.setLogoUrl(pick(logo, cur.getLogoUrl()));
                    r.setWebsite(pick(b.website(), cur.getWebsite()));
                    Brand saved = brandService.updateBrand(b.slug(), r);
                    // products keep a copy of the brand name — keep it in sync
                    mongoTemplate.updateMulti(
                            new Query(Criteria.where("brandSlug").is(b.slug())),
                            new Update().set("brandName", saved.getName()), Product.class);
                    System.out.println("✏️  Brand updated: " + b.slug());
                } else {
                    r.setName(pick(b.name(), b.slug()));
                    r.setDescription(b.description());
                    r.setLogoUrl(logo);
                    r.setWebsite(b.website());
                    brandService.createBrand(r);
                    System.out.println("🏷️  Brand created: " + b.slug());
                }
            } catch (Exception ex) {
                System.out.println("❌ Brand " + b.slug() + ": " + ex.getMessage());
            }
        }
    }

    // ───────────────────────── categories ─────────────────────────

    private void syncCategories(Path root) {
        CategoryEntry[] list = read(root.resolve("categories.json"), CategoryEntry[].class);
        if (list == null) return;

        List<CategoryEntry> deletions = new ArrayList<>();
        for (CategoryEntry c : list) {
            if (c.slug() == null) continue;
            if (Boolean.TRUE.equals(c.delete())) { deletions.add(c); continue; }
            try {
                Optional<Category> existing = categoryRepo.findBySlug(c.slug());
                String img = resolveImage(root, pick(c.image(), c.imageUrl()), "exploreaba/categories", c.slug());

                CategoryRequest r = new CategoryRequest();
                r.setSlug(c.slug());
                r.setParentSlug(c.parent()); // null = leave alone, "" = make top-level

                Category saved;
                if (existing.isPresent()) {
                    Category cur = existing.get();
                    r.setName(pick(c.name(), cur.getName()));
                    r.setDescription(pick(c.description(), cur.getDescription()));
                    r.setImageUrl(pick(img, cur.getImageUrl()));
                    saved = categoryService.updateCategory(c.slug(), r);
                    System.out.println("✏️  Category updated: " + c.slug());
                } else {
                    r.setName(pick(c.name(), c.slug()));
                    r.setDescription(c.description());
                    r.setImageUrl(img);
                    saved = categoryService.createCategory(r);
                    System.out.println("📁 Category created: " + c.slug());
                }

                boolean dirty = false;
                if (c.featured() != null)     { saved.setFeatured(c.featured());         dirty = true; }
                if (c.displayOrder() != null) { saved.setDisplayOrder(c.displayOrder()); dirty = true; }
                if (c.active() != null)       { saved.setActive(c.active());             dirty = true; }
                if (dirty) saved = categoryRepo.save(saved);

                propagateCategory(saved, 0);
            } catch (Exception ex) {
                System.out.println("❌ Category " + c.slug() + ": " + ex.getMessage());
            }
        }

        Collections.reverse(deletions); // children before parents
        for (CategoryEntry c : deletions) {
            try {
                if (categoryRepo.findBySlug(c.slug()).isEmpty()) continue;
                long used = mongoTemplate.count(
                        new Query(Criteria.where("categorySlug").is(c.slug())), Product.class);
                if (used > 0) {
                    System.out.println("⚠️  Not deleted: category '" + c.slug() + "' still has "
                            + used + " product(s). Move them first.");
                    continue;
                }
                categoryService.deleteCategory(c.slug()); // refuses if it has child categories
                System.out.println("🗑️  Deleted category: " + c.slug());
            } catch (Exception ex) {
                System.out.println("❌ Category delete " + c.slug() + ": " + ex.getMessage());
            }
        }
    }

    /** Keeps products and child categories consistent after a category is renamed or moved. */
    private void propagateCategory(Category cat, int depth) {
        if (depth > 10 || cat.getId() == null) return;
        mongoTemplate.updateMulti(
                new Query(Criteria.where("categorySlug").is(cat.getSlug())),
                new Update().set("categoryName", cat.getName())
                        .set("categoryLineageIds", lineageIds(cat)),
                Product.class);

        String base = (cat.getLineage() == null || cat.getLineage().isBlank()) ? "," : cat.getLineage();
        for (Category child : categoryRepo.findAll()) {
            if (child.getParent() != null && cat.getId().equals(child.getParent().getId())) {
                child.setLineage(base + cat.getId() + ",");
                categoryRepo.save(child);
                propagateCategory(child, depth + 1);
            }
        }
    }

    private List<String> lineageIds(Category cat) {
        List<String> ids = new ArrayList<>();
        if (cat.getLineage() != null) {
            for (String p : cat.getLineage().split(",")) if (!p.isBlank()) ids.add(p.trim());
        }
        if (cat.getId() != null) ids.add(cat.getId());
        return ids;
    }

    // ───────────────────────── products ─────────────────────────

    private void syncProducts(Path root, Set<String> slugFilter) {
        ProductEntry[] list = read(root.resolve("products.json"), ProductEntry[].class);
        if (list == null) return;
        for (ProductEntry e : list) {
            if (e.slug() == null) continue;
            if (!slugFilter.isEmpty() && !slugFilter.contains(e.slug().toLowerCase())) continue;
            try {
                syncProduct(root, e);
            } catch (Exception ex) {
                System.out.println("❌ Product " + e.slug() + ": " + ex.getMessage());
            }
        }
    }

    private void syncProduct(Path root, ProductEntry e) throws IOException {
        Optional<Product> existing = productRepo.findBySlug(e.slug());

        if (Boolean.TRUE.equals(e.delete())) {
            if (existing.isPresent()) {
                productService.deleteProduct(existing.get().getId());
                System.out.println("🗑️  Deleted product: " + e.slug());
            } else {
                System.out.println("ℹ️  Nothing to delete: " + e.slug());
            }
            return;
        }

        String brand = pick(e.brand(), val(existing, Product::getBrandSlug));
        String category = pick(e.category(), val(existing, Product::getCategorySlug));
        if (existing.isEmpty() && (brand == null || category == null)) {
            System.out.println("⚠️  Skipped (new product needs brand and category): " + e.slug());
            return;
        }
        if (brand != null && brandRepo.findBySlug(brand).isEmpty()) {
            System.out.println("⚠️  Skipped (unknown brand '" + brand + "'): " + e.slug());
            return;
        }
        if (category != null && categoryRepo.findBySlug(category).isEmpty()) {
            System.out.println("⚠️  Skipped (unknown category '" + category + "'): " + e.slug());
            return;
        }
        BigDecimal price = pick(e.price(), originalPrice(existing));
        if (price == null) {
            System.out.println("⚠️  Skipped (no price): " + e.slug());
            return;
        }

        BigDecimal discount = e.discount() != null ? e.discount()
                : existing.map(p -> BigDecimal.valueOf(discountOf(p))).orElse(BigDecimal.ZERO);

        // "was" price shown without a discount (only meaningful when no discount is active)
        BigDecimal manualCompare = null;
        if (discount.signum() <= 0) {
            BigDecimal oldCompare = (existing.isPresent() && discountOf(existing.get()) == 0)
                    ? existing.get().getCompareAtPrice() : null;
            manualCompare = pick(e.compareAtPrice(), oldCompare);
        }

        // slug rename
        String finalSlug = e.slug();
        if (e.renameTo() != null && !e.renameTo().isBlank() && !e.renameTo().equals(e.slug())) {
            if (existing.isEmpty()) {
                System.out.println("⚠️  renameTo ignored (product doesn't exist yet): " + e.slug());
            } else if (productRepo.findBySlug(e.renameTo()).isPresent()) {
                System.out.println("⚠️  Skipped rename: slug '" + e.renameTo() + "' already exists");
                return;
            } else {
                finalSlug = e.renameTo();
            }
        }

        // ── images
        List<ProductImage> media = val(existing, Product::getImages);
        boolean newImages = false;
        if (e.imageUrls() != null) {
            media = urlGallery(e.imageUrls());
            newImages = true;
        } else if (brand != null) {
            Path dir = root.resolve(brand).resolve(e.slug());
            boolean have = media != null && !media.isEmpty();
            boolean wantUpload = !have || uploadAll || Boolean.TRUE.equals(e.reuploadImages());
            if (Files.isDirectory(dir) && wantUpload) {
                List<ProductImage> up = uploadFolder(brand, e.slug(), dir);
                if (!up.isEmpty()) { media = up; newImages = true; }
            }
        }
        if (media == null) media = new ArrayList<>();

        // ── colours / sizes (JSON wins, otherwise what the product already has)
        List<String> colours = e.colours() != null ? e.colours() : existingOption(existing, "Colour");
        List<String> sizes = e.sizes() != null ? e.sizes() : existingOption(existing, "Size");
        Map<String, List<String>> options = new LinkedHashMap<>();
        if (!colours.isEmpty()) options.put("Colour", colours);
        if (!sizes.isEmpty()) options.put("Size", sizes);

        ProductRequest req = new ProductRequest();
        existing.ifPresent(p -> req.setId(p.getId()));
        req.setName(pick(e.name(), pick(val(existing, Product::getName), e.slug())));
        req.setSlug(finalSlug);
        req.setBasePrice(price);
        req.setDiscount(discount);
        req.setCompareAtPrice(manualCompare);
        req.setCategorySlug(category);
        req.setBrandSlug(brand);
        req.setDescription(pick(e.description(), val(existing, Product::getDescription)));
        req.setSpecifications(pick(e.specs(), val(existing, Product::getSpecifications)));
        req.setImages(media);
        req.setTags(e.tags()); // null = leave existing tags alone
        req.setVariantOptions(options);
        req.setIsActive(e.active() != null ? e.active() : (existing.isPresent() ? null : Boolean.TRUE));

        Product product = productService.createOrUpdateProduct(req);
        System.out.println((existing.isPresent() ? "✏️  Product updated: " : "📦 Product created: ")
                + finalSlug + (finalSlug.equals(e.slug()) ? "" : "  (renamed from " + e.slug() + ")"));

        syncVariants(e, product, colours, sizes, media, newImages, price, discount, manualCompare);
    }

    private void syncVariants(ProductEntry e, Product product, List<String> colours, List<String> sizes,
                              List<ProductImage> media, boolean newImages,
                              BigDecimal price, BigDecimal discount, BigDecimal manualCompare) {

        List<ProductVariant> current = variantRepo.findByProductId(product.getId());
        Map<String, String> codes = colourCodes(colours);
        int dims = (colours.isEmpty() ? 0 : 1) + (sizes.isEmpty() ? 0 : 1);
        String prefix = e.skuPrefix() != null ? e.skuPrefix()
                : derivePrefix(current, Math.max(dims, 1), e.slug());
        int defaultStock = e.stockPerVariant() != null ? e.stockPerVariant() : 0;
        List<Map<String, String>> desired = new ArrayList<>();

        List<String> colourLoop = colours.isEmpty() ? Collections.singletonList(null) : colours;
        List<String> sizeLoop = sizes.isEmpty() ? Collections.singletonList(null) : sizes;

        int saved = 0, skipped = 0;
        for (String colour : colourLoop) {
            for (String size : sizeLoop) {
                Map<String, String> attrs = new LinkedHashMap<>();
                if (colour != null) attrs.put("Colour", colour);
                if (size != null) attrs.put("Size", size);
                desired.add(attrs);

                ProductVariant match = current.stream()
                        .filter(v -> attrs.equals(v.getAttributes()))
                        .findFirst().orElse(null);

                // apply tweaks in order; later ones win
                BigDecimal tPrice = null; Integer tStock = null; Boolean tActive = null;
                if (e.tweaks() != null) {
                    for (VariantTweak t : e.tweaks()) {
                        boolean colourOk = t.colour() == null || t.colour().equalsIgnoreCase(colour);
                        boolean sizeOk = t.size() == null || t.size().equalsIgnoreCase(size);
                        if (!colourOk || !sizeOk) continue;
                        if (t.price() != null) tPrice = t.price();
                        if (t.stock() != null) tStock = t.stock();
                        if (t.active() != null) tActive = t.active();
                    }
                }

                BigDecimal original = tPrice != null ? tPrice : price;
                BigDecimal vPrice = afterDiscount(original, discount);
                BigDecimal vCompare = discount.signum() > 0 ? original : manualCompare;

                int existingStock = match != null && match.getStockQuantity() != null ? match.getStockQuantity() : 0;
                int stock = tStock != null ? tStock
                        : (match == null || Boolean.TRUE.equals(e.resetStock())) ? defaultStock : existingStock;

                String sku;
                if (match != null && e.skuPrefix() == null) {
                    sku = match.getSku();
                } else {
                    StringBuilder sb = new StringBuilder(prefix);
                    if (colour != null) sb.append('-').append(codes.get(colour));
                    if (size != null) sb.append('-').append(size.replaceAll("[^A-Za-z0-9]", "").toUpperCase());
                    if (colour == null && size == null) sb.append("-DEFAULT");
                    sku = sb.toString();
                }

                boolean unchanged = match != null && !newImages && tActive == null
                        && sku.equals(match.getSku())
                        && sameNumber(match.getPrice(), vPrice)
                        && sameNumber(match.getCompareAtPrice(), vCompare)
                        && stock == existingStock;
                if (unchanged) { skipped++; continue; }

                VariantRequest v = new VariantRequest();
                if (match != null) v.setId(match.getId());
                v.setProductId(product.getId());
                v.setSku(sku);
                v.setPrice(vPrice);
                v.setCompareAtPrice(vCompare);
                v.setStockQuantity(stock);
                v.setAttributes(attrs);
                v.setImages((match == null || newImages) ? media : match.getImages());
                v.setIsActive(tActive != null ? tActive : (match == null ? Boolean.TRUE : null));
                productService.saveVariant(v);
                saved++;
            }
        }

        long stale = current.stream()
                .filter(v -> desired.stream().noneMatch(d -> d.equals(v.getAttributes())))
                .count();
        if (stale > 0) {
            if (Boolean.TRUE.equals(e.pruneVariants())) {
                for (ProductVariant v : current) {
                    if (desired.stream().noneMatch(d -> d.equals(v.getAttributes()))) {
                        productService.deleteVariant(v.getId());
                        System.out.println("   🗑️  Removed variant " + v.getSku());
                    }
                }
            } else {
                System.out.println("   ⚠️  " + stale + " variant(s) no longer match colours/sizes. "
                        + "Add \"pruneVariants\": true to remove them.");
            }
        }

        productService.updateParentAggregates(product.getId());
        System.out.println("   variants: " + saved + " saved, " + skipped + " unchanged");
    }

    // ───────────────────────── helpers ─────────────────────────

    private List<ProductImage> uploadFolder(String brand, String slug, Path dir) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> IMAGE_EXT.contains(ext(p)))
                    .sorted().toList();
        }
        String folder = productsFolder + "/" + brand + "/" + slug;
        List<ProductImage> gallery = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            Path f = files.get(i);
            var result = storage.uploadLocalFile(f, folder, baseName(f));
            gallery.add(new ProductImage(result.url(), i == 0, ProductImage.MediaType.IMAGE));
            System.out.println("   ☁️  " + f.getFileName() + " -> " + result.url());
        }
        return gallery;
    }

    private List<ProductImage> urlGallery(List<String> urls) {
        List<ProductImage> gallery = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            String u = urls.get(i).trim();
            boolean video = u.matches("(?i).*\\.(mp4|webm|mov)$")
                    || u.contains("youtube.com") || u.contains("youtu.be") || u.contains("vimeo.com");
            gallery.add(new ProductImage(u, i == 0,
                    video ? ProductImage.MediaType.VIDEO : ProductImage.MediaType.IMAGE));
        }
        return gallery;
    }

    /** value can be a full http(s) URL or a path relative to the products root. */
    private String resolveImage(Path root, String value, String cloudFolder, String publicId) {
        if (value == null || value.isBlank()) return null;
        if (value.startsWith("http")) return value;
        Path file = root.resolve(value);
        if (!Files.isRegularFile(file)) {
            System.out.println("⚠️  Image file not found: " + file);
            return null;
        }
        return storage.uploadLocalFile(file, cloudFolder, publicId).url();
    }

    private <T> T read(Path file, Class<T> type) {
        if (!Files.exists(file)) {
            System.out.println("ℹ️  No " + file.getFileName() + " — skipping.");
            return null;
        }
        try {
            return mapper.readValue(file.toFile(), type);
        } catch (IOException ex) {
            System.out.println("❌ Could not read " + file.getFileName() + " (check the JSON): " + ex.getMessage());
            return null;
        }
    }

    private static int discountOf(Product p) {
        return p.getDiscount() == null ? 0 : p.getDiscount();
    }

    private BigDecimal originalPrice(Optional<Product> p) {
        if (p.isEmpty()) return null;
        Product x = p.get();
        return (discountOf(x) > 0 && x.getCompareAtPrice() != null) ? x.getCompareAtPrice() : x.getBasePrice();
    }

    private List<String> existingOption(Optional<Product> p, String name) {
        List<String> out = new ArrayList<>();
        if (p.isEmpty() || p.get().getVariantOptions() == null) return out;
        for (VariantOption o : p.get().getVariantOptions()) {
            if (name.equalsIgnoreCase(o.getName()) && o.getValues() != null) {
                out.addAll(o.getValues());
                break;
            }
        }
        return out;
    }

    /** Reuses the product's existing SKU prefix so new variants look like the old ones. */
    private String derivePrefix(List<ProductVariant> current, int strip, String slug) {
        for (ProductVariant v : current) {
            if (v.getSku() == null) continue;
            String[] parts = v.getSku().split("-");
            if (parts.length > strip) {
                return String.join("-", Arrays.copyOf(parts, parts.length - strip));
            }
        }
        return slug.toUpperCase();
    }

    /** First 3 letters of each colour; lengthened or numbered if two collide (e.g. Grey / Green). */
    private Map<String, String> colourCodes(List<String> colours) {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String c : colours) {
            String letters = c.replaceAll("[^A-Za-z]", "").toUpperCase();
            int len = Math.min(3, letters.length());
            String code = letters.substring(0, len);
            while (used.contains(code) && len < letters.length()) { len++; code = letters.substring(0, len); }
            String unique = code;
            int n = 2;
            while (used.contains(unique)) unique = code + n++;
            used.add(unique);
            out.put(c, unique);
        }
        return out;
    }

    private static BigDecimal afterDiscount(BigDecimal p, BigDecimal d) {
        if (d == null || d.signum() <= 0) return p;
        return p.multiply(BigDecimal.valueOf(100).subtract(d))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static boolean sameNumber(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    private static <T> T pick(T a, T b) { return a != null ? a : b; }

    private static <T> T val(Optional<Product> p, Function<Product, T> f) { return p.map(f).orElse(null); }

    private static Set<String> parse(String csv) {
        Set<String> s = new LinkedHashSet<>();
        if (csv != null) for (String p : csv.split(",")) if (!p.isBlank()) s.add(p.trim().toLowerCase());
        return s;
    }

    private String ext(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.substring(n.lastIndexOf('.') + 1);
    }

    private String baseName(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.lastIndexOf('.'));
    }

    private void clearCaches() {
        Set<String> names = new LinkedHashSet<>(cacheManager.getCacheNames());
        names.addAll(List.of("categoryTree", "featuredCategories", "brands",
                PRODUCT_DETAIL_CACHE, PRODUCTS_LIST_CACHE));
        for (String name : names) {
            try {
                Cache c = cacheManager.getCache(name);
                if (c == null) {
                    System.out.println("ℹ️  Cache '" + name + "' is not registered — skipped.");
                    continue;
                }
                c.clear();
                System.out.println("🧹 Cleared cache: " + name);
            } catch (Exception ex) {
                System.out.println("⚠️  Could not clear cache '" + name + "': " + ex.getMessage());
            }
        }
    }
}