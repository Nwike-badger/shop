package semicolon.africa.waylchub.model.order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentDetails {
    private String cardNumberLastFour; // For display/logging, not full card number
    private String cardType; // e.g., "Visa", "Mastercard"
    private String transactionId; // This would come from the payment gateway
    private String gatewayResponseCode; // e.g., "00" for success
    private String gatewayResponseMessage; // e.g., "Approved"
}
