package com.paynest.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import com.paynest.events.EventCodec;
import com.paynest.events.EventEnvelope;
import com.paynest.events.Message;
import com.paynest.events.Topics;
import com.paynest.events.WalletMessages.CaptureFunds;
import com.paynest.events.WalletMessages.FundsCaptured;
import com.paynest.events.WalletMessages.FundsHeld;
import com.paynest.events.WalletMessages.FundsHoldFailed;
import com.paynest.events.WalletMessages.FundsReleased;
import com.paynest.events.WalletMessages.HoldFunds;
import com.paynest.events.WalletMessages.ReleaseFunds;
import com.paynest.events.WalletMessages.WalletCreated;
import com.paynest.outbox.OutboxEvent;
import com.paynest.outbox.OutboxRepository;
import com.paynest.outbox.OutboxWriter;
import com.paynest.wallet.bank.BankClient;
import com.paynest.wallet.bank.BankResult;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.messaging.WalletCommandService;
import com.paynest.wallet.service.WalletService;

/** Saga commands, idempotent consumer and outbox: T-EVT-2, T-OUT-1, T-OUT-2, T-SAG-2 (wallet side). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class WalletMessagingIntegrationTest {

    @Autowired
    WalletService walletService;

    @Autowired
    WalletCommandService commands;

    @Autowired
    EventCodec codec;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    OutboxWriter outboxWriter;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    KafkaContainer kafka;

    @MockitoBean
    BankClient bank;

    Wallet alice;
    Wallet bob;

    @BeforeEach
    void setUp() {
        when(bank.charge(any(), anyLong())).thenReturn(BankResult.ok());
        alice = walletService.topUp("alice-" + UUID.randomUUID(), UUID.randomUUID().toString(), 10_000);
        bob = walletService.getOrCreate("bob-" + UUID.randomUUID());
    }

    @Test
    void holdThenCaptureMovesMoneyAndConservesTotal() {
        UUID transferId = UUID.randomUUID();

        handle(new HoldFunds(transferId, alice.getId(), 3_000));
        assertThat(reload(alice).getHeld()).isEqualTo(3_000);
        assertThat(lastEventFor(alice)).isEqualTo(new FundsHeld(transferId, alice.getId(), 3_000));

        handle(new CaptureFunds(transferId, alice.getId(), bob.getId(), 3_000));
        assertThat(reload(alice).getBalance()).isEqualTo(7_000);
        assertThat(reload(alice).getHeld()).isZero();
        assertThat(reload(bob).getBalance()).isEqualTo(3_000);
        assertThat(lastEventFor(alice)).isEqualTo(new FundsCaptured(transferId, alice.getId(), bob.getId(), 3_000));
    }

    @Test
    void holdWithInsufficientFundsReplies() {
        UUID transferId = UUID.randomUUID();

        handle(new HoldFunds(transferId, alice.getId(), 10_001));

        assertThat(reload(alice).getHeld()).isZero();
        assertThat(lastEventFor(alice)).isEqualTo(new FundsHoldFailed(transferId, alice.getId(), "INSUFFICIENT_FUNDS"));
    }

    @Test
    void releaseGivesHeldMoneyBack() {
        UUID transferId = UUID.randomUUID();
        handle(new HoldFunds(transferId, alice.getId(), 4_000));

        handle(new ReleaseFunds(transferId, alice.getId(), 4_000));

        assertThat(reload(alice).getHeld()).isZero();
        assertThat(reload(alice).getBalance()).isEqualTo(10_000);
        assertThat(lastEventFor(alice)).isEqualTo(new FundsReleased(transferId, alice.getId(), 4_000));
    }

    @Test
    void duplicateCommandIsHandledOnce() { // T-EVT-2, INV-6
        EventEnvelope hold = codec.decode(codec.encode(UUID.randomUUID(), new HoldFunds(UUID.randomUUID(), alice.getId(), 1_000)));

        commands.handle(hold);
        commands.handle(hold);

        assertThat(reload(alice).getHeld()).isEqualTo(1_000);
    }

    @Test
    void outboxRowIsRolledBackWithTheBusinessChange() { // T-OUT-1
        long before = outbox.count();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE wallets SET balance = balance + 1 WHERE id = ?", alice.getId());
            outboxWriter.append("wallet", alice.getId(), Topics.WALLET_EVENTS, new WalletCreated(alice.getId(), "x"));
            throw new IllegalStateException("boom");
        })).hasMessage("boom");

        assertThat(outbox.count()).isEqualTo(before);
        assertThat(reload(alice).getBalance()).isEqualTo(10_000);
    }

    @Test
    void relayPublishesOutboxRowsToKafka() { // T-OUT-2, T-EVT-1
        Wallet carol = walletService.getOrCreate("carol-" + UUID.randomUUID());

        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.WALLET_EVENTS));
            ConsumerRecord<String, String> record = pollUntil(consumer, carol.getId().toString());

            EventEnvelope envelope = codec.decode(record.value());
            assertThat(envelope.eventId()).isNotNull();
            assertThat(envelope.eventType()).isEqualTo("WalletCreated");
            assertThat(envelope.occurredAt()).isNotNull();
            assertThat(envelope.version()).isEqualTo(1);
            assertThat(codec.payloadOf(envelope)).isEqualTo(new WalletCreated(carol.getId(), carol.getUserId()));
            assertThat(outbox.findById(envelope.eventId())).get().extracting(OutboxEvent::getPublishedAt).isNotNull();
        }
    }

    private ConsumerRecord<String, String> pollUntil(KafkaConsumer<String, String> consumer, String key) {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            var match = StreamSupport.stream(consumer.poll(Duration.ofMillis(500)).spliterator(), false)
                    .filter(record -> key.equals(record.key()))
                    .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        }
        throw new AssertionError("No record with key " + key + " on " + Topics.WALLET_EVENTS);
    }

    private void handle(Message command) {
        commands.handle(codec.decode(codec.encode(UUID.randomUUID(), command)));
    }

    private Wallet reload(Wallet wallet) {
        return walletService.getOrCreate(wallet.getUserId());
    }

    private Message lastEventFor(Wallet wallet) {
        List<OutboxEvent> events = outbox.findByTopicOrderByCreatedAt(Topics.WALLET_EVENTS).stream()
                .filter(event -> event.getAggregateId().equals(wallet.getId().toString()))
                .toList();
        return codec.payloadOf(codec.decode(events.getLast().getPayload()));
    }
}
