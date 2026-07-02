package semicolon.africa.waylchub.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlutterwaveInitApiResponse {

    private String status;   // "success" | "error" (a string here, not a boolean like Paystack)
    private String message;
    private InitData data;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitData {
        private String link;   // ← hosted checkout URL, redirect the customer here
    }
}