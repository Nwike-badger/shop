package semicolon.africa.waylchub.model.recommendation;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Raw behavioral event log. Every meaningful user interaction with a product
 * is written here asynchronously (non-blocking to the caller).
 *
 * INDEXES:
 *   - (userId, eventType, timestamp) → user history queries
 *   - (productId, eventType, timestamp) → product popularity queries
 *   - (sessionId, timestamp) → anonymous session analytics
 *   - timestamp (TTL) → auto-expire VIEW/SEARCH events after 90 days
 *                        PURCHASE events should NOT expire — set ttl = null for those.
 *
 * TTL NOTE: MongoDB TTL only supports a single expiry per collection.
 * Strategy: store a calculated `expiresAt` field and use a TTL index on it.
 * PURCHASE events set expiresAt = null → MongoDB TTL ignores null values.
 */
@Document(collection = "user_behavior_logs")
@CompoundIndexes({
        @CompoundIndex(def = "{'userId': 1, 'eventType': 1, 'timestamp': -1}", name = "idx_user_event_time"),
        @CompoundIndex(def = "{'productId': 1, 'eventType': 1, 'timestamp': -1}", name = "idx_product_event_time"),
        @CompoundIndex(def = "{'sessionId': 1, 'timestamp': -1}", name = "idx_session_time"),
        // Composite index for collaborative filtering query: "find all users who also interacted with productId"
        @CompoundIndex(def = "{'productId': 1, 'userId': 1, 'eventType': 1}", name = "idx_product_user_event")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorLog {

    @Id
    private String id;

    /**
     * Authenticated user ID — null for anonymous sessions.
     * Recommendations for anonymous users fall back to session-based or popularity-based.
     */
    private String userId;

    /**
     * Browser/device session ID — always present (set by frontend).
     * Allows basic behavioral tracking even before login.
     */
    private String sessionId;

    private String productId;
    private String variantId;     // optional — only for ADD_TO_CART / PURCHASE
    private String categoryId;    // denormalized for fast category-popularity queries
    private String categorySlug;
    private String brandId;       // denormalized for brand-affinity queries

    private BehaviorEventType eventType;

    /**
     * For SEARCH events, store the query that led here.
     * Used for: search analytics, synonym suggestion, zero-result detection.
     */
    private String searchQuery;

    /**
     * For PURCHASE events, the actual amount paid.
     * Used for: revenue-weighted collaborative filtering.
     */
    private Double purchaseAmount;

    private LocalDateTime timestamp;

    /**
     * TTL field. MongoDB will delete this document when expiresAt < now.
     * Set to null for PURCHASE events to retain them permanently.
     */
    @Indexed(expireAfterSeconds = 0)
    private LocalDateTime expiresAt;

    // ── Event type taxonomy ───────────────────────────────────────────────────
    public enum BehaviorEventType {
        VIEW,           // Product detail page opened (weight: 1)
        SEARCH,         // User searched and this product appeared / was clicked (weight: 2)
        ADD_TO_CART,    // Added to cart (weight: 5)
        REMOVE_FROM_CART,
        WISHLIST,       // Saved to wishlist (weight: 3)
        PURCHASE        // Completed purchase (weight: 10) — never expires
    }

    /** Weights used when computing item-item similarity scores. */
    public static int eventWeight(BehaviorEventType type) {
        return switch (type) {
            case VIEW           -> 1;
            case SEARCH         -> 2;
            case WISHLIST       -> 3;
            case ADD_TO_CART    -> 5;
            case REMOVE_FROM_CART -> -1; // negative signal
            case PURCHASE       -> 10;
        };
    }
}