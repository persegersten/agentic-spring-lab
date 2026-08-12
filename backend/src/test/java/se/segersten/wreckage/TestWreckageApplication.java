package se.segersten.wreckage;

import org.springframework.boot.SpringApplication;

public class TestWreckageApplication {

	public static void main(String[] args) {
		SpringApplication.from(WreckageApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
