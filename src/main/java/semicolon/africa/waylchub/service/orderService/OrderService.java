package semicolon.africa.waylchub.service.orderService;

import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse placeOrder(OrderRequest request, String userId);
//    List<OrderResponse> getOrdersByUser(String userId);
//    List<OrderResponse> getAllOrders(); // for admin
//    OrderResponse getOrderById(String orderId);
//    OrderResponse updateOrderStatus(String orderId, String newStatus);
}
