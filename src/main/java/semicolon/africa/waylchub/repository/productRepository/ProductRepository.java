package semicolon.africa.waylchub.repository.productRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.product.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    // Optimized: Find by slug (for Product Details Page)
    Optional<Product> findBySlug(String slug);

    // Optimized: Find by Brand ID
    List<Product> findByBrandId(String brandId);

    // Optimized: Search by Name
    List<Product> findByNameContainingIgnoreCase(String name);
}