package com.soksak.soksak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class SoksakApplication {

	public static void main(String[] args) {
		SpringApplication.run(SoksakApplication.class, args);
	}

}
