package com.paynest.notification.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Idempotent consumer: ids of events already handled. */
@Document("processed_events")
public record ProcessedEvent(@Id String eventId, Instant processedAt) {
}
