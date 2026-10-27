package com.dms.secure_dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SecureDmsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecureDmsApplication.class, args);
	}

}
