package semicolon.africa.waylchub.model.recommendation;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Aggregated popularity scores per product, refreshed every 15 minutes
 * by RecommendationScheduler.
 *
 * WHY A SEPARATE COLLECTION vs. storing on Product:
 *   Updating a popularity score on every product view would create a massive
 *   write-amplification problem (every page view → product document update →
 *   cache invalidation). A separate collection lets us batch-aggregate every
 *   15 minutes without touching the core Product collection.
 *
 * SCORING FORMULA:
 *   popularityScore = (views_24h * 1) + (carts_24h * 5) + (purchases_24h * 10)
 *                    + (views_7d * 0.5) + (purchases_7d * 3)
 *   This gives a recency-weighted "heat" score.
 *
 * INDEX STRATEGY:
 *   - (categoryId, popularityScore DESC) → "trending in category X"
 *   - (popularityScore DESC) → "globally trending"
 */
@Document(collection = "product_popularity")
@CompoundIndexes({
        @CompoundIndex(def = "{'categoryId': 1, 'popularityScore': -1}", name = "idx_cat_popularity"),
        @CompoundIndex(def = "{'brandId': 1, 'popularityScore': -1}",    name = "idx_brand_popularity"),
        @CompoundIndex(def = "{'popularityScore': -1}",                  name = "idx_global_popularity")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPopularity {

    @Id
    private String productId;   // same as product ID — one document per product

    private String categoryId;
    private String categorySlug;
    private String brandId;

    // ── Rolling window counters ───────────────────────────────────────────────
    private long views24h;
    private long carts24h;
    private long purchases24h;
    private long wishlists24h;

    private long views7d;
    private long carts7d;
    private long purchases7d;

    /**
     * Final blended score — used for ordering in recommendation queries.
     * Recomputed by RecommendationScheduler#refreshPopularityScores().
     */
    private double popularityScore;

    @LastModifiedDate
    private LocalDateTime lastRefreshed;
}