package semicolon.africa.waylchub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import semicolon.africa.waylchub.model.product.Product;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    /**
     * Runs once after the application is fully started and ready.
     *
     * WHY ApplicationReadyEvent, NOT ContextRefreshedEvent:
     *   ContextRefreshedEvent fires every time ANY ApplicationContext is refreshed.
     *   In a standard Spring Boot app that's once, but in apps that use a parent/child
     *   context (e.g., Spring MVC, some test configurations), it fires TWICE per startup.
     *   That means dropIndex + createIndex runs twice, which is harmless but wasteful
     *   and produces confusing logs.
     *   ApplicationReadyEvent fires exactly once — after the entire context is up and
     *   the app is ready to serve traffic. It's the correct hook for one-time startup work.
     *
     * IDEMPOTENCY:
     *   MongoDB's createIndex is idempotent when the definition (fields + weights) is
     *   identical to an existing index. If the definition changes, MongoDB throws
     *   IndexOptionsConflict — caught below with a clear error message.
     *   This means it is safe to call on every startup with no performance concern
     *   after the first run (MongoDB returns immediately if the index already exists).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initIndicesAfterStartup() {
        log.info("Verifying MongoDB indexes...");

        // ── Drop stale text indexes that conflict with our weighted definition ──────
        // MongoDB only allows ONE text index per collection. If any other text index
        // (annotation-created, old migration, or wildcard) exists, createIndex below
        // will throw IndexOptionsConflict. We attempt to drop all known legacy names.
        for (String legacyName : new String[]{ "text_search", "$**_text" }) {
            try {
                mongoTemplate.indexOps(Product.class).dropIndex(legacyName);
                log.info("Dropped legacy text index '{}'", legacyName);
            } catch (Exception ignored) {
                // Index didn't exist — nothing to do
            }
        }

        // ── Create the weighted smart text index ─────────────────────────────────
        try {
            mongoTemplate.indexOps(Product.class).createIndex(
                    new TextIndexDefinition.TextIndexDefinitionBuilder()
                            .named("products_smart_text_idx")
                            .onField("name",         10F)  // exact product name — highest priority
                            .onField("brandName",     8F)  // brand search ("levi", "zara")
                            .onField("categoryName",  5F)  // category-term search ("jeans", "shoes")
                            .onField("tags",          4F)  // curated keywords from admin
                            .onField("description",   1F)  // lowest — prose, lots of noise
                            .build()
            );
            log.info("Smart text index verified.");

        } catch (Exception e) {
            // NOT rethrowing — a missing text index degrades search quality but does not
            // break the application. SmartSearchService falls back to regex search automatically.
            // Investigate and fix manually if this error appears in production logs.
            log.error("Failed to create smart text index — search quality will be degraded: {}", e.getMessage());
        }
    }
}