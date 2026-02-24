package semicolon.africa.waylchub.dto.orderDto;

import lombok.Builder;
import lombok.Data;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.model.order.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private String orderId;
    private String orderNumber;
    private String customerEmail;
    private BigDecimal grandTotal;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private List<String> itemNames;
}