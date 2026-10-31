package com.paynest.notification.sender;

import com.paynest.notification.domain.Channel;
import com.paynest.notification.domain.Notification;

public interface NotificationSender {

    Channel channel();

    /** @throws TemporarySendException when the channel is down; the event will be retried */
    void send(Notification notification);
}
