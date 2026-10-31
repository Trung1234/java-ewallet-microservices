package com.paynest.notification.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.paynest.notification.domain.Channel;
import com.paynest.notification.domain.Notification;

/** Fake push channel: logs instead of calling a push provider. */
@Component
@Order(3)
public class PushSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    @Override
    public Channel channel() {
        return Channel.PUSH;
    }

    @Override
    public void send(Notification notification) {
        log.info("[PUSH] to user {}: {}", notification.getUserId(), notification.getTitle());
    }
}
