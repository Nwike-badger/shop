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
 *
 * LAYER 2 — COLLABORATIVE FILTERING (available after CF job has run)
 *   Pre-computed nightly by RecommendationScheduler.
 *
 * LAYER 3 — PERSONALIZED (available when user has history)
 *   Seeds from user's recent behavior → fetches CF similarities for each seed.
 *
 * LAYER 4 — POPULARITY FALLBACK (always available)
 *   Most popular products in the same category.
 *
 * FIELD NOTE:
 *   Product.categoryLineageIds is a List<String>. Querying sub-categories uses
 *   Criteria.where("categoryLineageIds").in(categoryId) — NOT a regex on a
 *   "categoryLineage" string field which does not exist on the Product model.
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

    private static final int DEFAULT_LIMIT       = 10;
    private static final int SEED_PRODUCT_LIMIT  = 5;

    // =========================================================================
    // PRODUCT PAGE: "Similar Products" + "Also Bought"
    // =========================================================================

    public RecommendationResponse getProductPageRecommendations(
            String productId, String userId, String sessionId) {

        Product source = productRepository.findById(productId).orElse(null);
        if (source == null) return RecommendationResponse.empty();

        Set<String> exclude = Set.of(productId);

        List<Product> similar    = getContentBasedSimilar(source, exclude, DEFAULT_LIMIT);
        List<Product> alsoBought = getCollaborativeRecs(productId, SimilarityType.CO_PURCHASE, exclude, DEFAULT_LIMIT);
        List<Product> alsoViewed = getCollaborativeRecs(productId, SimilarityType.CO_VIEW,     exclude, DEFAULT_LIMIT);

        if (similar.isEmpty()) {
            similar = getPopularInCategory(source.getCategorySlug(), exclude, DEFAULT_LIMIT);
        }
        if (alsoBought.isEmpty()) {
            alsoBought = similar;
        }

        return RecommendationResponse.builder()
                .similarProducts(similar)
                .customersAlsoBought(alsoBought)
                .customersAlsoViewed(alsoViewed)
                .build();
    }

    // =========================================================================
    // LAYER 1: CONTENT-BASED FILTERING
    //
    // FIX: was using Criteria.where("categoryLineage").regex(...)
    //      Product has categoryLineageIds (List<String>), not a "categoryLineage"
    //      string field. Use Criteria.where("categoryLineageIds").in(categoryId).
    // =========================================================================

    public List<Product> getContentBasedSimilar(Product source, Set<String> exclude, int limit) {
        try {
            List<Criteria> criteriaList = new ArrayList<>();

            // Same category or any sub-category via lineage IDs
            if (source.getCategorySlug() != null) {
                criteriaList.add(Criteria.where("categorySlug").is(source.getCategorySlug()));
            }
            if (source.getCategory() != null && source.getCategory().getId() != null) {
                // categoryLineageIds contains the category itself + all ancestor IDs.
                // Any product whose lineage contains this category ID is in the
                // same category tree — no regex required.
                criteriaList.add(
                        Criteria.where("categoryLineageIds").in(source.getCategory().getId())
                );
            }

            if (source.getBrandName() != null) {
                criteriaList.add(Criteria.where("brandName").is(source.getBrandName()));
            }

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
            q.limit(limit * 3);

            List<Product> candidates = mongoTemplate.find(q, Product.class);

            return candidates.stream()
                    .map(p -> new AbstractMap.SimpleEntry<>(p, scoreContentSimilarity(source, p)))
                    .sorted(Map.Entry.<Product, Integer>comparingByValue().reversed())
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Content-based similarity failed for product {}: {}", source.getId(), e.getMessage());
            return List.of();
        }
    }

    private int scoreContentSimilarity(Product source, Product candidate) {
        int score = 0;
        if (Objects.equals(source.getCategorySlug(), candidate.getCategorySlug())) score += 3;
        if (Objects.equals(source.getBrandName(),    candidate.getBrandName()))    score += 2;
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
                    .collect(Collectors.toList());

            return fetchProductsOrdered(similarIds);

        } catch (Exception e) {
            log.warn("Collaborative filtering lookup failed for product {}: {}", productId, e.getMessage());
            return List.of();
        }
    }

    // =========================================================================
    // LAYER 3: PERSONALIZED ("For You")
    // =========================================================================

    public List<Product> getPersonalizedRecommendations(
            String userId, String sessionId, String currentCategorySlug, int limit) {
        try {
            List<String> recentlyViewed = trackingService.getRecentlyViewed(
                    userId, sessionId, SEED_PRODUCT_LIMIT);

            if (recentlyViewed.isEmpty()) {
                return getPopularInCategory(currentCategorySlug, Set.of(), limit);
            }

            Map<String, Double> scoreAccumulator = new HashMap<>();
            for (String seedId : recentlyViewed) {
                accumulateSimilarityScores(seedId, SimilarityType.CO_PURCHASE, 2.0, scoreAccumulator);
                accumulateSimilarityScores(seedId, SimilarityType.CO_VIEW,     1.0, scoreAccumulator);
            }

            Set<String> seenIds = new HashSet<>(recentlyViewed);
            if (userId != null) {
                seenIds.addAll(getPurchasedProductIds(userId));
            }

            List<String> rankedIds = scoreAccumulator.entrySet().stream()
                    .filter(e -> !seenIds.contains(e.getKey()))
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .limit(limit)
                    .collect(Collectors.toList());

            List<Product> personalized = fetchProductsOrdered(rankedIds);

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
            if (categorySlug == null) {
                return getGloballyPopular(exclude, limit);
            }

            List<String> popularIds = popularityRepository
                    .findByCategoryIdOrderByPopularityScoreDesc(categorySlug,
                            PageRequest.of(0, limit + exclude.size()))
                    .stream()
                    .map(p -> p.getProductId())
                    .filter(id -> !exclude.contains(id))
                    .limit(limit)
                    .collect(Collectors.toList());

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
                    .collect(Collectors.toList());

            return fetchProductsOrdered(ids);

        } catch (Exception e) {
            log.warn("Global popularity fallback failed: {}", e.getMessage());
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

    private List<Product> fetchProductsOrdered(List<String> orderedIds) {
        if (orderedIds.isEmpty()) return List.of();

        Query q = new Query(Criteria.where("id").in(orderedIds)
                .and("isActive").is(true));
        Map<String, Product> productMap = mongoTemplate.find(q, Product.class).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return orderedIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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