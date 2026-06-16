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
@Document(collection = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    private String id;

    // SHA-256 hash of the raw token — never store the raw value
    private String tokenHash;

    @Indexed
    private String userId;

    @Indexed
    private String email;

    private LocalDateTime expiresAt;

    private boolean used;
}