package semicolon.africa.waylchub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;


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

        // Thread name prefix for debugging
        executor.setThreadNamePrefix("AsyncEvent-");

        // Initialize the executor
        executor.initialize();

        return executor;
    }
}