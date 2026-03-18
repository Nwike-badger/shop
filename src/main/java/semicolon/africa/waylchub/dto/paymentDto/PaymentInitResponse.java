package semicolon.africa.waylchub.dto.paymentDto;

import lombok.Builder;
import lombok.Data;



@Data
@Builder
public class PaymentInitResponse {
    private String checkoutUrl;
    private String transactionReference;
    private String paymentReference; // This is your orderId
}