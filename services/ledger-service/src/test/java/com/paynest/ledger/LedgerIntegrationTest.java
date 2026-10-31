package com.paynest.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.paynest.events.EventCodec;
import com.paynest.events.LedgerMessages.LedgerPosted;
import com.paynest.events.LedgerMessages.LedgerPostingFailed;
import com.paynest.events.LedgerMessages.PostTransfer;
import com.paynest.events.Message;
import com.paynest.events.Topics;
import com.paynest.events.WalletMessages.WalletToppedUp;
import com.paynest.ledger.api.LedgerController;
import com.paynest.ledger.domain.Accounts;
import com.paynest.ledger.domain.Journal;
import com.paynest.ledger.domain.JournalRepository;
import com.paynest.ledger.messaging.LedgerMessageService;
import com.paynest.outbox.OutboxRepository;

/** T-LED-1 .. T-LED-3 and the ledger side of compensation. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class LedgerIntegrationTest {

    @Autowired
    LedgerMessageService messages;

    @Autowired
    LedgerController controller;

    @Autowired
    JournalRepository journals;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    EventCodec codec;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();

    @Test
    void transferCreatesBalancedDebitAndCredit() { // T-LED-1, INV-4
        UUID transferId = UUID.randomUUID();

        handle(new PostTransfer(transferId, alice, bob, 2_000));

        var journal = controller.journal(transferId.toString());
        assertThat(journal.entries()).hasSize(2);
        assertThat(controller.balance(Accounts.wallet(alice)).balance()).isEqualTo(-2_000);
        assertThat(controller.balance(Accounts.wallet(bob)).balance()).isEqualTo(2_000);
        assertThat(controller.trialBalance().balanced()).isTrue();
        assertThat(lastReply(transferId)).isEqualTo(new LedgerPosted(transferId, journal.id()));
    }

    @Test
    void entriesCannotBeChangedOrDeleted() { // T-LED-2
        UUID transferId = UUID.randomUUID();
        handle(new PostTransfer(transferId, alice, bob, 500));
        Journal journal = journals.findByReference(transferId.toString()).orElseThrow();

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = 1 WHERE journal_id = ?", journal.getId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE journal_id = ?", journal.getId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM journals WHERE id = ?", journal.getId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void unbalancedJournalIsRejectedByTheDatabase() { // INV-4, last line of defence
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            UUID journalId = UUID.randomUUID();
            jdbc.update("INSERT INTO journals (id, reference, type, created_at) VALUES (?, ?, 'TRANSFER', now())",
                    journalId, "manual-" + journalId);
            jdbc.update("INSERT INTO ledger_entries (id, journal_id, account, direction, amount, created_at) "
                    + "VALUES (?, ?, 'wallet:x', 'DEBIT', 100, now())", UUID.randomUUID(), journalId);
        })).rootCause().hasMessageContaining("not balanced");
    }

    @Test
    void samePostingTwiceIsPostedOnce() { // T-LED-3
        UUID transferId = UUID.randomUUID();

        handle(new PostTransfer(transferId, alice, bob, 700));
        handle(new PostTransfer(transferId, alice, bob, 700)); // same command, new event id

        assertThat(controller.balance(Accounts.wallet(bob)).balance()).isEqualTo(700);
        assertThat(replies(transferId)).hasSize(2).allMatch(LedgerPosted.class::isInstance);
    }

    @Test
    void transferAboveLimitIsRejected() {
        UUID transferId = UUID.randomUUID();

        handle(new PostTransfer(transferId, alice, bob, 1_000_001));

        assertThat(journals.findByReference(transferId.toString())).isEmpty();
        assertThat(lastReply(transferId)).isEqualTo(new LedgerPostingFailed(transferId, "LIMIT_EXCEEDED"));
    }

    @Test
    void topUpIsBookedAgainstTheBank() {
        UUID operationId = UUID.randomUUID();

        handle(new WalletToppedUp(operationId, alice, 9_000));

        assertThat(controller.balance(Accounts.wallet(alice)).balance()).isEqualTo(9_000);
        assertThat(controller.journal(operationId.toString()).entries())
                .extracting(LedgerController.EntryView::account)
                .containsExactlyInAnyOrder(Accounts.BANK, Accounts.wallet(alice));
    }

    private void handle(Message message) {
        messages.handle(codec.decode(codec.encode(UUID.randomUUID(), message)));
    }

    private List<Message> replies(UUID transferId) {
        return outbox.findByTopicOrderByCreatedAt(Topics.LEDGER_EVENTS).stream()
                .filter(event -> event.getAggregateId().equals(transferId.toString()))
                .map(event -> codec.payloadOf(codec.decode(event.getPayload())))
                .toList();
    }

    private Message lastReply(UUID transferId) {
        return replies(transferId).getLast();
    }
}
