package com.paynest.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.paynest.events.EventCodec;
import com.paynest.events.EventEnvelope;
import com.paynest.events.TransferMessages.TransferCompleted;
import com.paynest.events.TransferMessages.TransferFailed;
import com.paynest.notification.domain.Notification;
import com.paynest.notification.domain.NotificationRepository;
import com.paynest.notification.domain.ProcessedEvent;
import com.paynest.notification.domain.ProcessedEventRepository;
import com.paynest.notification.sender.NotificationSender;

/**
 * Turns transfer events into notifications (README §6 flow).
 * <p>
 * Idempotent in two ways: an event already in {@code processed_events} is skipped, and each
 * notification has an id derived from (event, user, channel). If the e-mail channel fails after the
 * in-app notification was saved, the retried event skips the in-app one and only retries e-mail.
 */
@Service
public class NotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationHandler.class);

    private final NotificationRepository notifications;
    private final ProcessedEventRepository processedEvents;
    private final PreferenceService preferences;
    private final Templates templates;
    private final List<NotificationSender> senders;
    private final EventCodec codec;

    public NotificationHandler(NotificationRepository notifications, ProcessedEventRepository processedEvents,
                               PreferenceService preferences, Templates templates, List<NotificationSender> senders,
                               EventCodec codec) {
        this.notifications = notifications;
        this.processedEvents = processedEvents;
        this.preferences = preferences;
        this.templates = templates;
        this.senders = senders;
        this.codec = codec;
    }

    public void handle(EventEnvelope envelope) {
        String eventId = envelope.eventId().toString();
        if (processedEvents.existsById(eventId)) {
            log.debug("Event {} already handled", eventId);
            return;
        }
        switch (codec.payloadOf(envelope)) {
            case TransferCompleted event -> {
                deliver(envelope, event.fromUserId(), event.transferId(), templates.transferSent(event));
                deliver(envelope, event.toUserId(), event.transferId(), templates.transferReceived(event));
            }
            case TransferFailed event -> deliver(envelope, event.fromUserId(), event.transferId(), templates.transferFailed(event));
            case null, default -> log.debug("Ignoring {}", envelope.eventType());
        }
        processedEvents.save(new ProcessedEvent(eventId, Instant.now()));
    }

    private void deliver(EventEnvelope envelope, String userId, UUID transferId, Templates.Content content) {
        PreferenceView preference = preferences.get(userId);
        for (NotificationSender sender : senders) {
            if (!preference.allows(sender.channel())
                    || notifications.existsById(Notification.idFor(envelope.eventId(), userId, sender.channel()))) {
                continue;
            }
            var notification = new Notification(envelope.eventId(), userId, sender.channel(), envelope.eventType(),
                    transferId.toString(), content.title(), content.body());
            sender.send(notification);
            notification.markSent();
            notifications.save(notification);
        }
    }
}
