package com.paynest.outbox;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer support backed by the {@code processed_events} table.
 * <p>
 * Call {@link #markProcessed(UUID)} inside the same transaction as the side effect. If the event
 * was already handled the insert does nothing and the caller skips the work (INV-6). If the
 * transaction rolls back, the mark is rolled back too and the event will be handled on redelivery.
 */
@Component
public class Inbox {

    private final JdbcTemplate jdbc;

    public Inbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code true} the first time an event id is seen, {@code false} for a duplicate */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId) {
        return jdbc.update("INSERT INTO processed_events (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId) == 1;
    }
}
