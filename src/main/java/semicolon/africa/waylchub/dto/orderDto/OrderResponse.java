package semicolon.africa.waylchub.dto.orderDto;

import lombok.Builder;
import lombok.Data;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.model.order.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private String orderId;
    private String orderNumber;
    private String customerEmail;
    private BigDecimal totalAmount;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private LocalDateTime createdAt;
    private List<String> itemNames;

}