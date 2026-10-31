package com.paynest.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;

/**
 * Row of the {@code outbox} table (README §4.3). The column names follow Debezium's outbox
 * defaults; {@code topic} and {@code published_at} are added for the polling relay.
 */
@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregatetype", nullable = false)
    private String aggregateType;

    /** Used as the Kafka message key. */
    @Column(name = "aggregateid", nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String type;

    /** The full {@code EventEnvelope} as JSON. */
    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;

    @Column(nullable = false)
    private String topic;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    OutboxEvent(UUID id, String aggregateType, String aggregateId, String type, String payload, String topic) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.topic = topic;
        this.createdAt = Instant.now();
    }

    void markPublished(Instant when) {
        this.publishedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public String getTopic() {
        return topic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
