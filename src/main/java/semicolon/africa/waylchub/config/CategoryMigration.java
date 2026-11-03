package semicolon.africa.waylchub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.service.productService.ProductCategoryIndexer;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs on every startup. Idempotent (does nothing once data is migrated) and
 * can never stop the app booting — each step only logs if it fails.
 *
 *  1. Drops the old UNIQUE index on categories.name, so "Shirts" can exist
 *     under both Men and Women.
 *  2. Creates the new product indexes (Boot 3 has auto-index-creation off).
 *  3. Converts old products (single categorySlug) to the new multi-category
 *     fields. Their single category simply becomes their primary category.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryMigration implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final CategoryRepository categoryRepository;
    private final ProductCategoryIndexer indexer;
    private final CacheManager cacheManager;

    @Override
    public void run(ApplicationArguments args) {
        step("drop unique category name index", () -> {
            IndexOperations ops = mongoTemplate.indexOps(Category.class);
            for (IndexInfo info : ops.getIndexInfo()) {
                boolean onlyName = info.getIndexFields().size() == 1
                        && "name".equals(info.getIndexFields().get(0).getKey());
                if (onlyName && info.isUnique()) {
                    ops.dropIndex(info.getName());
                    log.info("[CategoryMigration] Dropped unique index '{}' on categories.name", info.getName());
                }
            }
        });

        step("product indexes", () -> {
            IndexOperations ops = mongoTemplate.indexOps(Product.class);
            ops.ensureIndex(new Index().on("categoryIds", Sort.Direction.ASC).named("categoryIds"));
            ops.ensureIndex(new Index().on("categorySlugs", Sort.Direction.ASC).named("categorySlugs"));
        });

        step("convert old products", () -> {
            Query old = new Query(new Criteria().orOperator(
                    Criteria.where("categoryIds").exists(false),
                    Criteria.where("categoryIds").size(0).and("categorySlug").ne(null)));
            old.fields().include("categoryIds", "categorySlug");
            List<Product> products = mongoTemplate.find(old, Product.class);
            if (products.isEmpty()) return;

            Map<String, Category> byId = categoryRepository.findAll().stream()
                    .collect(Collectors.toMap(Category::getId, Function.identity()));
            int done = 0;
            for (Product p : products) {
                try {
                    indexer.reindexOne(p, byId);
                    done++;
                } catch (Exception e) {
                    log.warn("[CategoryMigration] Product {} skipped: {}", p.getId(), e.getMessage());
                }
            }
            log.info("[CategoryMigration] Converted {} product(s) to multi-category", done);

            // Cached products/lists still hold the old shape — clear them once.
            for (String cache : List.of(CacheConfig.PRODUCTS_LIST_CACHE, CacheConfig.PRODUCT_DETAIL_CACHE,
                    "categoryTree", "featuredCategories")) {
                var c = cacheManager.getCache(cache);
                if (c != null) c.clear();
            }
        });
    }

    private void step(String name, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("[CategoryMigration] Step '{}' failed (app continues): {}", name, e.getMessage());
        }
    }
}