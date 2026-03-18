package semicolon.africa.waylchub.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonnifyWebhookPayload {

    private String eventType;
    private EventData eventData;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventData {
        private String transactionReference;
        private String paymentReference; // This maps to your Order ID
        private String paymentStatus;
        private String amountPaid;
        private String paymentMethod;
        private String currency;
    }
}