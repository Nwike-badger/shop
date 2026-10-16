package semicolon.africa.waylchub;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.dto.productDto.VariantRequest;
import semicolon.africa.waylchub.model.product.*;
import semicolon.africa.waylchub.repository.productRepository.*;
import semicolon.africa.waylchub.service.productService.ProductService;
import semicolon.africa.waylchub.service.storage.StorageService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "exploreaba.seeder.enabled", havingValue = "true")
public class ExploreAbaCatalogSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepo;
    private final BrandRepository brandRepo;
    private final ProductRepository productRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductService productService;
    private final StorageService storage;
    private final ObjectMapper mapper;

    @Value("${exploreaba.images.path}") private String imagesPath;
    @Value("${exploreaba.seeder.wipe:false}") private boolean wipe;
    @Value("${cloudinary.root-folder:exploreaba/products}") private String rootFolder;

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp");

    record BrandEntry(String name, String slug, String description, String logoUrl, String website) {}

    record Entry(String name, String slug, String brand, String category, BigDecimal price, BigDecimal discount,
                 String description, List<String> tags, Map<String, String> specs,
                 List<String> colours, List<String> sizes, String skuPrefix, int stockPerVariant) {}

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🚀 EXPLOREABA CATALOG SEEDER");
        if (wipe) {
            variantRepo.deleteAll();
            productRepo.deleteAll();
            categoryRepo.deleteAll();
            brandRepo.deleteAll();
            System.out.println("🗑️  Database wiped.");
        }

        Path root = Paths.get(imagesPath);
        ensureBrands(root);
        ensureCategories();

        Entry[] entries = mapper.readValue(root.resolve("products.json").toFile(), Entry[].class);

        for (Entry e : entries) {
            if (e.brand() == null || brandRepo.findBySlug(e.brand()).isEmpty()) {
                System.out.println("⚠️  Skipped (unknown brand '" + e.brand() + "'): " + e.slug());
                continue;
            }
            Path dir = root.resolve(e.brand()).resolve(e.slug());
            if (!Files.isDirectory(dir)) {
                System.out.println("⚠️  Skipped (no folder " + dir + "): " + e.slug());
                continue;
            }
            System.out.println("📦 [" + e.brand() + "] " + e.name());
            List<ProductImage> media = uploadImages(e.brand(), e.slug(), dir);
            createProduct(e, media);
        }
        System.out.println("✅ SEEDING COMPLETE — set exploreaba.seeder.enabled=false now.");
    }

    // ---------------- brands & categories ----------------

    private void ensureBrands(Path root) throws IOException {
        BrandEntry[] brands = mapper.readValue(root.resolve("brands.json").toFile(), BrandEntry[].class);
        for (BrandEntry b : brands) {
            Brand brand = brandRepo.findBySlug(b.slug()).orElseGet(Brand::new);
            brand.setName(b.name());
            brand.setSlug(b.slug());
            brand.setDescription(b.description());
            brand.setLogoUrl(b.logoUrl());
            brand.setWebsite(b.website());
            brandRepo.save(brand);
            System.out.println("🏷️  Brand ready: " + b.name());
        }
    }

    private void ensureCategories() {
        Category fashion = ensureCat("Fashion", "fashion", null);
        ensureCat("T-Shirts & Tops", "t-shirts", fashion);
        ensureCat("Shorts", "shorts", fashion);
        ensureCat("Joggers & Trousers", "joggers-trousers", fashion);
        ensureCat("Hoodies & Jackets", "hoodies-jackets", fashion);
        ensureCat("Jerseys", "jerseys", fashion);
    }

    private Category ensureCat(String name, String slug, Category parent) {
        return categoryRepo.findBySlug(slug).orElseGet(() -> {
            Category c = new Category();
            c.setName(name);
            c.setSlug(slug);
            c.setParent(parent);
            c.setLineage(parent != null
                    ? (parent.getLineage() == null ? "," : parent.getLineage()) + parent.getId() + ","
                    : ",");
            return categoryRepo.save(c);
        });
    }

    // ---------------- images ----------------

    private List<ProductImage> uploadImages(String brand, String slug, Path dir) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> IMAGE_EXT.contains(ext(p)))
                    .sorted()
                    .toList();
        }

        String folder = rootFolder + "/" + brand + "/" + slug;
        List<ProductImage> gallery = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            Path f = files.get(i);
            var result = storage.uploadLocalFile(f, folder, baseName(f));
            gallery.add(new ProductImage(result.url(), i == 0, ProductImage.MediaType.IMAGE));
            System.out.println("   ☁️  " + f.getFileName() + " -> " + result.url());
        }
        return gallery;
    }

    private String ext(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.substring(n.lastIndexOf('.') + 1);
    }

    private String baseName(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.lastIndexOf('.'));
    }

    // ---------------- product + variants ----------------

    private void createProduct(Entry e, List<ProductImage> media) {
        // Re-running replaces the product instead of duplicating it
        productRepo.findBySlug(e.slug()).ifPresent(p -> productService.deleteProduct(p.getId()));

        ProductRequest req = new ProductRequest();
        req.setName(e.name());
        req.setSlug(e.slug());
        req.setBasePrice(e.price());
        req.setDiscount(e.discount() == null ? BigDecimal.ZERO : e.discount());
        req.setCategorySlug(e.category());
        req.setBrandSlug(e.brand());
        req.setDescription(e.description());
        req.setImages(media);
        req.setTags(e.tags());
        req.setSpecifications(e.specs());
        req.setVariantOptions(Map.of("Colour", e.colours(), "Size", e.sizes()));
        req.setIsActive(true);
        Product product = productService.createOrUpdateProduct(req);

        for (String colour : e.colours()) {
            for (String size : e.sizes()) {
                VariantRequest v = new VariantRequest();
                v.setProductId(product.getId());
                v.setSku(e.skuPrefix() + "-" + colourCode(colour) + "-" + size);
                v.setPrice(e.price());
                v.setStockQuantity(e.stockPerVariant());
                v.setAttributes(Map.of("Colour", colour, "Size", size));
                v.setImages(media);
                v.setIsActive(true);
                productService.saveVariant(v);
            }
        }
    }

    private String colourCode(String colour) {
        String letters = colour.replaceAll("[^A-Za-z]", "").toUpperCase();
        return letters.substring(0, Math.min(3, letters.length()));
    }
}