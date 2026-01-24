package semicolon.africa.waylchub.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.order.Order;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    // This will now work because 'userId' exists in the Order model
    List<Order> findByUserId(String userId);

    // You can also add this if you want to search by email
    List<Order> findByCustomerEmail(String email);
}
