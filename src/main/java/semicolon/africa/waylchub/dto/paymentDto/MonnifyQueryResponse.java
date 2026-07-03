package semicolon.africa.waylchub.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonnifyQueryResponse {

    private boolean requestSuccessful;   // API-call success (NOT the transaction status)
    private String responseMessage;
    private String responseCode;
    private ResponseBody responseBody;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseBody {
        private String paymentStatus;          // "PAID" | "PENDING" | "FAILED" | "EXPIRED" | "CANCELLED"
        private String paymentReference;        // our orderId
        private String transactionReference;    // Monnify's own ref (MNFY|...)
        private BigDecimal amountPaid;          // in NGN (Monnify uses major units, not kobo)
        private String paymentMethod;           // "CARD" | "ACCOUNT_TRANSFER" | "USSD"
        private String currencyCode;
    }
}