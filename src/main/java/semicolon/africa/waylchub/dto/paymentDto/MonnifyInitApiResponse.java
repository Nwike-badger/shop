package semicolon.africa.waylchub.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Typed response for POST /api/v1/merchant/transactions/init-payment
 * Replaces the raw Map that caused silent NPEs on unexpected shapes.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonnifyInitApiResponse {

    private boolean requestSuccessful;
    private String responseMessage;
    private String responseCode;
    private InitBody responseBody;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitBody {
        private String transactionReference;
        private String paymentReference;
        private String merchantName;
        private String apiKey;
        private String checkoutUrl;
        private String status;
    }
}