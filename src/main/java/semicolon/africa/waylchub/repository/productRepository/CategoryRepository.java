package semicolon.africa.waylchub.repository.productRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import semicolon.africa.waylchub.model.product.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends MongoRepository<Category, String> {
    Optional<Category> findBySlug(String slug);

    // Finds immediate children (e.g., find "Blenders" inside "Small Appliances")
    List<Category> findByParentId(String parentId);

    List<Category> findByParentIsNull();

    List<Category> findAllByLineageContaining(String id);
    @Query("{ 'isFeatured': true }")
    List<Category> findFeaturedCategoriesCustom();
}
