package semicolon.africa.waylchub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableRetry
@EnableScheduling
@SpringBootApplication(exclude = { RedisRepositoriesAutoConfiguration.class })
public class WaylchubApplication {

	public static void main(String[] args) {
		SpringApplication.run(WaylchubApplication.class, args);
	}

}