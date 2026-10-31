package com.paynest.transfer.service;

import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Business metrics of the saga. Values are recorded after the commit: a saga step that rolls back
 * is retried by the Kafka listener and must not be counted twice.
 */
@Component
public class TransferMetrics {

    private final MeterRegistry registry;
    private final Counter completed;
    private final Timer latency;

    public TransferMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.completed = registry.counter("paynest.transfer.completed");
        // CloudWatch only gets a percentile when the application computes it.
        this.latency = Timer.builder("paynest.transfer.latency").publishPercentiles(0.95).register(registry);
    }

    public void completed(Duration took) {
        afterCommit(() -> {
            completed.increment();
            latency.record(took);
        });
    }

    /** @param reason a failure code such as INSUFFICIENT_FUNDS; there are few, so the tag stays cheap */
    public void failed(String reason) {
        String tag = reason == null ? "UNKNOWN" : reason;
        afterCommit(() -> registry.counter("paynest.transfer.failed", "reason", tag).increment());
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
