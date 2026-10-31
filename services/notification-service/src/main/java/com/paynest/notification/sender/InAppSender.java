package com.paynest.notification.sender;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.paynest.notification.domain.Channel;
import com.paynest.notification.domain.Notification;

/** In-app delivery is the stored notification itself, read via {@code GET /notifications}. Runs first. */
@Component
@Order(1)
public class InAppSender implements NotificationSender {

    @Override
    public Channel channel() {
        return Channel.IN_APP;
    }

    @Override
    public void send(Notification notification) {
        // nothing to do: saving the notification makes it visible in the inbox
    }
}
