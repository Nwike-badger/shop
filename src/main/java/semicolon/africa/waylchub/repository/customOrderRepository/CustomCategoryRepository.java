package semicolon.africa.waylchub.repository.customOrderRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import semicolon.africa.waylchub.model.customOrder.CustomCategory;

import java.util.List;
import java.util.Optional;

/**
 * Intentionally simple — only uses safe derived query methods.
 *
 * WHY: Spring Data MongoDB mis-tokenises field names that contain reserved
 * keywords. "sortOrder" contains "Order" (a Spring Data keyword), causing
 * runtime PropertyReferenceException even if startup validation passes.
 * "Sort" parameter with @Query is also unreliable across Spring Boot versions.
 *
 * SOLUTION: Fetch all or fetch-by-active-boolean (both 100% safe field names),
 * then sort in Java. The collection has ~11 documents — Java sort is trivially
 * fast and eliminates all query-parsing risk.
 */
@Repository
public interface CustomCategoryRepository extends MongoRepository<CustomCategory, String> {

    Optional<CustomCategory> findBySlug(String slug);

    /**
     * All active categories — "active" is a plain boolean field with no keyword
     * overlap in Spring Data's DSL, so this derived query is completely safe.
     * Sorting is handled in CustomCategoryService.
     */
    List<CustomCategory> findByActiveTrue();

    /**
     * All categories regardless of active state — inherited findAll() is the
     * most reliable possible query. Sorting handled in service.
     */
    // findAll() is inherited from MongoRepository — no override needed.

    boolean existsBySlug(String slug);
}