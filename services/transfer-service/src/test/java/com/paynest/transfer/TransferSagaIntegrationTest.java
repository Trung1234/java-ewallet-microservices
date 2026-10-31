package com.paynest.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paynest.events.EventCodec;
import com.paynest.events.LedgerMessages.LedgerPosted;
import com.paynest.events.LedgerMessages.LedgerPostingFailed;
import com.paynest.events.LedgerMessages.PostTransfer;
import com.paynest.events.Message;
import com.paynest.events.Topics;
import com.paynest.events.TransferMessages.TransferCompleted;
import com.paynest.events.TransferMessages.TransferFailed;
import com.paynest.events.WalletMessages.CaptureFunds;
import com.paynest.events.WalletMessages.FundsCaptured;
import com.paynest.events.WalletMessages.FundsHeld;
import com.paynest.events.WalletMessages.FundsHoldFailed;
import com.paynest.events.WalletMessages.FundsReleased;
import com.paynest.events.WalletMessages.ReleaseFunds;
import com.paynest.events.WalletMessages.WalletCreated;
import com.paynest.outbox.OutboxRepository;
import com.paynest.transfer.domain.Transfer;
import com.paynest.transfer.domain.TransferRepository;
import com.paynest.transfer.domain.TransferStatus;
import com.paynest.transfer.domain.WalletDirectoryRepository;
import com.paynest.transfer.service.TransferSaga;
import com.paynest.transfer.service.TransferService;

/** Orchestrator paths: T-TRF-1, T-TRF-2, T-TRF-8 and replayed replies (T-EVT-2). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TransferSagaIntegrationTest {

    @Autowired
    TransferSaga saga;

    @Autowired
    TransferService transfers;

    @Autowired
    TransferRepository repository;

    @Autowired
    WalletDirectoryRepository directory;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    EventCodec codec;

    UUID aliceWallet = UUID.randomUUID();
    UUID bobWallet = UUID.randomUUID();
    String alice = "alice-" + UUID.randomUUID();
    String bob = "bob-" + UUID.randomUUID();
    Transfer transfer;

    @BeforeEach
    void setUp() {
        reply(new WalletCreated(aliceWallet, alice));
        reply(new WalletCreated(bobWallet, bob));
        transfer = transfers.create(alice, UUID.randomUUID().toString(), bobWallet, 5_000);
    }

    @Test
    void walletCreatedFillsTheDirectory() {
        assertThat(directory.findByUserId(alice)).get().extracting(e -> e.getWalletId()).isEqualTo(aliceWallet);
    }

    @Test
    void happyPathCompletes() { // T-TRF-1 (orchestrator)
        UUID id = transfer.getId();

        reply(new FundsHeld(id, aliceWallet, 5_000));
        assertThat(status()).isEqualTo(TransferStatus.HELD);
        assertThat(sent(Topics.LEDGER_COMMANDS, id)).containsExactly(new PostTransfer(id, aliceWallet, bobWallet, 5_000));

        reply(new LedgerPosted(id, UUID.randomUUID()));
        assertThat(sent(Topics.WALLET_COMMANDS, aliceWallet)).contains(new CaptureFunds(id, aliceWallet, bobWallet, 5_000));

        reply(new FundsCaptured(id, aliceWallet, bobWallet, 5_000));
        assertThat(status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(sent(Topics.TRANSFER_EVENTS, id))
                .containsExactly(new TransferCompleted(id, aliceWallet, alice, bobWallet, bob, 5_000));
    }

    @Test
    void holdRefusedFails() { // T-TRF-2
        UUID id = transfer.getId();

        reply(new FundsHoldFailed(id, aliceWallet, "INSUFFICIENT_FUNDS"));

        assertThat(status()).isEqualTo(TransferStatus.FAILED);
        assertThat(repository.findById(id).orElseThrow().getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(sent(Topics.TRANSFER_EVENTS, id))
                .containsExactly(new TransferFailed(id, aliceWallet, alice, bobWallet, bob, 5_000, "INSUFFICIENT_FUNDS"));
    }

    @Test
    void ledgerFailureIsCompensated() { // T-TRF-8
        UUID id = transfer.getId();
        reply(new FundsHeld(id, aliceWallet, 5_000));

        reply(new LedgerPostingFailed(id, "LIMIT_EXCEEDED"));
        assertThat(status()).isEqualTo(TransferStatus.COMPENSATING);
        assertThat(sent(Topics.WALLET_COMMANDS, aliceWallet)).contains(new ReleaseFunds(id, aliceWallet, 5_000));

        reply(new FundsReleased(id, aliceWallet, 5_000));
        assertThat(status()).isEqualTo(TransferStatus.FAILED);
        assertThat(sent(Topics.TRANSFER_EVENTS, id))
                .containsExactly(new TransferFailed(id, aliceWallet, alice, bobWallet, bob, 5_000, "LIMIT_EXCEEDED"));
    }

    @Test
    void lateOrReplayedRepliesAreIgnored() { // T-EVT-2
        UUID id = transfer.getId();
        var held = codec.decode(codec.encode(UUID.randomUUID(), new FundsHeld(id, aliceWallet, 5_000)));

        saga.handle(held);
        saga.handle(held);                                  // same event id: dropped by the inbox
        reply(new FundsHeld(id, aliceWallet, 5_000));       // new id, wrong state: ignored by the state check

        assertThat(sent(Topics.LEDGER_COMMANDS, id)).hasSize(1);
    }

    private void reply(Message message) {
        saga.handle(codec.decode(codec.encode(UUID.randomUUID(), message)));
    }

    private TransferStatus status() {
        return repository.findById(transfer.getId()).orElseThrow().getStatus();
    }

    private List<Message> sent(String topic, UUID key) {
        return outbox.findByTopicOrderByCreatedAt(topic).stream()
                .filter(event -> event.getAggregateId().equals(key.toString()))
                .map(event -> codec.payloadOf(codec.decode(event.getPayload())))
                .toList();
    }
}
