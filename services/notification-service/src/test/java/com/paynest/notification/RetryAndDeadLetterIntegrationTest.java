package com.paynest.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import com.paynest.events.EventCodec;
import com.paynest.events.Topics;
import com.paynest.events.TransferMessages.TransferCompleted;
import com.paynest.notification.domain.Channel;
import com.paynest.notification.domain.DeadLetter;
import com.paynest.notification.domain.DeadLetterRepository;
import com.paynest.notification.domain.Notification;
import com.paynest.notification.domain.NotificationRepository;

/**
 * T-NTF-4 / T-EVT-3 through real Kafka: e-mail always fails, so the event goes through the retry
 * topics and ends in the DLT. The in-app notifications were saved on the first attempt.
 */
@SpringBootTest(properties = {
        "paynest.notification.email.failure-rate=1.0",
        "paynest.notification.retry.attempts=3",
        "paynest.notification.retry.delay-ms=200",
        "paynest.notification.retry.multiplier=1.0"})
@Import(TestcontainersConfig.class)
class RetryAndDeadLetterIntegrationTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    EventCodec codec;

    @Autowired
    DeadLetterRepository deadLetters;

    @Autowired
    NotificationRepository notifications;

    @Test
    void temporaryFailureIsRetriedThenSentToDlt() throws Exception {
        UUID transferId = UUID.randomUUID();
        var event = new TransferCompleted(transferId, UUID.randomUUID(), "alice", UUID.randomUUID(), "bob", 1_000);
        kafka.send(Topics.TRANSFER_EVENTS, transferId.toString(), codec.encode(UUID.randomUUID(), event)).get();

        DeadLetter deadLetter = awaitDeadLetter(transferId.toString(), Duration.ofSeconds(60));

        assertThat(deadLetter.topic()).isEqualTo(Topics.TRANSFER_EVENTS + ".dlt");
        assertThat(deadLetter.error()).contains("SMTP server not reachable");
        assertThat(notifications.findByTransferId(transferId.toString()))
                .extracting(Notification::getChannel)
                .containsOnly(Channel.IN_APP);
    }

    private DeadLetter awaitDeadLetter(String key, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            var match = deadLetters.findAll().stream().filter(d -> key.equals(d.key())).findFirst();
            if (match.isPresent()) {
                return match.get();
            }
            Thread.sleep(250);
        }
        throw new AssertionError("No dead letter for " + key);
    }
}
