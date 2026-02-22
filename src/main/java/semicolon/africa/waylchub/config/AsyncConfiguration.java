package semicolon.africa.waylchub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;

/**
 * ✅ CRITICAL FIX #2: Enable async processing and retry logic
 *
 * @EnableAsync:
 * - Allows @Async methods to run in background threads
 * - Critical for event-driven parent aggregate updates
 *
 * @EnableRetry:
 * - Allows @Retryable on event listeners
 * - Handles OptimisticLockingFailureException automatically
 *
 * Thread Pool Configuration:
 * - Core size: 10 threads always available
 * - Max size: 50 threads under peak load
 * - Queue: 100 tasks waiting before rejection
 *
 * This means:
 * - Up to 50 parent updates can run concurrently
 * - 100 more can wait in queue
 * - Beyond that, new updates are rejected (logged, not failed)
 */
@Configuration
@EnableAsync
@EnableRetry
@EnableScheduling
public class AsyncConfiguration {

    /**
     * Custom thread pool for async operations
     *
     * WHY CUSTOM POOL:
     * Default Spring async executor uses SimpleAsyncTaskExecutor which
     * creates unlimited threads. Under load, this causes:
     * - Thread exhaustion
     * - Context switching overhead
     * - Memory exhaustion
     *
     * This bounded pool ensures:
     * - Controlled concurrency
     * - Predictable resource usage
     * - Graceful degradation under extreme load
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Minimum threads always alive
        executor.setCorePoolSize(10);

        // Maximum threads during peak load
        executor.setMaxPoolSize(50);

        // Queue size for pending tasks
        executor.setQueueCapacity(100);

        // Thread name prefix for debugging
        executor.setThreadNamePrefix("AsyncEvent-");

        // Initialize the executor
        executor.initialize();

        return executor;
    }
}