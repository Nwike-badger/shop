package semicolon.africa.waylchub.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FIX #1: @Cacheable will silently fail (or throw) without a registered CacheManager.
 *
 * This uses a simple in-memory cache (ConcurrentMap) — zero extra dependencies,
 * zero config, works immediately.
 *
 * ✅ To upgrade to Redis later, just swap the @Bean body — no other code changes needed.
 */
@Configuration
@EnableCaching   // ← THIS WAS MISSING. Without it, @Cacheable is ignored.
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // Registers the two cache buckets used in CategoryService
        return new ConcurrentMapCacheManager("categoryTree", "featuredCategories");
    }
}