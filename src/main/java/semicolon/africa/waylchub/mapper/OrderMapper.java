package semicolon.africa.waylchub.mapper;

import semicolon.africa.waylchub.dto.orderDto.OrderResponseDTO;
import semicolon.africa.waylchub.model.order.Order;

public class OrderMapper {

    public static OrderResponseDTO toResponseDTO(Order order) {
        return OrderResponseDTO.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .items(order.getItems())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
