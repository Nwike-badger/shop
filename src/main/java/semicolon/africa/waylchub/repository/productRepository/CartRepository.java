package semicolon.africa.waylchub.repository.productRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.product.Cart;

import java.util.Optional;

public interface CartRepository extends MongoRepository<Cart, String> {
    Optional<Cart> findByUserId(String userId);
    Optional<Cart> findBySessionId(String sessionId);

    // Efficiently check if either exists
    Optional<Cart> findByUserIdOrSessionId(String userId, String sessionId);

    void deleteBySessionId(String sessionId);
}