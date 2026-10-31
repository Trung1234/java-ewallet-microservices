package com.paynest.transfer.service;

import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paynest.events.EventCodec;
import com.paynest.events.EventEnvelope;
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
import com.paynest.outbox.Inbox;
import com.paynest.outbox.OutboxWriter;
import com.paynest.transfer.domain.Transfer;
import com.paynest.transfer.domain.TransferRepository;
import com.paynest.transfer.domain.TransferStatus;
import com.paynest.transfer.domain.WalletDirectoryEntry;
import com.paynest.transfer.domain.WalletDirectoryRepository;

/**
 * Saga orchestrator. Each reply from wallet-service or ledger-service moves the transfer one step
 * and emits the next command through the outbox, all in one local transaction:
 * <pre>
 * FundsHeld           -> HELD,          PostTransfer  -> ledger
 * LedgerPosted        ->                CaptureFunds  -> wallet
 * FundsCaptured       -> COMPLETED,     TransferCompleted
 * FundsHoldFailed     -> FAILED,        TransferFailed
 * LedgerPostingFailed -> COMPENSATING,  ReleaseFunds  -> wallet
 * FundsReleased       -> FAILED,        TransferFailed
 * </pre>
 * The state check before each step makes late or replayed replies harmless.
 */
@Service
public class TransferSaga {

    static final String AGGREGATE = "transfer";
    private static final Logger log = LoggerFactory.getLogger(TransferSaga.class);

    private final TransferRepository transfers;
    private final WalletDirectoryRepository directory;
    private final OutboxWriter outbox;
    private final Inbox inbox;
    private final EventCodec codec;

    public TransferSaga(TransferRepository transfers, WalletDirectoryRepository directory, OutboxWriter outbox,
                        Inbox inbox, EventCodec codec) {
        this.transfers = transfers;
        this.directory = directory;
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
            case WalletCreated event -> {
                if (!directory.existsById(event.walletId())) {
                    directory.save(new WalletDirectoryEntry(event.walletId(), event.userId()));
                }
            }
            case FundsHeld event -> step(event.transferId(), TransferStatus.PENDING, transfer -> {
                transfer.transitionTo(TransferStatus.HELD);
                outbox.append(AGGREGATE, transfer.getId(), Topics.LEDGER_COMMANDS, new PostTransfer(
                        transfer.getId(), transfer.getFromWalletId(), transfer.getToWalletId(), transfer.getAmount()));
            });
            case FundsHoldFailed event -> step(event.transferId(), TransferStatus.PENDING, transfer -> {
                transfer.transitionTo(TransferStatus.FAILED, event.reason());
                publishFailed(transfer);
            });
            case LedgerPosted event -> step(event.transferId(), TransferStatus.HELD, transfer ->
                    outbox.append(AGGREGATE, transfer.getFromWalletId(), Topics.WALLET_COMMANDS, new CaptureFunds(
                            transfer.getId(), transfer.getFromWalletId(), transfer.getToWalletId(), transfer.getAmount())));
            case LedgerPostingFailed event -> step(event.transferId(), TransferStatus.HELD, transfer -> {
                transfer.transitionTo(TransferStatus.COMPENSATING, event.reason());
                outbox.append(AGGREGATE, transfer.getFromWalletId(), Topics.WALLET_COMMANDS,
                        new ReleaseFunds(transfer.getId(), transfer.getFromWalletId(), transfer.getAmount()));
            });
            case FundsReleased event -> step(event.transferId(), TransferStatus.COMPENSATING, transfer -> {
                transfer.transitionTo(TransferStatus.FAILED, transfer.getFailureReason());
                publishFailed(transfer);
            });
            case FundsCaptured event -> step(event.transferId(), TransferStatus.HELD, transfer -> {
                transfer.transitionTo(TransferStatus.COMPLETED);
                outbox.append(AGGREGATE, transfer.getId(), Topics.TRANSFER_EVENTS, new TransferCompleted(
                        transfer.getId(), transfer.getFromWalletId(), transfer.getFromUserId(),
                        transfer.getToWalletId(), transfer.getToUserId(), transfer.getAmount()));
            });
            default -> log.debug("Ignoring {}", envelope.eventType());
        }
    }

    private void step(UUID transferId, TransferStatus expected, Consumer<Transfer> action) {
        Transfer transfer = transfers.findById(transferId).orElse(null);
        if (transfer == null) {
            log.warn("Reply for unknown transfer {}", transferId);
            return;
        }
        if (transfer.getStatus() != expected) {
            log.info("Transfer {} is {}, expected {}; reply ignored", transferId, transfer.getStatus(), expected);
            return;
        }
        action.accept(transfer);
    }

    private void publishFailed(Transfer transfer) {
        outbox.append(AGGREGATE, transfer.getId(), Topics.TRANSFER_EVENTS, new TransferFailed(
                transfer.getId(), transfer.getFromWalletId(), transfer.getFromUserId(),
                transfer.getToWalletId(), transfer.getToUserId(), transfer.getAmount(), transfer.getFailureReason()));
    }
}
