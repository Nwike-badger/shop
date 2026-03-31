package semicolon.africa.waylchub.service.recommendation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.recommendation.ItemSimilarity;
import semicolon.africa.waylchub.model.recommendation.ItemSimilarity.SimilarEntry;
import semicolon.africa.waylchub.model.recommendation.ItemSimilarity.SimilarityType;
import semicolon.africa.waylchub.model.recommendation.ProductPopularity;
import semicolon.africa.waylchub.model.recommendation.UserBehaviorLog;
import semicolon.africa.waylchub.model.recommendation.UserBehaviorLog.BehaviorEventType;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.recommendationRepository.ItemSimilarityRepository;
import semicolon.africa.waylchub.repository.recommendationRepository.ProductPopularityRepository;
import semicolon.africa.waylchub.repository.recommendationRepository.UserBehaviorRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Background scheduler that keeps the recommendation data fresh.
 *
 * JOB 1 — POPULARITY REFRESH (every 15 minutes)
 *   Aggregates behavior counts per product for 24h and 7d windows.
 *   Computes blended popularity score.
 *   Writes to product_popularity collection.
 *   Cost: one MongoDB aggregation query + N upserts.
 *
 * JOB 2 — COLLABORATIVE FILTERING (every hour)
 *   Computes item-item co-occurrence for CO_VIEW and CO_PURCHASE.
 *   Uses the last 30 days of behavior data.
 *   Writes to item_similarities collection.
 *   Cost: O(U × P²) in the worst case, but bounded by MAX_PRODUCTS_PER_JOB
 *   and only processes products with recent activity.
 *
 * SCALABILITY NOTE:
 *   This implementation is correct up to ~100k active products and ~5M
 *   behavior events. Beyond that, move the CF job to a dedicated
 *   offline Spark/Flink pipeline and have it write back to MongoDB.
 *   The RecommendationService read layer does NOT change — only the writer changes.
 *
 * SCHEDULING:
 *   Uses fixedDelay (not fixedRate) so a long-running job cannot stack.
 *   initialDelay gives the application time to start before first run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private final UserBehaviorRepository behaviorRepository;
    private final ItemSimilarityRepository similarityRepository;
    private final ProductPopularityRepository popularityRepository;
    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;

    // How many top neighbors to keep per product (memory/performance tradeoff)
    private static final int TOP_N_SIMILAR = 20;

    // Only process products that had activity in the last N days
    private static final int ACTIVE_WINDOW_DAYS = 30;

    // Max products to process per CF run (safety cap — raise as you scale)
    private static final int MAX_PRODUCTS_PER_CF_RUN = 5_000;

    // =========================================================================
    // JOB 1: POPULARITY REFRESH (every 15 minutes)
    // =========================================================================

    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 30 * 1000)
    public void refreshPopularityScores() {
        log.info("[Popularity] Starting refresh...");
        long start = System.currentTimeMillis();

        try {
            LocalDateTime now    = LocalDateTime.now();
            LocalDateTime ago24h = now.minusHours(24);
            LocalDateTime ago7d  = now.minusDays(7);

            // Aggregate event counts for 24h and 7d windows in two queries
            List<UserBehaviorRepository.EventCount> counts24h =
                    behaviorRepository.countGroupedByProductAndEvent(
                            List.of(BehaviorEventType.VIEW, BehaviorEventType.ADD_TO_CART,
                                    BehaviorEventType.PURCHASE, BehaviorEventType.WISHLIST),
                            ago24h);

            List<UserBehaviorRepository.EventCount> counts7d =
                    behaviorRepository.countGroupedByProductAndEvent(
                            List.of(BehaviorEventType.ADD_TO_CART, BehaviorEventType.PURCHASE),
                            ago7d);

            // Build maps: productId → { eventType → count }
            Map<String, Map<String, Long>> map24h = groupCounts(counts24h);
            Map<String, Map<String, Long>> map7d  = groupCounts(counts7d);

            // Collect all affected product IDs
            Set<String> allProductIds = new HashSet<>();
            allProductIds.addAll(map24h.keySet());
            allProductIds.addAll(map7d.keySet());

            if (allProductIds.isEmpty()) {
                log.info("[Popularity] No activity found, skipping.");
                return;
            }

            // Fetch existing category/brand denormalization from Product (for index fields)
            Map<String, Product> productMeta = productRepository.findAllById(allProductIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));

            // Build and upsert ProductPopularity documents
            List<ProductPopularity> toSave = new ArrayList<>();

            for (String productId : allProductIds) {
                Map<String, Long> e24 = map24h.getOrDefault(productId, Map.of());
                Map<String, Long> e7  = map7d.getOrDefault(productId, Map.of());

                long views24h     = e24.getOrDefault("VIEW",           0L);
                long carts24h     = e24.getOrDefault("ADD_TO_CART",    0L);
                long purchases24h = e24.getOrDefault("PURCHASE",       0L);
                long wishlists24h = e24.getOrDefault("WISHLIST",       0L);
                long carts7d      = e7.getOrDefault("ADD_TO_CART",     0L);
                long purchases7d  = e7.getOrDefault("PURCHASE",        0L);

                double score = (views24h     * 1.0)
                        + (carts24h     * 5.0)
                        + (purchases24h * 10.0)
                        + (wishlists24h * 3.0)
                        + (carts7d      * 2.0)
                        + (purchases7d  * 4.0);

                Product meta = productMeta.get(productId);

                ProductPopularity pop = ProductPopularity.builder()
                        .productId(productId)
                        .categoryId(meta != null && meta.getCategory() != null
                                ? meta.getCategory().getId() : null)
                        .categorySlug(meta != null ? meta.getCategorySlug() : null)
                        .brandId(meta != null && meta.getBrand() != null
                                ? meta.getBrand().getId() : null)
                        .views24h(views24h)
                        .carts24h(carts24h)
                        .purchases24h(purchases24h)
                        .wishlists24h(wishlists24h)
                        .carts7d(carts7d)
                        .purchases7d(purchases7d)
                        .popularityScore(score)
                        .lastRefreshed(now)
                        .build();

                toSave.add(pop);
            }

            // Batch upsert
            for (ProductPopularity pop : toSave) {
                Query q = new Query(Criteria.where("_id").is(pop.getProductId()));
                Update u = new Update()
                        .set("categoryId",     pop.getCategoryId())
                        .set("categorySlug",   pop.getCategorySlug())
                        .set("brandId",        pop.getBrandId())
                        .set("views24h",       pop.getViews24h())
                        .set("carts24h",       pop.getCarts24h())
                        .set("purchases24h",   pop.getPurchases24h())
                        .set("wishlists24h",   pop.getWishlists24h())
                        .set("carts7d",        pop.getCarts7d())
                        .set("purchases7d",    pop.getPurchases7d())
                        .set("popularityScore", pop.getPopularityScore())
                        .set("lastRefreshed",  pop.getLastRefreshed());

                mongoTemplate.upsert(q, u, ProductPopularity.class);
            }

            log.info("[Popularity] Refreshed {} products in {}ms",
                    toSave.size(), System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error("[Popularity] Refresh failed: {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    // JOB 2: COLLABORATIVE FILTERING MATRIX (every hour)
    // =========================================================================

    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void recomputeCollaborativeFiltering() {
        log.info("[CF] Starting item-item collaborative filtering computation...");
        long start = System.currentTimeMillis();

        try {
            // Process CO_PURCHASE (highest quality signal)
            computeCoOccurrence(
                    List.of(BehaviorEventType.PURCHASE),
                    SimilarityType.CO_PURCHASE,
                    ACTIVE_WINDOW_DAYS);

            // Process CO_VIEW (larger volume, slightly noisier)
            computeCoOccurrence(
                    List.of(BehaviorEventType.VIEW, BehaviorEventType.ADD_TO_CART),
                    SimilarityType.CO_VIEW,
                    ACTIVE_WINDOW_DAYS / 2); // shorter window for view signals

            log.info("[CF] Completed in {}ms", System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error("[CF] Computation failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Core item-item collaborative filtering algorithm.
     *
     * ALGORITHM (Item-Item CF — the same approach Amazon patented in 1998):
     *
     *   For each product A:
     *     1. Find all users U who have event(s) with product A in the time window
     *     2. For each user in U:
     *        - Find all other products B they also interacted with
     *        - Increment coOccurrence(A, B)
     *     3. Normalize: score(A, B) = coOccurrence(A,B) / sqrt(interactions(A) × interactions(B))
     *        (This is the standard cosine similarity for implicit feedback)
     *     4. Keep top-N by score, save to ItemSimilarity collection
     *
     * COMPLEXITY: O(P × U_avg × P_avg) where:
     *   P = active products, U_avg = avg users per product, P_avg = avg products per user
     *   In practice this is very fast because we bound by ACTIVE_WINDOW_DAYS.
     */
    private void computeCoOccurrence(
            List<BehaviorEventType> eventTypes,
            SimilarityType similarityType,
            int windowDays) {

        LocalDateTime windowStart = LocalDateTime.now().minusDays(windowDays);

        // Step 1: Find products with recent activity (avoid processing dead products)
        Query activeProductQuery = new Query(
                new Criteria().andOperator(
                        Criteria.where("eventType").in(eventTypes),
                        Criteria.where("timestamp").gte(windowStart)
                )
        );
        activeProductQuery.fields().include("productId");
        activeProductQuery.limit(MAX_PRODUCTS_PER_CF_RUN);

        Set<String> activeProductIds = mongoTemplate
                .find(activeProductQuery, UserBehaviorLog.class).stream()
                .map(UserBehaviorLog::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.info("[CF/{}] Processing {} active products", similarityType, activeProductIds.size());

        // Per-product interaction counts (denominator for cosine similarity)
        Map<String, Long> productInteractionCounts = new HashMap<>();
        for (String productId : activeProductIds) {
            long count = behaviorRepository
                    .findByProductIdAndEventTypeInAndTimestampAfter(productId, eventTypes, windowStart)
                    .stream().map(UserBehaviorLog::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            productInteractionCounts.put(productId, Math.max(count, 1));
        }

        // Step 2 & 3: Build co-occurrence matrix and compute similarities
        for (String productA : activeProductIds) {

            // Find all users who interacted with product A
            Set<String> usersOfA = behaviorRepository
                    .findByProductIdAndEventTypeInAndTimestampAfter(productA, eventTypes, windowStart)
                    .stream()
                    .map(UserBehaviorLog::getUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (usersOfA.isEmpty()) continue;

            // Co-occurrence counter: productB → count of users who also touched A
            Map<String, Long> coOccurrence = new HashMap<>();

            for (String userId : usersOfA) {
                // What else did this user interact with?
                List<String> otherProducts = behaviorRepository
                        .findByUserIdAndEventTypeInAndTimestampAfter(userId, eventTypes, windowStart)
                        .stream()
                        .map(UserBehaviorLog::getProductId)
                        .filter(id -> id != null && !id.equals(productA))
                        .toList();

                for (String productB : otherProducts) {
                    coOccurrence.merge(productB, 1L, Long::sum);
                }
            }

            if (coOccurrence.isEmpty()) continue;

            // Compute cosine similarity scores and keep top N
            long interactionsA = productInteractionCounts.getOrDefault(productA, 1L);

            List<SimilarEntry> topSimilar = coOccurrence.entrySet().stream()
                    .filter(e -> activeProductIds.contains(e.getKey())) // only products we track
                    .map(e -> {
                        long interactionsB = productInteractionCounts.getOrDefault(e.getKey(), 1L);
                        double score = e.getValue() / Math.sqrt((double) interactionsA * interactionsB);
                        return SimilarEntry.builder()
                                .productId(e.getKey())
                                .score(score)
                                .coOccurrenceCount(e.getValue())
                                .build();
                    })
                    .sorted(Comparator.comparingDouble(SimilarEntry::getScore).reversed())
                    .limit(TOP_N_SIMILAR)
                    .toList();

            // Upsert the similarity document for product A
            Query q = new Query(
                    Criteria.where("sourceProductId").is(productA)
                            .and("type").is(similarityType.name())
            );
            Update u = new Update()
                    .set("similar",    topSimilar)
                    .set("computedAt", LocalDateTime.now())
                    .setOnInsert("sourceProductId", productA)
                    .setOnInsert("type", similarityType);

            mongoTemplate.upsert(q, u, ItemSimilarity.class);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Map<String, Map<String, Long>> groupCounts(
            List<UserBehaviorRepository.EventCount> counts) {
        Map<String, Map<String, Long>> result = new HashMap<>();
        for (UserBehaviorRepository.EventCount ec : counts) {
            if (ec._id() == null) continue;
            result.computeIfAbsent(ec._id().productId(), k -> new HashMap<>())
                    .put(ec._id().eventType(), ec.count());
        }
        return result;
    }
}