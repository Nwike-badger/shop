package semicolon.africa.waylchub.service.orderService;

import semicolon.africa.waylchub.dto.orderDto.OrderRequestDTO;
import semicolon.africa.waylchub.dto.orderDto.OrderResponseDTO;

import java.util.List;

public interface OrderService {
    OrderResponseDTO placeOrder(OrderRequestDTO request);
    List<OrderResponseDTO> getOrdersByUser(String userId);
    List<OrderResponseDTO> getAllOrders(); // for admin
    OrderResponseDTO getOrderById(String orderId);
    OrderResponseDTO updateOrderStatus(String orderId, String newStatus); // optional
}
