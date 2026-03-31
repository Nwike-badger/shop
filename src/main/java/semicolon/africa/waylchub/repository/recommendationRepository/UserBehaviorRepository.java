package semicolon.africa.waylchub.repository.recommendationRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import semicolon.africa.waylchub.model.recommendation.UserBehaviorLog;
import semicolon.africa.waylchub.model.recommendation.UserBehaviorLog.BehaviorEventType;

import java.time.LocalDateTime;
import java.util.List;

public interface UserBehaviorRepository extends MongoRepository<UserBehaviorLog, String> {

    // ── User history ─────────────────────────────────────────────────────────

    /** Distinct product IDs a user has viewed (most recent first). Used for "recently viewed". */
    @Query(value = "{ 'userId': ?0, 'eventType': ?1 }",
            sort  = "{ 'timestamp': -1 }",
            fields = "{ 'productId': 1 }")
    List<UserBehaviorLog> findByUserIdAndEventType(String userId, BehaviorEventType type, Pageable pageable);

    /** All events for a user in a session (for anonymous personalization). */
    List<UserBehaviorLog> findBySessionIdOrderByTimestampDesc(String sessionId, Pageable pageable);

    /** Check if user has already seen this product (deduplication for "recently viewed"). */
    boolean existsByUserIdAndProductIdAndEventType(String userId, String productId, BehaviorEventType type);

    // ── Collaborative filtering data pull ────────────────────────────────────

    /**
     * All users who interacted with a product in the given time window.
     * Used by the CF job to build the co-occurrence matrix:
     *   step 1: find userIds who viewed/purchased productA
     *   step 2: find what else those users viewed/purchased
     */
    @Query("{ 'productId': ?0, 'eventType': { $in: ?1 }, 'timestamp': { $gte: ?2 } }")
    List<UserBehaviorLog> findByProductIdAndEventTypeInAndTimestampAfter(
            String productId,
            List<BehaviorEventType> eventTypes,
            LocalDateTime after);

    /**
     * All products a user interacted with in a window.
     * Used in the CF job's inner loop to find co-occurring products.
     */
    @Query("{ 'userId': ?0, 'eventType': { $in: ?1 }, 'timestamp': { $gte: ?2 } }")
    List<UserBehaviorLog> findByUserIdAndEventTypeInAndTimestampAfter(
            String userId,
            List<BehaviorEventType> eventTypes,
            LocalDateTime after);

    // ── Popularity aggregation ────────────────────────────────────────────────

    long countByProductIdAndEventTypeAndTimestampAfter(
            String productId, BehaviorEventType type, LocalDateTime after);

    /**
     * MongoDB aggregation to count events grouped by productId and eventType
     * in a time window — used by the popularity refresh job.
     *
     * Returns: [{ _id: { productId, eventType }, count: N }, ...]
     */
    @Aggregation(pipeline = {
            "{ $match: { 'eventType': { $in: ?0 }, 'timestamp': { $gte: ?1 } } }",
            "{ $group: { _id: { productId: '$productId', eventType: '$eventType' }, count: { $sum: 1 } } }",
            "{ $sort: { count: -1 } }"
    })
    List<EventCount> countGroupedByProductAndEvent(List<BehaviorEventType> types, LocalDateTime since);

    record EventCount(EventKey _id, long count) {}
    record EventKey(String productId, String eventType) {}
}