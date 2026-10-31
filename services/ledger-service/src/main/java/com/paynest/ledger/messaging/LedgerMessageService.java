package com.paynest.ledger.messaging;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paynest.events.EventCodec;
import com.paynest.events.EventEnvelope;
import com.paynest.events.LedgerMessages.LedgerPosted;
import com.paynest.events.LedgerMessages.LedgerPostingFailed;
import com.paynest.events.LedgerMessages.PostTransfer;
import com.paynest.events.Message;
import com.paynest.events.Topics;
import com.paynest.events.WalletMessages.WalletToppedUp;
import com.paynest.events.WalletMessages.WalletWithdrawn;
import com.paynest.ledger.domain.Accounts;
import com.paynest.ledger.domain.Journal;
import com.paynest.ledger.domain.JournalRepository;
import com.paynest.ledger.domain.JournalType;
import com.paynest.ledger.service.LedgerService;
import com.paynest.outbox.Inbox;
import com.paynest.outbox.OutboxWriter;

import static com.paynest.ledger.domain.Posting.credit;
import static com.paynest.ledger.domain.Posting.debit;

/**
 * Posts journals for saga commands (PostTransfer) and for wallet events (top-up, withdraw).
 * <p>
 * Two ways to make the saga take the compensation path, for tests and failure drills:
 * a transfer above {@code paynest.ledger.max-transfer-amount} is rejected, and
 * {@code paynest.ledger.failure-rate} rejects a random share of transfers.
 */
@Service
public class LedgerMessageService {

    private static final Logger log = LoggerFactory.getLogger(LedgerMessageService.class);
    private static final String AGGREGATE = "ledger";

    private final LedgerService ledger;
    private final JournalRepository journals;
    private final OutboxWriter outbox;
    private final Inbox inbox;
    private final EventCodec codec;
    private final long maxTransferAmount;
    private final double failureRate;

    public LedgerMessageService(LedgerService ledger, JournalRepository journals, OutboxWriter outbox, Inbox inbox,
                                EventCodec codec,
                                @Value("${paynest.ledger.max-transfer-amount:50000000}") long maxTransferAmount,
                                @Value("${paynest.ledger.failure-rate:0.0}") double failureRate) {
        this.ledger = ledger;
        this.journals = journals;
        this.outbox = outbox;
        this.inbox = inbox;
        this.codec = codec;
        this.maxTransferAmount = maxTransferAmount;
        this.failureRate = failureRate;
    }

    @Transactional
    public void handle(EventEnvelope envelope) {
        Message message = codec.payloadOf(envelope);
        if (message == null || !inbox.markProcessed(envelope.eventId())) {
            return;
        }
        switch (message) {
            case PostTransfer command -> postTransfer(command);
            case WalletToppedUp event -> ledger.post(event.operationId().toString(), JournalType.TOPUP, List.of(
                    debit(Accounts.BANK, event.amount()),
                    credit(Accounts.wallet(event.walletId()), event.amount())));
            case WalletWithdrawn event -> ledger.post(event.operationId().toString(), JournalType.WITHDRAW, List.of(
                    debit(Accounts.wallet(event.walletId()), event.amount()),
                    credit(Accounts.BANK, event.amount())));
            default -> log.debug("Ignoring {}", envelope.eventType());
        }
    }

    private void postTransfer(PostTransfer command) {
        String reference = command.transferId().toString();
        if (journals.findByReference(reference).isEmpty()) {
            if (command.amount() > maxTransferAmount) {
                reply(command, new LedgerPostingFailed(command.transferId(), "LIMIT_EXCEEDED"));
                return;
            }
            if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
                reply(command, new LedgerPostingFailed(command.transferId(), "LEDGER_REJECTED"));
                return;
            }
        }
        // Debit sender, credit recipient (README §2: "Nợ A, Có B").
        Journal journal = ledger.post(reference, JournalType.TRANSFER, List.of(
                debit(Accounts.wallet(command.fromWalletId()), command.amount()),
                credit(Accounts.wallet(command.toWalletId()), command.amount())));
        reply(command, new LedgerPosted(command.transferId(), journal.getId()));
    }

    private void reply(PostTransfer command, Message event) {
        outbox.append(AGGREGATE, command.transferId(), Topics.LEDGER_EVENTS, event);
        try (var ignored = MDC.putCloseable("transferId", command.transferId().toString())) {
            log.info("Replied {}", EventCodec.typeOf(event));
        }
    }
}
