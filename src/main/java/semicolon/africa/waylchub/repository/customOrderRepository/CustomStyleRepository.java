package semicolon.africa.waylchub.repository.customOrderRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import semicolon.africa.waylchub.model.customOrder.CustomStyle;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Same safe-derived-query approach as CustomCategoryRepository.
 * "categorySlug" and "active" are both safe field names — no keyword overlap.
 * "sortOrder" is NOT used in derived query names. Sorting done in Java.
 */
@Repository
public interface CustomStyleRepository extends MongoRepository<CustomStyle, String> {

    Optional<CustomStyle> findBySlug(String slug);

    /** Active styles for a category — both field names are keyword-safe. */
    List<CustomStyle> findByCategorySlugAndActiveTrue(String categorySlug);
    List<CustomStyle> findByCategorySlugInAndActiveTrue(Set<String> categorySlugs);

    /** All styles for a category (admin) — categorySlug is keyword-safe. */
    List<CustomStyle> findByCategorySlug(String categorySlug);

    long countByCategorySlug(String categorySlug);

    void deleteByCategorySlug(String categorySlug);

    boolean existsBySlug(String slug);
}