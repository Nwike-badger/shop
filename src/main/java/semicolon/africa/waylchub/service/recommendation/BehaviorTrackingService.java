package semicolon.africa.waylchub.service.recommendation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.model.recommendation.UserBehaviorLog;
import semicolon.africa.waylchub.model.recommendation.UserBehaviorLog.BehaviorEventType;
import semicolon.africa.waylchub.repository.recommendationRepository.UserBehaviorRepository;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Async, fire-and-forget behavioral event ingestion.
 *
 * DESIGN PRINCIPLE: Every method in this class is @Async.
 * The caller (controller) writes to the HTTP response immediately and this
 * service processes in a background thread pool. A 5ms tracking call NEVER
 * adds latency to a product page load.
 *
 * THREAD POOL: Configure a dedicated thread pool in AsyncConfig
 * so tracking threads cannot starve the main application pool.
 *
 * ERROR HANDLING: Tracking failures are caught and logged but never
 * surfaced to the user. A failed view event should not crash a product page.
 *
 * DEDUPLICATION: We skip duplicate VIEW events within a 30-minute window
 * for the same (userId/sessionId, productId) pair to avoid inflating view
 * counts from page refreshes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorTrackingService {

    private final UserBehaviorRepository behaviorRepository;
    private final ProductRepository productRepository;

    // TTL: Views expire after 90 days. Purchases never expire (null expiresAt).
    private static final int VIEW_TTL_DAYS     = 90;
    private static final int SEARCH_TTL_DAYS   = 30;
    private static final int DEDUP_WINDOW_MINS = 30;

    // =========================================================================
    // TRACK EVENTS (all async — do not block the HTTP request)
    // =========================================================================

    @Async("trackingExecutor")
    public void trackView(String userId, String sessionId, String productId) {
        try {
            // Skip duplicate views within the dedup window
            if (isDuplicateEvent(userId, sessionId, productId, BehaviorEventType.VIEW, DEDUP_WINDOW_MINS)) {
                return;
            }

            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) return; // product was deleted, ignore

            UserBehaviorLog log = UserBehaviorLog.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .productId(productId)
                    .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                    .categorySlug(product.getCategorySlug())
                    .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                    .eventType(BehaviorEventType.VIEW)
                    .timestamp(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(VIEW_TTL_DAYS))
                    .build();

            behaviorRepository.save(log);

        } catch (Exception e) {
            // NEVER throw from tracking methods — a failed track must not affect UX
            log.warn("Failed to track VIEW for product {}: {}", productId, e.getMessage());
        }
    }

    @Async("trackingExecutor")
    public void trackSearch(String userId, String sessionId, String query, String clickedProductId) {
        try {
            if (query == null || query.isBlank()) return;

            UserBehaviorLog.UserBehaviorLogBuilder builder = UserBehaviorLog.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .eventType(BehaviorEventType.SEARCH)
                    .searchQuery(query.toLowerCase().trim())
                    .timestamp(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(SEARCH_TTL_DAYS));

            // If user clicked a product from search results, record product affinity too
            if (clickedProductId != null) {
                Product product = productRepository.findById(clickedProductId).orElse(null);
                if (product != null) {
                    builder.productId(clickedProductId)
                            .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                            .categorySlug(product.getCategorySlug())
                            .brandId(product.getBrand() != null ? product.getBrand().getId() : null);
                }
            }

            behaviorRepository.save(builder.build());

        } catch (Exception e) {
            log.warn("Failed to track SEARCH '{}': {}", query, e.getMessage());
        }
    }

    @Async("trackingExecutor")
    public void trackCartAdd(String userId, String sessionId, String productId, String variantId) {
        try {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) return;

            UserBehaviorLog log = UserBehaviorLog.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .productId(productId)
                    .variantId(variantId)
                    .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                    .categorySlug(product.getCategorySlug())
                    .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                    .eventType(BehaviorEventType.ADD_TO_CART)
                    .timestamp(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(VIEW_TTL_DAYS))
                    .build();

            behaviorRepository.save(log);

        } catch (Exception e) {
            log.warn("Failed to track ADD_TO_CART for product {}: {}", productId, e.getMessage());
        }
    }

    @Async("trackingExecutor")
    public void trackWishlist(String userId, String sessionId, String productId) {
        try {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) return;

            UserBehaviorLog log = UserBehaviorLog.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .productId(productId)
                    .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                    .categorySlug(product.getCategorySlug())
                    .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                    .eventType(BehaviorEventType.WISHLIST)
                    .timestamp(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(VIEW_TTL_DAYS))
                    .build();

            behaviorRepository.save(log);

        } catch (Exception e) {
            log.warn("Failed to track WISHLIST for product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Called by OrderService after successful payment.
     * PURCHASE events have null expiresAt — they are never deleted.
     * These are the highest-quality signal for collaborative filtering.
     */
    @Async("trackingExecutor")
    public void trackPurchases(String userId, String sessionId,
                               List<String> productIds, List<Double> amounts) {
        try {
            for (int i = 0; i < productIds.size(); i++) {
                String productId = productIds.get(i);
                Double amount = (amounts != null && i < amounts.size()) ? amounts.get(i) : null;

                Product product = productRepository.findById(productId).orElse(null);
                if (product == null) continue;

                UserBehaviorLog log = UserBehaviorLog.builder()
                        .userId(userId)
                        .sessionId(sessionId)
                        .productId(productId)
                        .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                        .categorySlug(product.getCategorySlug())
                        .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                        .eventType(BehaviorEventType.PURCHASE)
                        .purchaseAmount(amount)
                        .timestamp(LocalDateTime.now())
                        .expiresAt(null) // NEVER expire purchase events
                        .build();

                behaviorRepository.save(log);
            }
        } catch (Exception e) {
            log.warn("Failed to track PURCHASE events for user {}: {}", userId, e.getMessage());
        }
    }

    // =========================================================================
    // QUERY HELPERS (used by RecommendationService)
    // =========================================================================

    /**
     * Returns up to `limit` product IDs the user has recently viewed.
     * Used for: "Recently Viewed" widget, personalized recommendations seed.
     */
    public List<String> getRecentlyViewed(String userId, String sessionId, int limit) {
        try {
            String id = userId != null ? userId : sessionId;
            if (id == null) return List.of();

            List<UserBehaviorLog> logs = userId != null
                    ? behaviorRepository.findByUserIdAndEventType(
                    userId, BehaviorEventType.VIEW, PageRequest.of(0, limit * 2)) // fetch extra for dedup
                    : behaviorRepository.findBySessionIdOrderByTimestampDesc(
                    sessionId, PageRequest.of(0, limit * 2));

            return logs.stream()
                    .map(UserBehaviorLog::getProductId)
                    .filter(p -> p != null)
                    .distinct()
                    .limit(limit)
                    .toList();

        } catch (Exception e) {
            log.warn("Failed to fetch recently viewed for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Returns true if the same (userId OR sessionId) already has this eventType
     * for this productId within the last `windowMinutes` minutes.
     * Prevents view-count inflation from page refreshes.
     */
    private boolean isDuplicateEvent(String userId, String sessionId,
                                     String productId, BehaviorEventType type,
                                     int windowMinutes) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(windowMinutes);

            if (userId != null) {
                return behaviorRepository
                        .findByUserIdAndEventTypeInAndTimestampAfter(
                                userId, List.of(type), cutoff)
                        .stream()
                        .anyMatch(l -> productId.equals(l.getProductId()));
            } else if (sessionId != null) {
                return behaviorRepository
                        .findBySessionIdOrderByTimestampDesc(
                                sessionId, PageRequest.of(0, 50))
                        .stream()
                        .anyMatch(l -> productId.equals(l.getProductId())
                                && type == l.getEventType()
                                && l.getTimestamp().isAfter(cutoff));
            }
        } catch (Exception e) {
            // If dedup check fails, allow the event rather than suppressing it
            log.warn("Dedup check failed, allowing event: {}", e.getMessage());
        }
        return false;
    }
}