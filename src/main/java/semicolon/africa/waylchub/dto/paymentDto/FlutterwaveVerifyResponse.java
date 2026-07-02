package semicolon.africa.waylchub.dto.paymentDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlutterwaveVerifyResponse {
    private String status;    // "success" | "error"
    private String message;
    private Data data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private Long id;
        @JsonProperty("tx_ref")   private String txRef;
        @JsonProperty("flw_ref")  private String flwRef;
        private Double amount;
        private String currency;
        private String status;         // "successful" | "pending" | "failed"
        @JsonProperty("payment_type") private String paymentType;
    }
}