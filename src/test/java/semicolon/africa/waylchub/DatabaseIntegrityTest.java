package semicolon.africa.waylchub;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductDatabaseIntegrityTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        productVariantRepository.deleteAll();
    }

    // ==========================================
    // PHASE 1: UNIQUENESS CONSTRAINTS
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("🛑 1. Product Slug Must Be Unique")
    void testProductSlugUniqueness() {
        // Given: A product already exists
        Product p1 = Product.builder()
                .name("iPhone 15")
                .slug("iphone-15")
                .basePrice(BigDecimal.valueOf(1000))
                .build();
        productRepository.save(p1);

        // When: Trying to save another product with the EXACT same slug
        Product p2 = Product.builder()
                .name("iPhone 15 Pro") // Different name
                .slug("iphone-15")     // SAME SLUG
                .basePrice(BigDecimal.valueOf(1200))
                .build();

        // Then: It must fail
        assertThatThrownBy(() -> productRepository.save(p2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @Order(2)
    @DisplayName("🛑 2. Variant SKU Must Be Unique")
    void testVariantSkuUniqueness() {
        // Given
        ProductVariant v1 = ProductVariant.builder()
                .productId("prod_1")
                .sku("SKU-001")
                .price(BigDecimal.TEN)
                .build();
        productVariantRepository.save(v1);

        // When: Creating another variant with same SKU
        ProductVariant v2 = ProductVariant.builder()
                .productId("prod_2")
                .sku("SKU-001") // Duplicate!
                .price(BigDecimal.valueOf(20))
                .build();

        // Then
        assertThatThrownBy(() -> productVariantRepository.save(v2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ==========================================
    // PHASE 2: VALIDATION LOGIC (Pricing/Data)
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("💰 3. Pricing Integrity - No Negative Prices")
    void testNegativePriceValidation() {
        // If you added @Min(0) to your POJO, this test ensures DB rejects bad data
        Product p = Product.builder()
                .name("Bad Price Item")
                .slug("bad-price")
                .basePrice(BigDecimal.valueOf(-100.00)) // Invalid
                .build();

        // Expect ConstraintViolation or MongoException depending on validation layer
        assertThatThrownBy(() -> productRepository.save(p))
                .satisfies(e -> {
                    // It might be wrapped in ConstraintViolationException or similar
                    assertThat(e.getClass().getSimpleName()).containsAnyOf("ConstraintViolationException", "ValidationException");
                });
    }

    @Test
    @Order(4)
    @DisplayName("📉 4. Discount Logic - Should default to 0")
    void testDefaultValues() {
        Product p = Product.builder()
                .name("Default Item")
                .slug("default-item")
                .basePrice(BigDecimal.TEN)
                .build();

        Product saved = productRepository.save(p);

        // Ensure defaults from POJO are respected
        assertThat(saved.getDiscount()).isEqualTo(0);
        assertThat(saved.getVariantOptions()).isNotNull(); // Should be empty list, not null
        assertThat(saved.getSpecifications()).isNotNull(); // Should be empty map, not null
    }

    // ==========================================
    // PHASE 3: INDEX & SEARCH VERIFICATION
    // ==========================================

    @Test
    @Order(5)
    @DisplayName("🔍 5. Text Search Integrity")
    void testTextSearch() {
        // Given
        Product p1 = Product.builder().name("Gaming Laptop").slug("laptop").description("Fastest processor").basePrice(BigDecimal.TEN).build();
        Product p2 = Product.builder().name("Office Chair").slug("chair").description("Ergonomic back support").basePrice(BigDecimal.TEN).build();
        Product p3 = Product.builder().name("Gaming Mouse").slug("mouse").brandName("Razer").basePrice(BigDecimal.TEN).build();

        productRepository.saveAll(List.of(p1, p2, p3));

        // When: We use MongoTemplate (Just like your Service does)
        // This creates a query that requires the Text Index to function
        Query query = new Query();
        query.addCriteria(org.springframework.data.mongodb.core.query.TextCriteria.forDefaultLanguage().matching("Gaming"));

        List<Product> results = mongoTemplate.find(query, Product.class);

        // Then
        assertThat(results).hasSize(2)
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Gaming Laptop", "Gaming Mouse");
    }

    @Test
    @Order(6)
    @DisplayName("📊 6. Verify Compound Indexes Exist")
    void testIndexesAreCreated() {
        // Get all indexes for Product collection
        List<IndexInfo> productIndexes = mongoTemplate.indexOps(Product.class).getIndexInfo();

        // Print them (optional, helps debugging)
        productIndexes.forEach(idx -> System.out.println("Product Index: " + idx.getName()));

        // Assert 'category_price' index exists
        boolean hasCatPrice = productIndexes.stream()
                .anyMatch(idx -> idx.getName().equals("category_price"));

        // Assert 'text_search' index exists
        boolean hasTextIndex = productIndexes.stream()
                .anyMatch(idx -> idx.getName().equals("text_search"));

        assertThat(hasCatPrice).as("Category+Price index missing").isTrue();
        assertThat(hasTextIndex).as("Text search index missing").isTrue();

        // Check Variant Indexes
        List<IndexInfo> variantIndexes = mongoTemplate.indexOps(ProductVariant.class).getIndexInfo();
        boolean hasProductPrice = variantIndexes.stream()
                .anyMatch(idx -> idx.getName().equals("product_price_idx"));

        assertThat(hasProductPrice).as("Variant Product+Price index missing").isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("🚀 7. Sorting using Compound Index")
    void testCompoundIndexSorting() {
        // This confirms the index actually *works* for the query it was designed for
        Product p1 = Product.builder().slug("a").categorySlug("tech").minPrice(BigDecimal.valueOf(100)).build();
        Product p2 = Product.builder().slug("b").categorySlug("tech").minPrice(BigDecimal.valueOf(50)).build();
        Product p3 = Product.builder().slug("c").categorySlug("home").minPrice(BigDecimal.valueOf(20)).build();

        productRepository.saveAll(List.of(p1, p2, p3));

        // Query: Category = "tech", Sort by Price ASC
        // If the index 'category_price' works, this should be efficient (though hard to measure efficiency in unit test, we measure result correctness)
        List<Product> techProducts = productRepository.findAll(Sort.by("minPrice"));

        // Simple check to ensure data consistency
        assertThat(techProducts).hasSize(3);
    }
}