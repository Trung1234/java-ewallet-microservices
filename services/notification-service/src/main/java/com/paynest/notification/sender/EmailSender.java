package com.paynest.notification.sender;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.paynest.notification.domain.Channel;
import com.paynest.notification.domain.Notification;

/**
 * Fake e-mail channel: logs instead of sending. {@code paynest.notification.email.failure-rate}
 * makes it fail like an unreachable SMTP server, to exercise retry topics and the DLT.
 */
@Component
@Order(2)
public class EmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final double failureRate;

    public EmailSender(@Value("${paynest.notification.email.failure-rate:0.0}") double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new TemporarySendException("SMTP server not reachable (simulated)");
        }
        log.info("[EMAIL] to user {}: {} - {}", notification.getUserId(), notification.getTitle(), notification.getBody());
    }
}
