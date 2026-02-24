package semicolon.africa.waylchub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry

public class WaylchubApplication {

	public static void main(String[] args) {
		SpringApplication.run(WaylchubApplication.class, args);
	}

}