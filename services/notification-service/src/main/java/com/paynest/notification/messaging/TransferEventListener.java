package com.paynest.notification.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import com.paynest.events.EventCodec;
import com.paynest.events.Topics;
import com.paynest.notification.domain.DeadLetter;
import com.paynest.notification.domain.DeadLetterRepository;
import com.paynest.notification.service.NotificationHandler;

/**
 * Non-blocking retries (README §3.4): a failed event moves to a retry topic and is tried again
 * after a delay, without blocking newer events. After the last attempt it lands in the DLT.
 * Defaults: 4 attempts, delays 1s, 5s, 25s.
 */
@Component
public class TransferEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransferEventListener.class);

    private final NotificationHandler handler;
    private final DeadLetterRepository deadLetters;
    private final EventCodec codec;

    public TransferEventListener(NotificationHandler handler, DeadLetterRepository deadLetters, EventCodec codec) {
        this.handler = handler;
        this.deadLetters = deadLetters;
        this.codec = codec;
    }

    @RetryableTopic(
            attempts = "${paynest.notification.retry.attempts:4}",
            backOff = @BackOff(
                    delayString = "${paynest.notification.retry.delay-ms:1000}",
                    multiplierString = "${paynest.notification.retry.multiplier:5.0}"),
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dlt",
            // A message that cannot be parsed will never succeed: no retry, straight to the DLT.
            exclude = JacksonException.class)
    @KafkaListener(topics = Topics.TRANSFER_EVENTS, groupId = "notification-service")
    public void onEvent(String message) {
        handler.handle(codec.decode(message));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        String error = header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        if (error == null) {
            error = header(record, KafkaHeaders.EXCEPTION_MESSAGE);
        }
        log.error("Event moved to DLT, needs manual handling. key={} error={}", record.key(), error);
        deadLetters.save(new DeadLetter(UUID.randomUUID().toString(), record.topic(), record.key(), record.value(),
                error, Instant.now()));
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
