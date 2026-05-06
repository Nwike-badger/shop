package semicolon.africa.waylchub.service.recommendation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.ProductFilterRequest;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.repository.recommendationRepository.ProductPopularityRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-strategy product search that solves the "jeans not showing" problem.
 *
 * THE PROBLEM with the original search:
 *   MongoDB text index only matches documents where the query term appears in
 *   name/description/brandName. So searching "jeans" on a product called
 *   "Levi Original Fit" (whose category is "Jeans") returns 0 results.
 *
 * THE SOLUTION — three strategies run in parallel, results merged:
 *
 *   Strategy 1: TEXT SEARCH
 *     Standard MongoDB text index search on name, description, brandName, tags.
 *
 *   Strategy 2: CATEGORY EXPANSION
 *     Parse the query for terms that match category names or slugs.
 *     If "jeans" matches a category, include ALL products in that category
 *     (and all sub-categories via the categoryLineageIds field).
 *
 *   Strategy 3: BRAND MATCHING
 *     Check if any query term matches a brand name.
 *
 * FIELD NOTE:
 *   Product.categoryLineageIds is a List<String> of ancestor + self category IDs.
 *   Queries use Criteria.where("categoryLineageIds").in(catId) — NOT a regex on
 *   a "categoryLineage" string field (which does not exist on the model).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartSearchService {

    private final MongoTemplate mongoTemplate;
    private final CategoryRepository categoryRepository;
    private final ProductPopularityRepository popularityRepository;

    private static final int MAX_RESULTS_PER_STRATEGY = 100;

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    public Page<Product> search(String keyword, ProductFilterRequest filter, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return browseFiltered(filter, pageable);
        }

        String cleanQuery = keyword.trim().toLowerCase();
        List<String> queryTerms = Arrays.asList(cleanQuery.split("\\s+"));

        Set<String> textMatchIds      = runTextSearch(cleanQuery, filter);
        Set<String> categoryExpandIds = runCategoryExpansion(queryTerms, filter);
        Set<String> brandMatchIds     = runBrandMatch(queryTerms, filter);

        LinkedHashSet<String> orderedIds = new LinkedHashSet<>();
        orderedIds.addAll(textMatchIds);
        orderedIds.addAll(categoryExpandIds);
        orderedIds.addAll(brandMatchIds);

        if (orderedIds.isEmpty()) {
            log.debug("Smart search: zero results for query='{}', returning empty page", cleanQuery);
            return Page.empty(pageable);
        }

        List<String> rankedIds = reRankByPopularity(new ArrayList<>(orderedIds));

        int totalSize = rankedIds.size();
        int start     = (int) pageable.getOffset();
        int end       = Math.min(start + pageable.getPageSize(), totalSize);

        if (start >= totalSize) {
            return Page.empty(pageable);
        }

        List<String> pageIds = rankedIds.subList(start, end);

        Query fetchQuery = new Query(Criteria.where("id").in(pageIds)
                .and("isActive").is(true));
        List<Product> products = mongoTemplate.find(fetchQuery, Product.class);

        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<Product> orderedProducts = pageIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return PageableExecutionUtils.getPage(orderedProducts, pageable, () -> totalSize);
    }

    // =========================================================================
    // STRATEGY 1: TEXT SEARCH
    // =========================================================================

    private Set<String> runTextSearch(String query, ProductFilterRequest filter) {
        try {
            Query q = new Query();
            q.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(query));
            q.addCriteria(Criteria.where("isActive").is(true));
            applyPriceFilter(q, filter);
            applyHardCategoryFilter(q, filter);
            q.fields().include("id");
            q.limit(MAX_RESULTS_PER_STRATEGY);

            return mongoTemplate.find(q, Product.class).stream()
                    .map(Product::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

        } catch (Exception e) {
            log.warn("Text search failed (index may not exist): {}", e.getMessage());
            return runFallbackNameSearch(query, filter);
        }
    }

    private Set<String> runFallbackNameSearch(String query, ProductFilterRequest filter) {
        try {
            String regex = ".*" + query.replace(" ", ".*") + ".*";
            Query q = new Query();
            q.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(regex, "i"),
                    Criteria.where("brandName").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i")
            ));
            q.addCriteria(Criteria.where("isActive").is(true));
            q.fields().include("id");
            q.limit(MAX_RESULTS_PER_STRATEGY);

            return mongoTemplate.find(q, Product.class).stream()
                    .map(Product::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception e) {
            log.error("Fallback name search failed: {}", e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    // =========================================================================
    // STRATEGY 2: CATEGORY EXPANSION
    //
    // FIX: was using Criteria.where("categoryLineage").regex(...)
    //      which references a field that does not exist on the Product model.
    //      Product has categoryLineageIds (List<String>) populated by
    //      ProductService.extractLineageIds() — queried with .in(catId).
    // =========================================================================

    private Set<String> runCategoryExpansion(List<String> queryTerms, ProductFilterRequest filter) {
        try {
            List<Category> matchingCategories = new ArrayList<>();

            for (String term : queryTerms) {
                if (term.length() < 3) continue;

                categoryRepository.findBySlug(term).ifPresent(matchingCategories::add);

                Query catQuery = new Query(
                        Criteria.where("name").regex(".*" + term + ".*", "i")
                );
                matchingCategories.addAll(mongoTemplate.find(catQuery, Category.class));
            }

            if (matchingCategories.isEmpty()) return new LinkedHashSet<>();

            // Deduplicate matched category IDs
            Set<String> categoryIds = matchingCategories.stream()
                    .map(Category::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // categoryLineageIds contains the category itself AND all its ancestors.
            // Querying .in(catId) finds every product that belongs to this category
            // or any sub-category — no regex needed.
            Query q = new Query();
            q.addCriteria(Criteria.where("categoryLineageIds").in(categoryIds));
            q.addCriteria(Criteria.where("isActive").is(true));
            applyPriceFilter(q, filter);
            q.fields().include("id");
            q.limit(MAX_RESULTS_PER_STRATEGY);

            return mongoTemplate.find(q, Product.class).stream()
                    .map(Product::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

        } catch (Exception e) {
            log.warn("Category expansion search failed: {}", e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    // =========================================================================
    // STRATEGY 3: BRAND MATCHING
    // =========================================================================

    private Set<String> runBrandMatch(List<String> queryTerms, ProductFilterRequest filter) {
        try {
            List<Criteria> brandCriteria = queryTerms.stream()
                    .filter(term -> term.length() >= 2)
                    .map(term -> Criteria.where("brandName").regex(".*" + term + ".*", "i"))
                    .collect(Collectors.toList());

            if (brandCriteria.isEmpty()) return new LinkedHashSet<>();

            Query q = new Query();
            q.addCriteria(new Criteria().orOperator(brandCriteria.toArray(new Criteria[0])));
            q.addCriteria(Criteria.where("isActive").is(true));
            applyPriceFilter(q, filter);
            q.fields().include("id");
            q.limit(MAX_RESULTS_PER_STRATEGY);

            return mongoTemplate.find(q, Product.class).stream()
                    .map(Product::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

        } catch (Exception e) {
            log.warn("Brand match search failed: {}", e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    // =========================================================================
    // RE-RANK BY POPULARITY
    // =========================================================================

    private List<String> reRankByPopularity(List<String> productIds) {
        if (productIds.size() <= 1) return productIds;

        try {
            Map<String, Double> scoreMap = new HashMap<>();
            popularityRepository.findAllById(productIds)
                    .forEach(p -> scoreMap.put(p.getProductId(), p.getPopularityScore()));

            return productIds.stream()
                    .sorted(Comparator.comparingDouble(
                            (String id) -> scoreMap.getOrDefault(id, 0.0)).reversed())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Popularity re-ranking failed, using original order: {}", e.getMessage());
            return productIds;
        }
    }

    // =========================================================================
    // BROWSE (no keyword — filter only)
    // =========================================================================

    private Page<Product> browseFiltered(ProductFilterRequest filter, Pageable pageable) {
        Query q = new Query();
        q.addCriteria(Criteria.where("isActive").is(true));
        applyHardCategoryFilter(q, filter);
        applyPriceFilter(q, filter);

        long count = mongoTemplate.count(q.skip(-1).limit(-1), Product.class);
        List<Product> products = mongoTemplate.find(q.with(pageable), Product.class);

        return PageableExecutionUtils.getPage(products, pageable, () -> count);
    }

    // =========================================================================
    // FILTER HELPERS
    // =========================================================================

    private void applyPriceFilter(Query q, ProductFilterRequest filter) {
        if (filter == null) return;
        if (filter.getMinPrice() != null)
            q.addCriteria(Criteria.where("minPrice").gte(filter.getMinPrice()));
        if (filter.getMaxPrice() != null)
            q.addCriteria(Criteria.where("maxPrice").lte(filter.getMaxPrice()));
    }

    private void applyHardCategoryFilter(Query q, ProductFilterRequest filter) {
        if (filter == null || filter.getCategorySlug() == null) return;

        categoryRepository.findBySlug(filter.getCategorySlug()).ifPresent(cat -> {
            // Same fix: use categoryLineageIds not a regex on a non-existent field
            q.addCriteria(Criteria.where("categoryLineageIds").in(cat.getId()));
        });
    }

    /**
     * Run this ONCE against your MongoDB to enable full smart search.
     *
     * db.products.dropIndex("$**_text");
     * db.products.createIndex(
     *   { name: "text", description: "text", brandName: "text",
     *     categoryName: "text", "tags": "text" },
     *   { weights: { name: 10, brandName: 8, categoryName: 5,
     *                tags: 4, description: 1 },
     *     name: "products_smart_text_idx" }
     * );
     */
    public static final String REQUIRED_INDEX_COMMAND = """
        db.products.dropIndex("$**_text");
        db.products.createIndex(
          { name: "text", description: "text", brandName: "text",
            categoryName: "text", "tags": "text" },
          { weights: { name: 10, brandName: 8, categoryName: 5,
                       tags: 4, description: 1 },
            name: "products_smart_text_idx" }
        );
        """;
}