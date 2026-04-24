package semicolon.africa.waylchub.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * Transparent logging layer over any {@link CacheManager}.
 *
 * Wrap your real CacheManager with this one in {@link CacheConfig} and every
 * cache interaction will be logged at DEBUG level (HIT / MISS) and INFO level
 * (PUT / EVICT / CLEAR), giving you full visibility into what Redis is doing
 * without touching any service code.
 *
 * Log levels are intentionally asymmetric:
 *  - HIT  → INFO   (confirm Redis is actually serving traffic)
 *  - MISS → DEBUG  (expected on first access; noisy at INFO in production)
 *  - PUT  → INFO   (confirm entries are being written to Redis)
 *  - EVICT/CLEAR → INFO (confirm invalidation is happening)
 */
public class LoggingCacheManager implements CacheManager {

    private final CacheManager delegate;

    public LoggingCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        return cache != null ? new LoggingCache(cache) : null;
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    // =========================================================================
    // Inner class — one instance per named cache
    // =========================================================================

    @Slf4j
    static class LoggingCache implements Cache {

        private final Cache delegate;

        LoggingCache(Cache delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        // -----------------------------------------------------------------
        // GET — the read path; HIT at INFO, MISS at DEBUG
        // -----------------------------------------------------------------

        @Override
        public ValueWrapper get(Object key) {
            ValueWrapper result = delegate.get(key);
            if (result != null) {
                log.info("[Cache HIT]  cache='{}' key='{}'", getName(), key);
            } else {
                log.debug("[Cache MISS] cache='{}' key='{}'", getName(), key);
            }
            return result;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            T result = delegate.get(key, type);
            if (result != null) {
                log.info("[Cache HIT]  cache='{}' key='{}' type='{}'",
                        getName(), key, type.getSimpleName());
            } else {
                log.debug("[Cache MISS] cache='{}' key='{}' type='{}'",
                        getName(), key, type.getSimpleName());
            }
            return result;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            // Spring calls this for sync=true @Cacheable methods.
            // We can't intercept the hit/miss split here without re-implementing
            // the load-or-store logic, so we delegate and log the outcome.
            try {
                T result = delegate.get(key, valueLoader);
                log.info("[Cache LOAD] cache='{}' key='{}' (sync load completed)",
                        getName(), key);
                return result;
            } catch (Cache.ValueRetrievalException e) {
                log.warn("[Cache LOAD FAILED] cache='{}' key='{}': {}",
                        getName(), key, e.getCause().getMessage());
                throw e;
            }
        }

        // -----------------------------------------------------------------
        // PUT — the write path
        // -----------------------------------------------------------------

        @Override
        public void put(Object key, Object value) {
            delegate.put(key, value);
            log.info("[Cache PUT]   cache='{}' key='{}'", getName(), key);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            ValueWrapper result = delegate.putIfAbsent(key, value);
            if (result == null) {
                log.info("[Cache PUT]   cache='{}' key='{}' (putIfAbsent — written)",
                        getName(), key);
            } else {
                log.debug("[Cache SKIP]  cache='{}' key='{}' (putIfAbsent — already present)",
                        getName(), key);
            }
            return result;
        }

        // -----------------------------------------------------------------
        // EVICT / CLEAR
        // -----------------------------------------------------------------

        @Override
        public void evict(Object key) {
            delegate.evict(key);
            log.info("[Cache EVICT] cache='{}' key='{}'", getName(), key);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            boolean removed = delegate.evictIfPresent(key);
            if (removed) {
                log.info("[Cache EVICT] cache='{}' key='{}' (was present)", getName(), key);
            }
            return removed;
        }

        @Override
        public void clear() {
            delegate.clear();
            log.info("[Cache CLEAR] cache='{}'", getName());
        }

        @Override
        public boolean invalidate() {
            boolean invalidated = delegate.invalidate();
            log.info("[Cache INVALIDATE] cache='{}' result={}", getName(), invalidated);
            return invalidated;
        }
    }
}