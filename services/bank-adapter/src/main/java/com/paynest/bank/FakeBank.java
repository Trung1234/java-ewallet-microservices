package com.paynest.bank;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * A bank that misbehaves on purpose, so callers have to deal with real-world failures:
 * <ul>
 *   <li>{@code failureRate}: answers 503 without doing anything (safe to retry)</li>
 *   <li>{@code timeoutRate}: does the work, then answers too late (the caller times out although
 *       the money moved; a retry with the same reference must not move it twice)</li>
 *   <li>amounts above {@code maxAmount} are declined with 422 (retrying will not help)</li>
 * </ul>
 * Operations are idempotent on {@code reference}. State is in memory: a restart forgets it.
 */
@Component
public class FakeBank {

    private static final Logger log = LoggerFactory.getLogger(FakeBank.class);

    private final Map<UUID, BankReceipt> processed = new ConcurrentHashMap<>();
    private volatile double failureRate;
    private volatile double timeoutRate;
    private final Duration slowResponse;
    private final long maxAmount;

    public FakeBank(@Value("${paynest.bank.failure-rate:0.2}") double failureRate,
                    @Value("${paynest.bank.timeout-rate:0.1}") double timeoutRate,
                    @Value("${paynest.bank.slow-response:5s}") Duration slowResponse,
                    @Value("${paynest.bank.max-amount:100000000}") long maxAmount) {
        this.failureRate = failureRate;
        this.timeoutRate = timeoutRate;
        this.slowResponse = slowResponse;
        this.maxAmount = maxAmount;
    }

    public BankReceipt process(String operation, UUID reference, long amount) {
        BankReceipt previous = processed.get(reference);
        if (previous != null) {
            log.info("{} {} already processed, returning the same receipt", operation, reference);
            return previous;
        }
        if (amount > maxAmount) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Amount above bank limit " + maxAmount);
        }
        double dice = ThreadLocalRandom.current().nextDouble();
        if (dice < failureRate) {
            log.warn("{} {}: simulated outage (503)", operation, reference);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Simulated bank outage");
        }
        BankReceipt receipt = processed.computeIfAbsent(reference,
                ref -> new BankReceipt(ref, operation, amount, "SUCCEEDED", Instant.now()));
        if (dice < failureRate + timeoutRate) {
            log.warn("{} {}: processed, but answering slowly ({})", operation, reference, slowResponse);
            sleep(slowResponse);
        }
        log.info("{} {} of {} succeeded", operation, reference, amount);
        return receipt;
    }

    public Chaos chaos() {
        return new Chaos(failureRate, timeoutRate);
    }

    public Chaos updateChaos(Chaos chaos) {
        this.failureRate = chaos.failureRate();
        this.timeoutRate = chaos.timeoutRate();
        log.info("Chaos settings changed: {}", chaos);
        return chaos();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record BankReceipt(UUID reference, String operation, long amount, String status, Instant processedAt) {
    }

    public record Chaos(double failureRate, double timeoutRate) {
    }
}
