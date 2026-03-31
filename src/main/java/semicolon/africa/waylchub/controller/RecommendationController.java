package semicolon.africa.waylchub.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.productDto.ProductFilterRequest;
import semicolon.africa.waylchub.dto.recommendation.RecommendationResponse;
import semicolon.africa.waylchub.dto.recommendation.TrackEventRequest;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.service.recommendation.BehaviorTrackingService;
import semicolon.africa.waylchub.service.recommendation.RecommendationService;
import semicolon.africa.waylchub.service.recommendation.SmartSearchService;

import java.security.Principal;
import java.util.List;

/**
 * REST endpoints for search, recommendations, and behavior tracking.
 *
 * BASE URL: /api/v1
 *
 * SEARCH:
 *   GET  /search?q=jeans&page=0&size=24   → smart multi-strategy search
 *
 * RECOMMENDATIONS:
 *   GET  /products/{id}/recommendations   → product page carousels
 *   GET  /recommendations/for-you         → personalized homepage feed
 *   GET  /recommendations/trending        → globally popular
 *   GET  /recommendations/trending/category/{slug} → popular in category
 *
 * BEHAVIOR TRACKING (fire-and-forget — all return 204):
 *   POST /track/view
 *   POST /track/search
 *   POST /track/cart
 *   POST /track/wishlist
 *
 * NOTE ON AUTHENTICATION:
 *   User ID is read from the JWT Principal when authenticated.
 *   Session ID is read from the X-Session-Id request header.
 *   Both are optional — the tracking and recommendation logic handles nulls.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final SmartSearchService searchService;
    private final RecommendationService recommendationService;
    private final BehaviorTrackingService trackingService;

    // =========================================================================
    // SMART SEARCH
    // =========================================================================

    @GetMapping("/search")
    public ResponseEntity<Page<Product>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "24")   int size,
            @RequestParam(required = false)      String category,
            @RequestParam(required = false)      Double minPrice,
            @RequestParam(required = false)      Double maxPrice,
            Principal principal,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setCategorySlug(category);
        if (minPrice != null) filter.setMinPrice(java.math.BigDecimal.valueOf(minPrice));
        if (maxPrice != null) filter.setMaxPrice(java.math.BigDecimal.valueOf(maxPrice));

        Page<Product> results = searchService.search(q, filter, PageRequest.of(page, size));

        // Async: track the search event (non-blocking)
        if (q != null && !q.isBlank()) {
            String userId = principal != null ? principal.getName() : null;
            trackingService.trackSearch(userId, sessionId, q, null);
        }

        return ResponseEntity.ok(results);
    }

    // =========================================================================
    // PRODUCT PAGE RECOMMENDATIONS
    // =========================================================================

    @GetMapping("/products/{productId}/recommendations")
    public ResponseEntity<RecommendationResponse> getProductRecommendations(
            @PathVariable String productId,
            Principal principal,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        String userId = principal != null ? principal.getName() : null;
        RecommendationResponse response =
                recommendationService.getProductPageRecommendations(productId, userId, sessionId);

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // PERSONALIZED "FOR YOU"
    // =========================================================================

    @GetMapping("/recommendations/for-you")
    public ResponseEntity<List<Product>> getPersonalizedFeed(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false)    String categorySlug,
            Principal principal,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        String userId = principal != null ? principal.getName() : null;
        List<Product> feed = recommendationService.getPersonalizedRecommendations(
                userId, sessionId, categorySlug, limit);

        return ResponseEntity.ok(feed);
    }

    // =========================================================================
    // TRENDING / POPULAR
    // =========================================================================

    @GetMapping("/recommendations/trending")
    public ResponseEntity<List<Product>> getTrending(
            @RequestParam(defaultValue = "10") int limit) {

        List<Product> trending = recommendationService.getGloballyPopular(java.util.Set.of(), limit);
        return ResponseEntity.ok(trending);
    }

    @GetMapping("/recommendations/trending/category/{slug}")
    public ResponseEntity<List<Product>> getTrendingInCategory(
            @PathVariable String slug,
            @RequestParam(defaultValue = "10") int limit) {

        List<Product> trending = recommendationService.getPopularInCategory(slug, java.util.Set.of(), limit);
        return ResponseEntity.ok(trending);
    }

    // =========================================================================
    // BEHAVIOR TRACKING  (fire and forget — always return 204 No Content)
    // =========================================================================

    @PostMapping("/track/view")
    public ResponseEntity<Void> trackView(
            @RequestBody TrackEventRequest req,
            Principal principal,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        String userId = principal != null ? principal.getName() : null;
        trackingService.trackView(userId, sessionId, req.productId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/track/search")
    public ResponseEntity<Void> trackSearch(
            @RequestBody TrackEventRequest req,
            Principal principal,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        String userId = principal != null ? principal.getName() : null;
        trackingService.trackSearch(userId, sessionId, req.query(), req.productId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/track/cart")
    public ResponseEntity<Void> trackCartAdd(
            @RequestBody TrackEventRequest req,
            Principal principal,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        String userId = principal != null ? principal.getName() : null;
        trackingService.trackCartAdd(userId, sessionId, req.productId(), req.variantId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/track/wishlist")
    public ResponseEntity<Void> trackWishlist(
            @RequestBody TrackEventRequest req,
            Principal principal,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        String userId = principal != null ? principal.getName() : null;
        trackingService.trackWishlist(userId, sessionId, req.productId());
        return ResponseEntity.noContent().build();
    }
}