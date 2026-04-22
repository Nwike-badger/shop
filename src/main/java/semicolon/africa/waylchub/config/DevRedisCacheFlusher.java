package semicolon.africa.waylchub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Flushes all Redis cache entries when the application starts in the
 * default (dev) profile.
 *
 * WHY THIS IS NEEDED:
 * The DataSeeder wipes and re-seeds MongoDB on every restart, so all
 * document IDs change. Without this flush, Redis serves stale cache
 * entries that reference IDs which no longer exist in MongoDB — causing
 * silent 404s and broken category/product responses.
 *
 * SCOPE:
 * Only active on the "default" profile (local dev).
 * Annotate with @Profile("!prod") or @Profile("dev") — whichever matches
 * your environment setup. Remove this bean entirely before deploying to
 * production or staging.
 *
 * HOW TO DISABLE FOR A SPECIFIC RUN:
 * Start with --spring.profiles.active=prod and this bean won't load.
 */
@Slf4j
@Component
@Profile("!prod")   // ← active in default/dev, skipped in prod
@RequiredArgsConstructor
public class DevRedisCacheFlusher {

    private final CacheManager cacheManager;   // ← always available, no extra bean needed

    @EventListener(ApplicationReadyEvent.class)
    public void flushCacheOnStartup() {
        log.info("[DEV] Flushing all caches after DataSeeder...");
        int cleared = 0;
        boolean flushFailed = false;

        for (String name : cacheManager.getCacheNames()) {
            try {
                var cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                    log.info("[DEV] Cleared cache: '{}'", name);
                    cleared++;
                }
            } catch (Exception e) {
                flushFailed = true;
                // Log as ERROR not WARN — stale cache = broken app in dev
                log.error("[DEV] FAILED to clear cache '{}': {}", name, e.getMessage());
            }
        }

        if (flushFailed) {
            // Throw so the startup fails fast and visibly rather than
            // silently serving stale data that doesn't match re-seeded MongoDB
            throw new IllegalStateException(
                    "[DEV] Cache flush failed — Redis and MongoDB are out of sync. " +
                            "Fix Redis connection before starting the app."
            );
        }

        log.info("[DEV] Done — {} cache(s) flushed. Redis and MongoDB are in sync.", cleared);
    }
}