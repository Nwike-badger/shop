package semicolon.africa.waylchub.dto.paymentDto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PaymentInitRequest {
    private String orderId; // Your internal order ID
    private BigDecimal amount;
    private String customerEmail;
    private String customerName;
    private String paymentDescription;
}
