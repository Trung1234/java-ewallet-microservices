package com.paynest.notification.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paynest.notification.domain.Channel;
import com.paynest.notification.domain.Notification;
import com.paynest.notification.domain.NotificationRepository;
import com.paynest.notification.service.PreferenceService;
import com.paynest.notification.service.PreferenceView;
import com.paynest.web.ApiException;

@RestController
public class NotificationController {

    private final NotificationRepository notifications;
    private final PreferenceService preferences;

    public NotificationController(NotificationRepository notifications, PreferenceService preferences) {
        this.notifications = notifications;
        this.preferences = preferences;
    }

    /** The in-app inbox, newest first. */
    @GetMapping("/notifications")
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        var pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        return notifications.findByUserIdAndChannelOrderByCreatedAtDesc(jwt.getSubject(), Channel.IN_APP, pageRequest)
                .stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/notifications/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCount(notifications.countByUserIdAndChannelAndReadFalse(jwt.getSubject(), Channel.IN_APP));
    }

    @PatchMapping("/notifications/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        Notification notification = notifications.findById(id)
                .filter(n -> n.getUserId().equals(jwt.getSubject()))
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification " + id + " not found"));
        notification.markRead();
        return NotificationResponse.from(notifications.save(notification));
    }

    @GetMapping("/preferences")
    public PreferenceView getPreferences(@AuthenticationPrincipal Jwt jwt) {
        return preferences.get(jwt.getSubject());
    }

    @PutMapping("/preferences")
    public PreferenceView updatePreferences(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferenceRequest request) {
        return preferences.update(jwt.getSubject(), request.emailEnabled(), request.pushEnabled());
    }

    public record PreferenceRequest(@NotNull Boolean emailEnabled, @NotNull Boolean pushEnabled) {
    }

    public record UnreadCount(long count) {
    }

    public record NotificationResponse(String id, String title, String body, String eventType, String transferId,
                                       boolean read, Instant createdAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getTitle(), n.getBody(), n.getEventType(), n.getTransferId(),
                    n.isRead(), n.getCreatedAt());
        }
    }
}
