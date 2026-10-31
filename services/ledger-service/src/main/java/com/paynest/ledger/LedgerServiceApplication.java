package com.paynest.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans {@code com.paynest} so the shared libraries (common-web, outbox) are picked up too.
 */
@SpringBootApplication(scanBasePackages = "com.paynest")
@EntityScan("com.paynest")
@EnableJpaRepositories("com.paynest")
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
