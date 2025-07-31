package semicolon.africa.waylchub.mapper;

import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderResponse;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderItem;
import semicolon.africa.waylchub.model.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

public class OrderMapper {

    public static Order toOrder(OrderRequest request, String userId) {
        return Order.builder()
                .userId(userId)
                .items(request.getItems().stream()
                        .map(OrderMapper::toOrderItem)
                        .collect(Collectors.toList()))
                .shippingAddress(request.getShippingAddress())
                .paymentMethod(request.getPaymentMethod())
                .cartSubTotal(request.getCartSubTotal())
                .shippingFee(request.getShippingFee())
                .discountAmount(request.getDiscountAmount())
                .totalAmount(request.getTotalAmount())
                .orderStatus(OrderStatus.PENDING) // Default initial status
                .orderDate(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    public static OrderItem toOrderItem(OrderItemRequest request) {
        return OrderItem.builder()
                .productId(request.getProductId())
                .productName(request.getProductName())
                .imageUrl(request.getImageUrl())
                .quantity(request.getQuantity())
                .priceAtPurchase(request.getPriceAtPurchase())
                .build();
    }

    public static OrderResponse toOrderResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .items(order.getItems())
                .shippingAddress(order.getShippingAddress())
                .paymentMethod(order.getPaymentMethod())
                .totalAmount(order.getTotalAmount())
                .orderStatus(order.getOrderStatus())
                .orderDate(order.getOrderDate())
                .build();
    }
}