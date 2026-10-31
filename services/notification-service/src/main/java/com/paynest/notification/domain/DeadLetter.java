package com.paynest.notification.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** An event that still failed after all retries; kept for manual handling. */
@Document("dead_letters")
public record DeadLetter(@Id String id, String topic, String key, String payload, String error, Instant receivedAt) {
}
