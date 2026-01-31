package semicolon.africa.waylchub;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.dto.productDto.ProductAttributeRequest;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.model.product.Brand;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.ProductImage;
import semicolon.africa.waylchub.repository.productRepository.BrandRepository;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private CategoryRepository categoryRepo;
    @Autowired private BrandRepository brandRepo;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepo;

    private final Random random = new Random();

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🌱 STARTING NIGERIAN MARKET SEEDER...");

        // 1. CLEANUP
        productRepo.deleteAll();
        categoryRepo.deleteAll();
        brandRepo.deleteAll();

        // 2. CREATE LOCAL BRANDS
        List<String> brands = Arrays.asList(
                "Aba Textile Mills", "Lagos Tailors", "Zaria Leathers",
                "Adire World", "Arewa Cottons", "Naija Feet", "Urban Vibes"
        );
        for (String b : brands) {
            Brand brand = new Brand();
            brand.setName(b);
            brand.setSlug(b.toLowerCase().replace(" ", "-"));
            brandRepo.save(brand);
        }

        // ==========================================
        // 3. CATEGORY HIERARCHY (The "Shelf" Setup)
        // ==========================================

        // --- MEN'S FASHION ---
        Category men = saveCat("Men's Fashion", "men-fashion", null, "https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?auto=format&fit=crop&w=400", true, 1);

        List<Category> menLeaves = List.of(
                saveCat("Native Wear", "men-native", men, "https://images.unsplash.com/photo-1590117591724-8aa470b227e3?auto=format&fit=crop&w=400", true, null),
                saveCat("Senator Suits", "senator-suits", men, "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400", false, null),
                saveCat("Men's Shirts", "men-shirts", men, "https://images.unsplash.com/photo-1620799140408-edc6dcb6d633?auto=format&fit=crop&w=400", true, null)
        );

        // --- WOMEN'S FASHION ---
        Category women = saveCat("Women's Fashion", "women-fashion", null, "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?auto=format&fit=crop&w=400", true, 2);

        List<Category> womenLeaves = List.of(
                saveCat("Ankara Dresses", "ankara-dresses", women, "https://images.unsplash.com/photo-1627485937980-221c88ac04a5?auto=format&fit=crop&w=400", true, null),
                saveCat("Jumpsuits", "women-jumpsuits", women, "https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?auto=format&fit=crop&w=400", true, null),
                saveCat("Bags & Purses", "women-bags", women, "https://images.unsplash.com/photo-1594223274512-ad4803739b7c?auto=format&fit=crop&w=400", true, null),
                saveCat("Lingerie & Underwear", "women-underwear", women, "https://images.unsplash.com/photo-1582533561751-ef6f6ab93a2e?auto=format&fit=crop&w=400", false, null)
        );

        // --- FOOTWEAR (UNISEX) ---
        Category footwear = saveCat("Footwear", "footwear", null, "https://images.unsplash.com/photo-1603487759182-23b8859ed625?auto=format&fit=crop&w=400", true, 3);

        List<Category> footwearLeaves = List.of(
                saveCat("Palms & Slippers", "palms", footwear, "https://images.unsplash.com/photo-1593032457869-12d9b06c47cc?auto=format&fit=crop&w=400", true, null),
                saveCat("Crocs & Rubber Slides", "crocs-slides", footwear, "https://images.unsplash.com/photo-1603487759182-23b8859ed625?auto=format&fit=crop&w=400", true, null),
                saveCat("Handmade Shoes", "handmade-shoes", footwear, "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=400", false, null)
        );

        // --- CHILDREN ---
        Category kids = saveCat("Children", "children-fashion", null, "https://images.unsplash.com/photo-1621452773781-0f992fd0f5d9?auto=format&fit=crop&w=400", true, 4);

        List<Category> kidsLeaves = List.of(
                saveCat("Boys Native", "boys-native", kids, "https://images.unsplash.com/photo-1471286174890-9c808743015a?auto=format&fit=crop&w=400", true, null),
                saveCat("Girls Dresses", "girls-dresses", kids, "https://images.unsplash.com/photo-1518831959646-742c3a14ebf7?auto=format&fit=crop&w=400", true, null)
        );


        // ==========================================
        // 4. POPULATE PRODUCTS (The "Stocking")
        // ==========================================
        System.out.println("📦 Stocking Shelves...");

        // Populate Men
        createSpecificProduct(menLeaves.get(0), "Royal Blue Aso-Oke Agbada", "Aba Textile Mills", 85000);
        createSpecificProduct(menLeaves.get(0), "White Senator Native Set", "Lagos Tailors", 45000);
        createSpecificProduct(menLeaves.get(2), "Crisp White Cotton Shirt", "Arewa Cottons", 15000);

        // Populate Women
        createSpecificProduct(womenLeaves.get(0), "Luxury Adire Maxi Dress", "Adire World", 55000);
        createSpecificProduct(womenLeaves.get(0), "Ankara Peplum Top", "Lagos Tailors", 12500);
        createSpecificProduct(womenLeaves.get(1), "Floral Print Jumpsuit", "Urban Vibes", 22000);
        createSpecificProduct(womenLeaves.get(2), "Snake Skin Leather Tote", "Zaria Leathers", 45000);
        createSpecificProduct(womenLeaves.get(3), "Cotton Comfort Panties (Set of 3)", "Arewa Cottons", 8500);

        // Populate Footwear
        createSpecificProduct(footwearLeaves.get(0), "Brown Leather Palms", "Naija Feet", 18000);
        createSpecificProduct(footwearLeaves.get(0), "Black Velvet Slippers", "Naija Feet", 20000);
        createSpecificProduct(footwearLeaves.get(1), "Comfort Rubber Slides (Crocs Style)", "Urban Vibes", 12000);

        // Populate Kids
        createSpecificProduct(kidsLeaves.get(0), "Boys Kaftan Set", "Lagos Tailors", 18000);
        createSpecificProduct(kidsLeaves.get(1), "Princess Ball Gown", "Adire World", 25000);

        // Fill up the rest with random data to make it look full
        generateRandomProducts(menLeaves, brands);
        generateRandomProducts(womenLeaves, brands);
        generateRandomProducts(footwearLeaves, brands);

        System.out.println("✅ SEEDING COMPLETE!");
    }


    // ==========================================
    // HELPERS
    // ==========================================

    private Category saveCat(String name, String slug, Category parent, String imageUrl, boolean featured, Integer order) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setParent(parent);
        c.setImageUrl(imageUrl);
        c.setFeatured(featured);
        c.setDisplayOrder(order);

        if (parent != null) {
            String parentLineage = parent.getLineage() == null ? "," : parent.getLineage();
            c.setLineage(parentLineage + parent.getId() + ",");
        } else {
            c.setLineage(",");
        }
        return categoryRepo.save(c);
    }

    private void createSpecificProduct(Category category, String name, String brand, double price) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        // Robust slug generation
        req.setSlug(name.toLowerCase().replace(" ", "-") + "-" + random.nextInt(1000));
        req.setPrice(new BigDecimal(price));
        req.setCategorySlug(category.getSlug());
        req.setBrandSlug(brand.toLowerCase().replace(" ", "-"));
        req.setStockQuantity(10 + random.nextInt(50));
        req.setSku("SKU-" + random.nextInt(99999));

        // Use category image as product image for consistency + a secondary image
        List<ProductImage> images = new ArrayList<>();
        images.add(new ProductImage(category.getImageUrl(), true));
        images.add(new ProductImage("https://via.placeholder.com/600x600?text=Detail+View", false));
        req.setImages(images);

        req.setAttributes(Arrays.asList(
                new ProductAttributeRequest("Material", "Local Fabric"),
                new ProductAttributeRequest("Made In", "Nigeria")
        ));

        // Use the SERVICE to save (this ensures validation logic runs)
        productService.addOrUpdateProduct(req);
    }

    private void generateRandomProducts(List<Category> categories, List<String> brands) {
        for (Category cat : categories) {
            // Add 2 extra random products per category
            for (int i = 1; i <= 2; i++) {
                String brand = brands.get(random.nextInt(brands.size()));
                String name = brand + " " + cat.getName() + " Special " + i;
                double price = 10000 + random.nextInt(50000);
                createSpecificProduct(cat, name, brand, price);
            }
        }
    }
}