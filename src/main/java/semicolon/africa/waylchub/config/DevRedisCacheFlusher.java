//package semicolon.africa.waylchub.config;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.context.event.ApplicationReadyEvent;
//import org.springframework.context.annotation.Profile;
//import org.springframework.context.event.EventListener;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//@Profile("!prod")
//@RequiredArgsConstructor
//public class DevRedisCacheFlusher {
//
//    private final RedisConnectionFactory connectionFactory;
//
//    @EventListener(ApplicationReadyEvent.class)
//    public void flushCacheOnStartup() {
//        log.info("[DEV] Flushing Redis after DataSeeder...");
//        try (var connection = connectionFactory.getConnection()) {
//            connection.serverCommands().flushDb();
//            log.info("[DEV] Redis FLUSHDB complete — cache is clean and in sync with MongoDB.");
//        } catch (Exception e) {
//            // Redis being slow is not fatal — stale keys will be evicted by TTL
//            // and cache misses will fall through to MongoDB automatically.
//            // Do NOT throw here — crashing the app over a cache flush is wrong.
//            log.warn("[DEV] Redis flush failed: {}. Stale cache entries will expire via TTL. App continues normally.", e.getMessage());
//        }
//    }
//}