package semicolon.africa.waylchub.dto.orderDto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.order.OrderItem;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.model.product.Address;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderResponse {
    private String orderId;
    private String userId;
    private List<OrderItem> items;
    private Address shippingAddress;
    private String paymentMethod;
    private BigDecimal totalAmount;
    private OrderStatus orderStatus;
    private LocalDateTime orderDate;
}
