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

    // TTL cleanup: Mongo auto-deletes this document 30 days after createdAt.
    // Keep "30d" in sync with application.security.jwt.refresh-token-expiration (2592000s).
    // Changing this annotation does NOT retroactively update an existing index in
    // MongoDB — run the collMod command below once after deploying.
    @Indexed(expireAfter = "30d")
    private Instant createdAt;

    // Authoritative expiry for the immediate app-level check in refreshToken().
    private Instant expiresAt;

    // Reuse detection: rotation marks the token used instead of deleting it
    // outright, so a near-simultaneous re-presentation (concurrent requests
    // racing, not theft) can be tolerated for a short grace window instead of
    // forcing a logout. A token reused outside that window revokes every
    // session for the user.
    private boolean used;
    private Instant usedAt;
}