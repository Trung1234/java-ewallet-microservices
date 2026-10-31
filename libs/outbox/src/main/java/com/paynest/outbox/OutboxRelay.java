package com.paynest.outbox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polling publisher: reads unpublished outbox rows and sends them to Kafka.
 * <p>
 * Rows are marked as published only after Kafka acknowledged them ({@code acks=all}). If Kafka is
 * down the transaction rolls back and the same rows are sent on the next poll, so no event is lost
 * (INV-5). A row can be sent twice (crash after send, before commit): consumers must be idempotent.
 * <p>
 * Set {@code paynest.outbox.relay-enabled=false} when Debezium CDC publishes the outbox instead.
 */
@Component
@ConditionalOnProperty(name = "paynest.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final int batchSize;
    private final long sendTimeoutMs;
    private volatile boolean failing;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate transaction,
                       @Value("${paynest.outbox.batch-size:100}") int batchSize,
                       @Value("${paynest.outbox.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.transaction = transaction;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${paynest.outbox.poll-interval-ms:500}")
    public void publishPending() {
        try {
            Integer sent;
            do {
                sent = transaction.execute(status -> publishBatch());
            } while (sent != null && sent == batchSize);
            if (failing) {
                log.info("Outbox relay recovered, Kafka is reachable again");
                failing = false;
            }
        } catch (RuntimeException e) {
            if (!failing) {
                log.warn("Outbox relay cannot publish, rows stay in the outbox and will be retried: {}", e.getMessage());
                failing = true;
            }
        }
    }

    private int publishBatch() {
        List<OutboxEvent> batch = outbox.lockNextBatch(batchSize);
        List<CompletableFuture<?>> sends = batch.stream()
                .<CompletableFuture<?>>map(event -> kafka.send(event.getTopic(), event.getAggregateId(), event.getPayload()))
                .toList();
        for (CompletableFuture<?> send : sends) {
            await(send);
        }
        Instant now = Instant.now();
        batch.forEach(event -> event.markPublished(now));
        return batch.size();
    }

    private void await(CompletableFuture<?> send) {
        try {
            send.get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox events", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka did not acknowledge outbox event", e);
        }
    }
}
