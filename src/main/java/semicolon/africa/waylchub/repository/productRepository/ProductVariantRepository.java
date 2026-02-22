package semicolon.africa.waylchub.repository.productRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.product.ProductVariant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends MongoRepository<ProductVariant, String> {

    // Correct place for SKU search
    Optional<ProductVariant> findBySku(String sku);

    List<ProductVariant> findByProductId(String productId);

    List<ProductVariant> findByIdIn(Collection<String> ids);
}