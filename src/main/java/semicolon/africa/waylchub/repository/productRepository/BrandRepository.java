package semicolon.africa.waylchub.repository.productRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.product.Brand;

import java.util.Optional;

public interface BrandRepository extends MongoRepository<Brand, String> {
    Optional<Brand> findBySlug(String slug);
    Optional<Brand> findByNameIgnoreCase(String name);
}
