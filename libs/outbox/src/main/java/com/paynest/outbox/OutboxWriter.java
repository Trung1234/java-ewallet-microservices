package com.paynest.outbox;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.paynest.events.EventCodec;
import com.paynest.events.Message;

/**
 * Writes an event to the outbox table. It must be called inside the transaction that changes the
 * business data, so both are committed or rolled back together (no dual write).
 */
@Component
public class OutboxWriter {

    @PersistenceContext
    private EntityManager entityManager;

    private final EventCodec codec;

    public OutboxWriter(EventCodec codec) {
        this.codec = codec;
    }

    /**
     * @param aggregateType the aggregate that produced the message, e.g. {@code "wallet"}
     * @param key           Kafka message key (wallet id or transfer id) that decides the partition
     * @param topic         destination topic
     * @return the event id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String aggregateType, Object key, String topic, Message payload) {
        UUID eventId = UUID.randomUUID();
        entityManager.persist(new OutboxEvent(eventId, aggregateType, key.toString(), EventCodec.typeOf(payload),
                codec.encode(eventId, payload), topic));
        return eventId;
    }
}
