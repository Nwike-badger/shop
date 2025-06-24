package semicolon.africa.waylchub.service.orderService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.orderDto.OrderRequestDTO;
import semicolon.africa.waylchub.dto.orderDto.OrderResponseDTO;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.mapper.OrderMapper;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.model.order.PaymentStatus;
import semicolon.africa.waylchub.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    public OrderResponseDTO placeOrder(OrderRequestDTO request) {
        BigDecimal totalAmount = request.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(request.getUserId())
                .items(request.getItems())
                .shippingAddress(request.getShippingAddress())
                .totalAmount(totalAmount)
                .orderStatus(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Order saved = orderRepository.save(order);
        return OrderMapper.toResponseDTO(saved);
    }

    @Override
    public List<OrderResponseDTO> getOrdersByUser(String userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public OrderResponseDTO getOrderById(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return OrderMapper.toResponseDTO(order);
    }

    @Override
    public OrderResponseDTO updateOrderStatus(String orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setOrderStatus(OrderStatus.valueOf(newStatus.toUpperCase()));
        order.setUpdatedAt(LocalDateTime.now());

        Order updated = orderRepository.save(order);
        return OrderMapper.toResponseDTO(updated);
    }
}
