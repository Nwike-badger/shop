package semicolon.africa.waylchub.dto.userDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {
    @NotBlank(message = "Google token is required")
    private String token;


    private String guestId;
}