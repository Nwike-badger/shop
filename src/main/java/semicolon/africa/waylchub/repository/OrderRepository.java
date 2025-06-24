package semicolon.africa.waylchub.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.order.Order;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByUserId(String userId);
}
