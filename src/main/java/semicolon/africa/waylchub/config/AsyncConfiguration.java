package semicolon.africa.waylchub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableRetry
@EnableScheduling
public class AsyncConfiguration {

    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Minimum threads always alive
        executor.setCorePoolSize(10);

        // Maximum threads during peak load
        executor.setMaxPoolSize(50);

        // Queue size for pending tasks
        executor.setQueueCapacity(100);

        // Thread name prefix for debugging (shows in logs and thread dumps)
        executor.setThreadNamePrefix("AsyncEvent-");

        // Idle threads above core size die after 60 seconds of inactivity
        executor.setKeepAliveSeconds(60);

        // On shutdown: finish in-flight async tasks before JVM exits
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Maximum time to wait for in-flight tasks during shutdown
        executor.setAwaitTerminationSeconds(30);

        // When all threads are busy AND the queue is full:
        // CallerRunsPolicy makes the *calling* thread run the task instead of
        // throwing TaskRejectedException. Provides back-pressure without data loss.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}