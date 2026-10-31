package com.paynest.notification.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notifications")
@CompoundIndex(name = "user_channel_created", def = "{'userId': 1, 'channel': 1, 'createdAt': -1}")
public class Notification {

    public enum Status { PENDING, SENT }

    /** Derived from (event, user, channel): handling the same event again cannot create a second copy. */
    @Id
    private String id;

    private String userId;
    private Channel channel;
    private String eventType;
    private String transferId;
    private String title;
    private String body;
    private Status status;
    private boolean read;
    private Instant createdAt;
    private Instant sentAt;

    protected Notification() {
    }

    public Notification(UUID eventId, String userId, Channel channel, String eventType, String transferId,
                        String title, String body) {
        this.id = idFor(eventId, userId, channel);
        this.userId = userId;
        this.channel = channel;
        this.eventType = eventType;
        this.transferId = transferId;
        this.title = title;
        this.body = body;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public static String idFor(UUID eventId, String userId, Channel channel) {
        return UUID.nameUUIDFromBytes((eventId + ":" + userId + ":" + channel).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public void markSent() {
        status = Status.SENT;
        sentAt = Instant.now();
    }

    public void markRead() {
        read = true;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
