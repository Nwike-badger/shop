package semicolon.africa.waylchub.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaystackVerifyResponse {

    private boolean status;      // API-call success (NOT the transaction status)
    private String message;
    private Data data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String status;      // TRANSACTION status: "success" | "failed" | "abandoned"
        private String reference;    // our orderId
        private BigDecimal amount;   // in KOBO — divide by 100 for NGN
        private String channel;      // "card" | "bank" | "ussd" | "bank_transfer" ...
        private String currency;
        private String gateway_response;
    }
}