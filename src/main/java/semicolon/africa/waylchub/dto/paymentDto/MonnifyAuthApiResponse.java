package semicolon.africa.waylchub.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Typed response for POST /api/v1/auth/login
 * Replaces the raw Map that caused silent NPEs on unexpected shapes.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonnifyAuthApiResponse {

    private boolean requestSuccessful;
    private String responseMessage;
    private String responseCode;
    private AuthBody responseBody;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthBody {
        private String accessToken;
        private String expiresIn;
    }
}