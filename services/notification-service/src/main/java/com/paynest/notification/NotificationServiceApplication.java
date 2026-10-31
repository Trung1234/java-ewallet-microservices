package com.paynest.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Scans {@code com.paynest} so the shared common-web library is picked up too.
 */
@SpringBootApplication(scanBasePackages = "com.paynest")
@EnableCaching
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
