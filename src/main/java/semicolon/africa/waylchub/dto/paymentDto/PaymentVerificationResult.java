package semicolon.africa.waylchub.dto.paymentDto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PaymentVerificationResult {
    public enum Status { SUCCESSFUL, PENDING, FAILED, NOT_FOUND }

    private Status status;
    private String orderId;             // your tx_ref / paymentReference
    private String gatewayReference;    // flw_ref / transactionReference
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;       // "card", "banktransfer", "opay", etc.
}