package semicolon.africa.waylchub.repository.orderRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String userId);

    Page<Order> findByCustomerId(String customerId, Pageable pageable);
    List<Order> findByOrderStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoff);


    // Useful for admin dashboards
    Page<Order> findByOrderStatus(OrderStatus status, Pageable pageable);

}