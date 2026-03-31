package semicolon.africa.waylchub.service.recommendation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.recommendation.RecommendationResponse;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.recommendation.ItemSimilarity;
import semicolon.africa.waylchub.model.recommendation.ItemSimilarity.SimilarityType;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.recommendationRepository.ItemSimilarityRepository;
import semicolon.africa.waylchub.repository.recommendationRepository.ProductPopularityRepository;
import semicolon.africa.waylchub.repository.recommendationRepository.UserBehaviorRepository;
import semicolon.africa.waylchub.model.recommendation.UserBehaviorLog.BehaviorEventType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommendation Service — four layers of intelligence, graceful degradation.
 *
 * LAYER 1 — CONTENT-BASED (always available, zero user data needed)
 *   Same category + brand overlap + tag overlap.
 *   Used for: "Similar Products" on product detail page.
 *
 * LAYER 2 — COLLABORATIVE FILTERING (available after CF job has run)
 *   "Customers who viewed/bought this also viewed/bought..."
 *   Pre-computed nightly by RecommendationScheduler.
 *   Used for: "Also Bought", "Also Viewed" carousels.
 *
 * LAYER 3 — PERSONALIZED (available when user has history)
 *   "Based on products you've viewed/bought..."
 *   Seeds from user's recent behavior → fetches CF similarities for each seed.
 *   Used for: Homepage "For You" carousel.
 *
 * LAYER 4 — POPULARITY FALLBACK (always available)
 *   Most popular products in the same category.
 *   Used when layers 1-3 return insufficient results.
 *
 * ALL METHODS return at most `limit` products, deduplicated, excluding the
 * source product itself and any products the user has already purchased.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final MongoTemplate mongoTemplate;
    private final ProductRepository productRepository;
    private final ItemSimilarityRepository similarityRepository;
    private final ProductPopularityRepository popularityRepository;
    private final UserBehaviorRepository behaviorRepository;
    private final BehaviorTrackingService trackingService;

    private static final int DEFAULT_LIMIT = 10;
    private static final int SEED_PRODUCT_LIMIT = 5; // max seeds for personalized recs

    // =========================================================================
    // PRODUCT PAGE: "Similar Products" + "Also Bought"
    // =========================================================================

    /**
     * Primary recommendation call for a product detail page.
     * Returns a structured response with multiple recommendation carousels.
     */
    public RecommendationResponse getProductPageRecommendations(
            String productId, String userId, String sessionId) {

        Product source = productRepository.findById(productId).orElse(null);
        if (source == null) return RecommendationResponse.empty();

        Set<String> exclude = Set.of(productId);

        // Carousel 1: Content-based similar products
        List<Product> similar = getContentBasedSimilar(source, exclude, DEFAULT_LIMIT);

        // Carousel 2: "Customers also bought" (collaborative filtering)
        List<Product> alsoBought = getCollaborativeRecs(
                productId, SimilarityType.CO_PURCHASE, exclude, DEFAULT_LIMIT);

        // Carousel 3: "Customers also viewed"
        List<Product> alsoViewed = getCollaborativeRecs(
                productId, SimilarityType.CO_VIEW, exclude, DEFAULT_LIMIT);

        // Fill carousels that are empty with popularity fallback
        if (similar.isEmpty()) {
            similar = getPopularInCategory(source.getCategorySlug(), exclude, DEFAULT_LIMIT);
        }
        if (alsoBought.isEmpty()) {
            alsoBought = similar; // reuse similar if CF data not available yet
        }

        return RecommendationResponse.builder()
                .similarProducts(similar)
                .customersAlsoBought(alsoBought)
                .customersAlsoViewed(alsoViewed)
                .build();
    }

    // =========================================================================
    // LAYER 1: CONTENT-BASED FILTERING
    // =========================================================================

    /**
     * Finds products similar to `source` based on shared attributes.
     * Scoring: +3 same category, +2 same brand, +1 per shared tag.
     * No user data required — works from day one of your catalogue.
     */
    public List<Product> getContentBasedSimilar(Product source, Set<String> exclude, int limit) {
        try {
            List<Criteria> criteriaList = new ArrayList<>();

            // Same category (including sub-categories via lineage)
            if (source.getCategorySlug() != null) {
                criteriaList.add(Criteria.where("categorySlug").is(source.getCategorySlug()));
                if (source.getCategory() != null) {
                    criteriaList.add(
                            Criteria.where("categoryLineage")
                                    .regex("," + source.getCategory().getId() + ",")
                    );
                }
            }

            // Same brand
            if (source.getBrandName() != null) {
                criteriaList.add(Criteria.where("brandName").is(source.getBrandName()));
            }

            // Shared tags
            if (source.getTags() != null && !source.getTags().isEmpty()) {
                criteriaList.add(Criteria.where("tags").in(source.getTags()));
            }

            if (criteriaList.isEmpty()) {
                return getPopularInCategory(source.getCategorySlug(), exclude, limit);
            }

            Query q = new Query(new Criteria().orOperator(criteriaList.toArray(new Criteria[0])));
            q.addCriteria(Criteria.where("isActive").is(true));
            if (!exclude.isEmpty()) {
                q.addCriteria(Criteria.where("id").nin(exclude));
            }
            q.limit(limit * 3); // fetch extra, re-rank by score

            List<Product> candidates = mongoTemplate.find(q, Product.class);

            // Score each candidate
            return candidates.stream()
                    .map(p -> new AbstractMap.SimpleEntry<>(p, scoreContentSimilarity(source, p)))
                    .sorted(Map.Entry.<Product, Integer>comparingByValue().reversed())
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .toList();

        } catch (Exception e) {
            log.warn("Content-based similarity failed for product {}: {}", source.getId(), e.getMessage());
            return List.of();
        }
    }

    private int scoreContentSimilarity(Product source, Product candidate) {
        int score = 0;
        if (Objects.equals(source.getCategorySlug(), candidate.getCategorySlug())) score += 3;
        if (Objects.equals(source.getBrandName(), candidate.getBrandName()))        score += 2;
        if (source.getTags() != null && candidate.getTags() != null) {
            Set<String> shared = new HashSet<>(source.getTags());
            shared.retainAll(candidate.getTags());
            score += shared.size();
        }
        return score;
    }

    // =========================================================================
    // LAYER 2: COLLABORATIVE FILTERING (pre-computed)
    // =========================================================================

    /**
     * Returns the top-N pre-computed similar products for the given signal type.
     * Reads from ItemSimilarity collection — single indexed lookup, very fast.
     */
    public List<Product> getCollaborativeRecs(
            String productId, SimilarityType type, Set<String> exclude, int limit) {
        try {
            Optional<ItemSimilarity> similarity =
                    similarityRepository.findBySourceProductIdAndType(productId, type);

            if (similarity.isEmpty() || similarity.get().getSimilar() == null) {
                return List.of();
            }

            List<String> similarIds = similarity.get().getSimilar().stream()
                    .sorted(Comparator.comparingDouble(ItemSimilarity.SimilarEntry::getScore).reversed())
                    .map(ItemSimilarity.SimilarEntry::getProductId)
                    .filter(id -> !exclude.contains(id))
                    .limit(limit)
                    .toList();

            return fetchProductsOrdered(similarIds);

        } catch (Exception e) {
            log.warn("Collaborative filtering lookup failed for product {}: {}", productId, e.getMessage());
            return List.of();
        }
    }

    // =========================================================================
    // LAYER 3: PERSONALIZED ("For You")
    // =========================================================================

    /**
     * Generates personalized recommendations for a user based on their history.
     *
     * ALGORITHM:
     *   1. Fetch user's recently viewed/purchased products ("seeds")
     *   2. For each seed, fetch pre-computed CO_PURCHASE + CO_VIEW neighbors
     *   3. Aggregate scores (products appearing as neighbors of multiple seeds score higher)
     *   4. De-duplicate, exclude already-seen products
     *   5. Re-rank by popularity
     *   6. Return top N
     *
     * Falls back to popularity-based if user has no history.
     */
    public List<Product> getPersonalizedRecommendations(
            String userId, String sessionId, String currentCategorySlug, int limit) {
        try {
            List<String> recentlyViewed = trackingService.getRecentlyViewed(
                    userId, sessionId, SEED_PRODUCT_LIMIT);

            if (recentlyViewed.isEmpty()) {
                // Cold start: return popular products in the current category context
                return getPopularInCategory(currentCategorySlug, Set.of(), limit);
            }

            // Get CF neighbors for each seed, aggregate scores
            Map<String, Double> scoreAccumulator = new HashMap<>();

            for (String seedId : recentlyViewed) {
                // Combine CO_PURCHASE (weight 2.0) and CO_VIEW (weight 1.0) signals
                accumulateSimilarityScores(seedId, SimilarityType.CO_PURCHASE, 2.0, scoreAccumulator);
                accumulateSimilarityScores(seedId, SimilarityType.CO_VIEW, 1.0, scoreAccumulator);
            }

            // Exclude the seeds themselves
            Set<String> seenIds = new HashSet<>(recentlyViewed);

            // Also exclude products user has already purchased
            if (userId != null) {
                Set<String> purchased = getPurchasedProductIds(userId);
                seenIds.addAll(purchased);
            }

            List<String> rankedIds = scoreAccumulator.entrySet().stream()
                    .filter(e -> !seenIds.contains(e.getKey()))
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .limit(limit)
                    .toList();

            List<Product> personalized = fetchProductsOrdered(rankedIds);

            // Pad with popularity fallback if insufficient results
            if (personalized.size() < limit) {
                Set<String> exclude = new HashSet<>(seenIds);
                personalized.forEach(p -> exclude.add(p.getId()));

                List<Product> fallback = getPopularInCategory(
                        currentCategorySlug, exclude, limit - personalized.size());

                List<Product> merged = new ArrayList<>(personalized);
                merged.addAll(fallback);
                return merged;
            }

            return personalized;

        } catch (Exception e) {
            log.warn("Personalized recs failed for user {}: {}", userId, e.getMessage());
            return getPopularInCategory(currentCategorySlug, Set.of(), limit);
        }
    }

    // =========================================================================
    // LAYER 4: POPULARITY FALLBACK
    // =========================================================================

    public List<Product> getPopularInCategory(String categorySlug, Set<String> exclude, int limit) {
        try {
            // Find category ID from slug first
            if (categorySlug == null) {
                return getGloballyPopular(exclude, limit);
            }

            // Use popularity collection (pre-aggregated, fast lookup)
            List<String> popularIds = popularityRepository
                    .findByCategoryIdOrderByPopularityScoreDesc(categorySlug,
                            PageRequest.of(0, limit + exclude.size()))
                    .stream()
                    .map(p -> p.getProductId())
                    .filter(id -> !exclude.contains(id))
                    .limit(limit)
                    .toList();

            if (popularIds.isEmpty()) {
                return getGloballyPopular(exclude, limit);
            }

            return fetchProductsOrdered(popularIds);

        } catch (Exception e) {
            log.warn("Category popularity lookup failed: {}", e.getMessage());
            return getGloballyPopular(exclude, limit);
        }
    }

    public List<Product> getGloballyPopular(Set<String> exclude, int limit) {
        try {
            List<String> ids = popularityRepository
                    .findAllByOrderByPopularityScoreDesc(PageRequest.of(0, limit + exclude.size()))
                    .stream()
                    .map(p -> p.getProductId())
                    .filter(id -> !exclude.contains(id))
                    .limit(limit)
                    .toList();

            return fetchProductsOrdered(ids);

        } catch (Exception e) {
            log.warn("Global popularity fallback failed: {}", e.getMessage());
            // Last resort: just get any active products
            Query q = new Query(Criteria.where("isActive").is(true));
            q.limit(limit);
            return mongoTemplate.find(q, Product.class);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private void accumulateSimilarityScores(
            String seedProductId, SimilarityType type,
            double weight, Map<String, Double> accumulator) {

        similarityRepository.findBySourceProductIdAndType(seedProductId, type)
                .ifPresent(sim -> {
                    if (sim.getSimilar() == null) return;
                    sim.getSimilar().forEach(entry ->
                            accumulator.merge(entry.getProductId(),
                                    entry.getScore() * weight,
                                    Double::sum)
                    );
                });
    }

    /**
     * Fetches full Product documents for a list of IDs, preserving the input order.
     * MongoDB $in does not preserve order, so we sort after fetching.
     */
    private List<Product> fetchProductsOrdered(List<String> orderedIds) {
        if (orderedIds.isEmpty()) return List.of();

        Query q = new Query(Criteria.where("id").in(orderedIds)
                .and("isActive").is(true));
        Map<String, Product> productMap = mongoTemplate.find(q, Product.class).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return orderedIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private Set<String> getPurchasedProductIds(String userId) {
        return behaviorRepository.findByUserIdAndEventType(
                        userId, BehaviorEventType.PURCHASE, PageRequest.of(0, 200))
                .stream()
                .map(l -> l.getProductId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}