package dev.connor.tanchi_snake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TanchiSnakeApplication {

	public static void main(String[] args) {
		SpringApplication.run(TanchiSnakeApplication.class, args);
	}

}
