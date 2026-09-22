package se.segersten.wreckage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WreckageApplication {

	public static void main(String[] args) {
		SpringApplication.run(WreckageApplication.class, args);
	}

}
