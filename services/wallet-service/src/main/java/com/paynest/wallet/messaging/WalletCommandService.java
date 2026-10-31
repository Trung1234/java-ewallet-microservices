package com.paynest.wallet.messaging;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.paynest.outbox.Inbox;
import com.paynest.outbox.OutboxWriter;
import com.paynest.wallet.domain.TransactionType;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.domain.WalletRepository;
import com.paynest.wallet.domain.WalletTransaction;
import com.paynest.wallet.domain.WalletTransactionRepository;

/**
 * Executes the saga commands sent by transfer-service. Each command is handled in one transaction
 * together with its reply event (outbox) and its processed-event mark (inbox).
 */
@Service
public class WalletCommandService {

    private static final Logger log = LoggerFactory.getLogger(WalletCommandService.class);
    private static final String AGGREGATE = "wallet";

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final OutboxWriter outbox;
    private final Inbox inbox;
    private final EventCodec codec;

    public WalletCommandService(WalletRepository wallets, WalletTransactionRepository transactions,
                                OutboxWriter outbox, Inbox inbox, EventCodec codec) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.outbox = outbox;
        this.inbox = inbox;
        this.codec = codec;
    }

    @Transactional
    public void handle(EventEnvelope envelope) {
        Message message = codec.payloadOf(envelope);
        if (message == null || !inbox.markProcessed(envelope.eventId())) {
            return;
        }
        switch (message) {
            case HoldFunds command -> hold(command);
            case CaptureFunds command -> capture(command);
            case ReleaseFunds command -> release(command);
            default -> log.debug("Ignoring {}", envelope.eventType());
        }
    }

    private void hold(HoldFunds command) {
        Optional<Wallet> found = wallets.findById(command.walletId());
        if (found.isEmpty()) {
            reply(command.walletId(), new FundsHoldFailed(command.transferId(), command.walletId(), "WALLET_NOT_FOUND"));
            return;
        }
        Wallet wallet = found.get();
        if (wallet.available() < command.amount()) {
            reply(wallet.getId(), new FundsHoldFailed(command.transferId(), wallet.getId(), "INSUFFICIENT_FUNDS"));
            return;
        }
        wallet.hold(command.amount());
        transactions.save(WalletTransaction.of(wallet, TransactionType.HOLD, command.amount(), command.transferId()));
        reply(wallet.getId(), new FundsHeld(command.transferId(), wallet.getId(), command.amount()));
    }

    /** Debit sender and credit recipient in one local transaction (both wallets live in this DB). */
    private void capture(CaptureFunds command) {
        Wallet from = wallets.findById(command.fromWalletId()).orElseThrow();
        Wallet to = wallets.findById(command.toWalletId()).orElseThrow();
        from.capture(command.amount());
        to.credit(command.amount());
        transactions.save(WalletTransaction.of(from, TransactionType.TRANSFER_OUT, command.amount(), command.transferId()));
        transactions.save(WalletTransaction.of(to, TransactionType.TRANSFER_IN, command.amount(), command.transferId()));
        reply(from.getId(), new FundsCaptured(command.transferId(), from.getId(), to.getId(), command.amount()));
    }

    private void release(ReleaseFunds command) {
        Wallet wallet = wallets.findById(command.walletId()).orElseThrow();
        boolean wasHeld = transactions.existsByWalletIdAndTypeAndReference(
                wallet.getId(), TransactionType.HOLD, command.transferId().toString());
        if (wasHeld) {
            wallet.release(command.amount());
            transactions.save(WalletTransaction.of(wallet, TransactionType.RELEASE, command.amount(), command.transferId()));
        }
        reply(wallet.getId(), new FundsReleased(command.transferId(), wallet.getId(), command.amount()));
    }

    private void reply(UUID walletId, Message event) {
        outbox.append(AGGREGATE, walletId, Topics.WALLET_EVENTS, event);
    }
}
