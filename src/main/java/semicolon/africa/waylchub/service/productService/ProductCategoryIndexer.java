package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The ONE place that decides what a product's category fields contain.
 * Used by ProductService (product saved), CategoryService (category renamed or
 * moved) and CategoryMigration (old data).
 *
 * Fields it maintains on Product:
 *   categoryIds        [primaryId, otherId, …]
 *   categorySlugs      same order
 *   categorySlug/Name  the primary one (kept for older frontend code)
 *   category           @DBRef to the primary one
 *   categoryLineageIds every assigned category + all ancestors
 *
 * Category lookups go through CategoryFastLoader (one query, no per-parent calls).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCategoryIndexer {

    private final MongoTemplate mongoTemplate;
    private final CategoryFastLoader fastLoader;

    /**
     * Resolve what the form sent into an ordered list of categories.
     *
     * @param primarySlug the main category (may be null → first of allSlugs is used)
     * @param allSlugs    every category the product should be in (may be null)
     * @return ordered, de-duplicated categories, primary first; empty if none given
     * @throws ResourceNotFoundException for any slug that doesn't exist
     */
    public List<Category> resolve(String primarySlug, Collection<String> allSlugs) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (primarySlug != null && !primarySlug.isBlank()) ordered.add(primarySlug.trim());
        if (allSlugs != null) {
            allSlugs.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(ordered::add);
        }
        if (ordered.isEmpty()) return new ArrayList<>();

        Map<String, Category> bySlug = fastLoader.findBySlugs(ordered).stream()
                .collect(Collectors.toMap(Category::getSlug, Function.identity(), (a, b) -> a));

        List<String> missing = ordered.stream().filter(s -> !bySlug.containsKey(s)).toList();
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("Category not found: " + String.join(", ", missing));
        }
        return ordered.stream().map(bySlug::get).collect(Collectors.toList());
    }

    /** Writes the category fields onto an in-memory product (caller saves it). */
    public void apply(Product product, List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            product.setCategoryIds(new ArrayList<>());
            product.setCategorySlugs(new ArrayList<>());
            product.setCategoryLineageIds(new ArrayList<>());
            product.setCategory(null);
            product.setCategorySlug(null);
            product.setCategoryName(null);
            return;
        }
        Category primary = categories.get(0);
        product.setCategoryIds(categories.stream().map(Category::getId).collect(Collectors.toList()));
        product.setCategorySlugs(categories.stream().map(Category::getSlug).collect(Collectors.toList()));
        product.setCategoryLineageIds(lineageOf(categories));
        product.setCategory(primary);
        product.setCategorySlug(primary.getSlug());
        product.setCategoryName(primary.getName());
    }

    /**
     * Recompute the category fields of every product touching this category or
     * anything below it. Called after a category is renamed or moved.
     * Uses targeted updates (not save) so it never trips @Version.
     */
    public int reindexProductsUnder(String categoryId) {
        Query q = new Query(new Criteria().orOperator(
                Criteria.where("categoryLineageIds").is(categoryId),
                Criteria.where("categoryIds").is(categoryId)));
        q.fields().include("categoryIds", "categorySlug");
        List<Product> products = mongoTemplate.find(q, Product.class);
        if (products.isEmpty()) return 0;

        Map<String, Category> byId = fastLoader.byId();
        for (Product p : products) {
            reindexOne(p, byId);
        }
        return products.size();
    }

    /**
     * Recompute one product's category fields from its stored categoryIds.
     * For old products that only have the single categorySlug, that slug
     * becomes the (only) category.
     */
    public void reindexOne(Product p, Map<String, Category> byId) {
        List<Category> cats = new ArrayList<>();
        if (p.getCategoryIds() != null && !p.getCategoryIds().isEmpty()) {
            for (String id : p.getCategoryIds()) {
                Category c = byId.get(id);
                if (c != null) cats.add(c); // silently drop ids of deleted categories
            }
        } else if (p.getCategorySlug() != null) {
            byId.values().stream()
                    .filter(c -> p.getCategorySlug().equals(c.getSlug()))
                    .findFirst()
                    .ifPresent(cats::add);
        }

        Product tmp = new Product();
        apply(tmp, cats);

        Update u = new Update()
                .set("categoryIds", tmp.getCategoryIds())
                .set("categorySlugs", tmp.getCategorySlugs())
                .set("categoryLineageIds", tmp.getCategoryLineageIds())
                .set("categorySlug", tmp.getCategorySlug())
                .set("categoryName", tmp.getCategoryName())
                .set("category", tmp.getCategory()); // written as a DBRef (only the id is stored)
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(p.getId())), u, Product.class);
    }

    /** Ancestors of every category, then the category itself; de-duplicated, order kept. */
    public static List<String> lineageOf(Collection<Category> categories) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Category c : categories) {
            if (c.getLineage() != null) {
                for (String part : c.getLineage().split(",")) {
                    if (!part.isBlank()) ids.add(part.trim());
                }
            }
            if (c.getId() != null) ids.add(c.getId());
        }
        return new ArrayList<>(ids);
    }
}