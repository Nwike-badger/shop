package semicolon.africa.waylchub.repository.orderRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import semicolon.africa.waylchub.model.order.Order;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String userId);

    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    // Useful for admin dashboards
    Page<Order> findByOrderStatus(String orderStatus, Pageable pageable);
}