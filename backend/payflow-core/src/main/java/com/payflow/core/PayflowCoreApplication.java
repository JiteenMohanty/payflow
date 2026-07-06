package com.payflow.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * UserDetailsServiceAutoConfiguration is excluded because authentication is
 * handled entirely by our own filters (API key + JWT) - there is no
 * UserDetailsService/AuthenticationManager-based login path to back it.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class PayflowCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayflowCoreApplication.class, args);
    }
}
