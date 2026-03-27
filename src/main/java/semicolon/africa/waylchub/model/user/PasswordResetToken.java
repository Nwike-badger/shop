package semicolon.africa.waylchub.model.user;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    private String id;

    // Store only the BCrypt hash of the raw token — never the raw value
    private String tokenHash;

    @Indexed
    private String userId;

    @Indexed
    private String email;

    private LocalDateTime expiresAt;

    private boolean used;
}