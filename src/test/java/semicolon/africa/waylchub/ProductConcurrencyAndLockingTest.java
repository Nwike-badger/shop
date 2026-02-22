package semicolon.africa.waylchub;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductConcurrencyAndLockingTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("🔒 11. Optimistic Locking: Concurrent Updates Should Fail")
    void testOptimisticLocking() {
        // 1. Setup: Create a product (Version starts at 0 or null)
        Product p = Product.builder()
                .name("Lock Test Product")
                .slug("lock-test")
                .basePrice(BigDecimal.TEN)
                .build();
        Product saved = productRepository.save(p);

        // 2. Simulate User A and User B reading the *same* data
        Product userA_Copy = productRepository.findById(saved.getId()).orElseThrow();
        Product userB_Copy = productRepository.findById(saved.getId()).orElseThrow();

        // Check they have same version
        assertThat(userA_Copy.getVersion()).isEqualTo(userB_Copy.getVersion());

        // 3. User A updates and saves first
        userA_Copy.setBasePrice(BigDecimal.valueOf(20));
        productRepository.save(userA_Copy); // ✅ Success! Version increments in DB.

        // 4. User B tries to update their *stale* copy
        userB_Copy.setBasePrice(BigDecimal.valueOf(30));

        // 5. Expect Failure!
        // The DB sees userB_Copy.version (0) != current DB version (1)
        assertThatThrownBy(() -> productRepository.save(userB_Copy))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
