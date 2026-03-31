package semicolon.africa.waylchub.model.recommendation;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pre-computed item-item similarity store.
 *
 * For each product, we store the N most similar products, ranked by score.
 * This is computed by RecommendationScheduler every hour from UserBehaviorLogs.
 *
 * WHY PRE-COMPUTE vs. ON-THE-FLY:
 *   On-the-fly collaborative filtering at query time requires scanning
 *   millions of behavior log rows and computing cosine similarity in real time.
 *   That's O(n²) and cannot serve sub-100ms responses at scale.
 *
 *   Pre-computing converts a hard O(n²) problem into a simple O(1) lookup:
 *   "give me the 10 most similar products to X" → single findById.
 *
 * The tradeoff is staleness (up to 1 hour lag), which is perfectly acceptable
 * for an e-commerce recommendation feature.
 *
 * INDEX: compound unique on (sourceProductId, type) so we can have separate
 * similarity lists for CO_VIEW vs CO_PURCHASE.
 */
@Document(collection = "item_similarities")
@CompoundIndex(def = "{'sourceProductId': 1, 'type': 1}", unique = true, name = "idx_source_type")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSimilarity {

    @Id
    private String id;

    /** The product we are computing neighbors for. */
    private String sourceProductId;

    /** Which signal was used to compute this similarity. */
    private SimilarityType type;

    /** Top-N similar products, ordered by score descending. */
    private List<SimilarEntry> similar;

    @LastModifiedDate
    private LocalDateTime computedAt;

    // ── Nested types ─────────────────────────────────────────────────────────

    public enum SimilarityType {
        CO_VIEW,       // "customers who viewed this also viewed"
        CO_PURCHASE,   // "customers who bought this also bought"
        CO_CART,       // "customers who carted this also carted"
        CONTENT        // content-based: same category/brand/tag overlap score
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarEntry {
        private String productId;

        /**
         * Normalized cosine similarity score [0.0 → 1.0].
         * score = coOccurrences(A,B) / sqrt(interactions(A) × interactions(B))
         * This is the standard item-item collaborative filtering formula.
         */
        private double score;

        /** Raw co-occurrence count — kept for debugging and score recalibration. */
        private long coOccurrenceCount;
    }
}