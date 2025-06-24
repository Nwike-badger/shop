package semicolon.africa.waylchub.repository.productRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import semicolon.africa.waylchub.model.product.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {
    Optional<Product> findByNameAndCategory(String name, String category);

    @Query("{ 'variants.sku' : ?0 }")
    Optional<Product> findByVariants_Sku(String sku);

    List<Product> findByCategory(String category);
    List<Product> findBySubCategory(String subCategory);
}