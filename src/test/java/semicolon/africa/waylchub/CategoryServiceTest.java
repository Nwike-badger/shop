package semicolon.africa.waylchub;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import semicolon.africa.waylchub.dto.productDto.CategoryTreeResponse;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.service.productService.CategoryService;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CategoryServiceTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.0"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired
    private CategoryService categoryService;

    @SpyBean
    private CategoryRepository categoryRepository;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // 1. Wipe DB
        categoryRepository.deleteAll();
        // 2. Wipe Cache (Crucial for test isolation)
        Objects.requireNonNull(cacheManager.getCache("categoryTree")).clear();
    }

    @Test
    @Order(1)
    @DisplayName("✅ 1. Create Root Category")
    void testCreateRootCategory() {
        Category saved = createCategory("Electronics", "electronics", null);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getParent()).isNull();
    }

    @Test
    @Order(2)
    @DisplayName("✅ 2. Create Child Category")
    void testCreateChildCategory() {
        Category parent = createCategory("Electronics", "electronics", null);
        Category child = createCategory("Phones", "phones", "electronics");

        assertThat(child.getParent().getId()).isEqualTo(parent.getId());

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();
        assertThat(tree).isNotEmpty();
        // Accessing index 0 safely now because cache was cleared
        assertThat(tree.get(0).getChildren()).isNotEmpty();
        assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("Phones");
    }

    @Test
    @Order(3)
    @DisplayName("✅ 3. Deep Hierarchy")
    void testDeepHierarchy() {
        createCategory("Electronics", "electronics", null);
        createCategory("Phones", "phones", "electronics");
        createCategory("Android", "android", "phones");

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();

        // This chain requires the cache to have been rebuilt correctly
        assertThat(tree.get(0).getChildren().get(0).getChildren().get(0).getName()).isEqualTo("Android");
    }

    @Test
    @Order(4)
    @DisplayName("✅ 4. Tree Performance (Cached)")
    void testTreePerformance() {
        createCategory("Root", "root", null);
        for (int i = 0; i < 10; i++) {
            createCategory("Child_" + i, "child-" + i, "root");
        }

        Mockito.reset(categoryRepository);

        // 1st Call: Misses Cache -> Hits DB (1 call) -> Puts in Cache
        categoryService.getCategoryTree();
        Mockito.verify(categoryRepository, Mockito.times(1)).findAll();

        // 2nd Call: Hits Cache -> Skips DB (0 calls)
        categoryService.getCategoryTree();
        // Total DB calls should still be 1
        Mockito.verify(categoryRepository, Mockito.times(1)).findAll();
    }

    @Test
    @Order(5)
    @DisplayName("✅ 5. Slug Uniqueness")
    void testSlugUniqueness() {
        createCategory("Electronics", "electronics", null);
        assertThatThrownBy(() -> createCategory("Duplicate", "electronics", null))
                .isInstanceOf(DuplicateKeyException.class);
    }


    private Category createCategory(String name, String slug, String parentSlug) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setActive(true);

        if (parentSlug != null) {
            Category parent = categoryRepository.findBySlug(parentSlug).orElseThrow();
            category.setParent(parent);
            // Manually set lineage for valid object state, though service handles it in prod
            String parentLineage = parent.getLineage();
            String prefix = (parentLineage == null || parentLineage.isEmpty()) ? "," : parentLineage;
            category.setLineage(prefix + parent.getId() + ",");
        } else {
            category.setLineage(",");
        }

        // CRITICAL CHANGE: Calling the Service method triggers @CacheEvict
        // Calling repository.save() directly bypasses the cache logic!
        return categoryService.createCategory(category);
    }
}