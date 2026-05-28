package com.example.kie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KieApplication {

	public static void main(String[] args) {
		SpringApplication.run(KieApplication.class, args);
	}

}
