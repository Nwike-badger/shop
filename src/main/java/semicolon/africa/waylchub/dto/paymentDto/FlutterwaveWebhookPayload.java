package semicolon.africa.waylchub.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlutterwaveWebhookPayload {

    private String event;      // "charge.completed"
    private EventData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventData {
        private Long id;               // Flutterwave's own transaction id

        @JsonProperty("tx_ref")
        private String txRef;          // ← this is your Order ID (we set it on init)

        @JsonProperty("flw_ref")
        private String flwRef;

        private String amount;
        private String currency;
        private String status;         // "successful" | "failed"

        @JsonProperty("payment_type")
        private String paymentType;    // ← ADD: "card" | "banktransfer" | "ussd" | "account"
    }
}