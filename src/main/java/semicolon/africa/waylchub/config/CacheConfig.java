package semicolon.africa.waylchub.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    // Cache Names
    public static final String MONNIFY_TOKEN_CACHE = "monnifyAccessToken";
    public static final String CATEGORY_TREE_CACHE = "categoryTree";
    public static final String FEATURED_CATEGORIES_CACHE = "featuredCategories";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        // 1. Payment Gateway Cache (Strict 55-minute expiry)
        CaffeineCache tokenCache = new CaffeineCache(MONNIFY_TOKEN_CACHE,
                Caffeine.newBuilder()
                        .expireAfterWrite(55, TimeUnit.MINUTES)
                        .maximumSize(10)
                        .recordStats()
                        .build());

        // 2. Category Caches (Longer expiry, or just size-based eviction)
        CaffeineCache categoryTreeCache = new CaffeineCache(CATEGORY_TREE_CACHE,
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS) // Example: Cache categories for a day
                        .maximumSize(100)
                        .build());

        CaffeineCache featuredCategoriesCache = new CaffeineCache(FEATURED_CATEGORIES_CACHE,
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(50)
                        .build());

        // Register all caches with the manager
        cacheManager.setCaches(Arrays.asList(tokenCache, categoryTreeCache, featuredCategoriesCache));

        return cacheManager;
    }
}