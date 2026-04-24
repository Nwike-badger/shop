package semicolon.africa.waylchub.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String MONNIFY_TOKEN_CACHE       = "monnifyAccessToken";
    public static final String CATEGORY_TREE_CACHE       = "categoryTree";
    public static final String FEATURED_CATEGORIES_CACHE = "featuredCategories";
    public static final String BRANDS_CACHE              = "brands";
    public static final String SITE_CONFIG_CACHE         = "siteConfig";
    public static final String PRODUCTS_LIST_CACHE       = "productsList";
    public static final String PRODUCT_DETAIL_CACHE      = "productDetail";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // WRAPPER_ARRAY: type id always comes first — no token-ordering conflict
                // with Map<String,Object> or bare Object fields (e.g. Product.specifications)
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.WRAPPER_ARRAY);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("exploreaba:")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(MONNIFY_TOKEN_CACHE,        base.entryTtl(Duration.ofMinutes(55)));
        cacheConfigs.put(CATEGORY_TREE_CACHE,        base.entryTtl(Duration.ofHours(24)));
        cacheConfigs.put(FEATURED_CATEGORIES_CACHE,  base.entryTtl(Duration.ofHours(24)));
        cacheConfigs.put(BRANDS_CACHE,               base.entryTtl(Duration.ofHours(24)));
        cacheConfigs.put(SITE_CONFIG_CACHE,          base.entryTtl(Duration.ofHours(24)));
        cacheConfigs.put(PRODUCTS_LIST_CACHE,        base.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put(PRODUCT_DETAIL_CACHE,       base.entryTtl(Duration.ofHours(1)));

        RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofHours(1)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();

        // Wrap with LoggingCacheManager so every HIT, MISS, PUT, and EVICT
        // is logged without any changes to service code.
        // HIT  → INFO  (confirm Redis is serving traffic)
        // MISS → DEBUG (first-access; noisy at INFO — lower to DEBUG in prod if needed)
        // PUT  → INFO  (confirm entries are being written to Redis)
        return new LoggingCacheManager(redisCacheManager);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] GET failed on '{}' key='{}': {}", cache.getName(), key, e.getMessage());
            }
            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("[Cache] PUT failed on '{}' key='{}': {}", cache.getName(), key, e.getMessage());
            }
            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] EVICT failed on '{}' key='{}': {}", cache.getName(), key, e.getMessage());
            }
            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("[Cache] CLEAR failed on '{}': {}", cache.getName(), e.getMessage());
            }
        };
    }
}