package com.paynest.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scans {@code com.paynest} so the shared common-web library is picked up too.
 */
@SpringBootApplication(scanBasePackages = "com.paynest")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
