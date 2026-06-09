package semicolon.africa.waylchub.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaystackWebhookPayload {

    private String event;   // "charge.success"
    private EventData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventData {
        private String reference;   // ← this is our orderId (we set it on init)
        private String status;      // "success"
        private Long   amount;      // in kobo
        private String channel;     // "card", "bank", etc.

        @JsonProperty("gateway_response")
        private String gatewayResponse;
    }
}