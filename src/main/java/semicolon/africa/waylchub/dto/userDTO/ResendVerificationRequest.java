package semicolon.africa.waylchub.dto.userDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendVerificationRequest {
    @Email
    @NotBlank
    private String email;
}