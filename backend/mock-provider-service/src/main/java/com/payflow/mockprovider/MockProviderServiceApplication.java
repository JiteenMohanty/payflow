package com.payflow.mockprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.util.Random;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockProviderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockProviderServiceApplication.class, args);
    }

    // A real bean, not ThreadLocalRandom.current() called inline in
    // ChaosFilter, specifically so tests can inject a seeded or mocked
    // instance instead of asserting on genuinely random behavior.
    @Bean
    public Random random() {
        return new Random();
    }
}
