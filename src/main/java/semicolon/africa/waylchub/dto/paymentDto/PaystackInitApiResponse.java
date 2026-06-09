package semicolon.africa.waylchub.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaystackInitApiResponse {

    private boolean status;
    private String message;
    private InitData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitData {
        @JsonProperty("authorization_url")
        private String authorizationUrl;   // ← this is the checkout URL we redirect to

        @JsonProperty("access_code")
        private String accessCode;

        private String reference;          // ← we set this to orderId on init
    }
}