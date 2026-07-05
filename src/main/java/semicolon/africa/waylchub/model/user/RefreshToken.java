package semicolon.africa.waylchub.model.user;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String tokenHash;      // SHA-256 of the raw token; raw is never stored

    @Indexed
    private String userId;

    // TTL cleanup: Mongo auto-deletes this document 7 days after createdAt.
    // Keep "7d" in sync with application.security.jwt.refresh-token-expiration (604800s).
    // NOTE: expireAfter = "0s" would silently create a broken, non-TTL index — that's why
    // the lifetime is expressed as a positive duration on the creation timestamp instead.
    @Indexed(expireAfter = "7d")
    private Instant createdAt;

    // Authoritative expiry for the immediate app-level check in refreshToken().
    // Mongo's TTL monitor only sweeps ~every 60s, so this ensures an expired-but-not-yet-
    // deleted token is still rejected on the spot.
    private Instant expiresAt;
}