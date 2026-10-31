package com.paynest.events;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Common envelope for every message on Kafka (product spec §7).
 *
 * @param eventId    unique id; consumers use it to drop duplicates
 * @param eventType  simple class name of the payload, e.g. {@code TransferCompleted}
 * @param occurredAt when the producing service wrote the event
 * @param version    schema version of the payload
 * @param payload    the message body as JSON
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        int version,
        JsonNode payload) {

    public static final int CURRENT_VERSION = 1;
}
