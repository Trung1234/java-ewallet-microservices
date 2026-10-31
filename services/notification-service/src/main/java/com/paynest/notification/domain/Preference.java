package com.paynest.notification.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("preferences")
public record Preference(@Id String userId, boolean emailEnabled, boolean pushEnabled, Instant updatedAt) {
}
