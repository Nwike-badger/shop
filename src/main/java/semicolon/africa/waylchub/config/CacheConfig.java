package semicolon.africa.waylchub.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
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

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY
                );

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

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofHours(1)))
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }

    /**
     * Resilient cache error handler — logs and swallows Redis errors instead
     * of propagating them as exceptions.
     *
     * WITHOUT THIS: if Redis is unreachable (wrong credentials, network blip,
     * Redis Cloud cold start), @Cacheable throws a connection exception which
     * bubbles up through the controller and returns a 400/500 to the client —
     * even though MongoDB is perfectly healthy.
     *
     * WITH THIS: any Redis failure is logged as a warning and the method
     * executes normally against MongoDB. The response is never cached for that
     * request, but the client gets a correct 200.
     *
     * This is the standard production pattern: Redis is a performance layer,
     * not a hard dependency. Downtime should degrade gracefully, not break the app.
     */


    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] GET failed on '{}' key='{}': {}", cache.getName(), key, e.getMessage());
                // swallow — method will execute and hit MongoDB
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("[Cache] PUT failed on '{}' key='{}': {}", cache.getName(), key, e.getMessage());
                // swallow — response already returned to client, just won't be cached
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] EVICT failed on '{}' key='{}': {}", cache.getName(), key, e.getMessage());
                // swallow — eviction failure means stale data may linger, but won't crash
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("[Cache] CLEAR failed on '{}': {}", cache.getName(), e.getMessage());
                // swallow
            }
        };
    }
}