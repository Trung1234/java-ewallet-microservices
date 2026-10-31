package com.paynest.transfer.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paynest.events.Topics;
import com.paynest.events.WalletMessages.HoldFunds;
import com.paynest.outbox.OutboxWriter;
import com.paynest.transfer.domain.Transfer;
import com.paynest.transfer.domain.TransferRepository;
import com.paynest.transfer.domain.WalletDirectoryEntry;
import com.paynest.transfer.domain.WalletDirectoryRepository;
import com.paynest.web.ApiException;

/**
 * Accepts transfer requests. The request is stored as PENDING together with the first saga
 * command (HoldFunds) in one transaction; the rest happens asynchronously in {@code TransferSaga}.
 */
@Service
public class TransferService {

    private final TransferRepository transfers;
    private final WalletDirectoryRepository directory;
    private final OutboxWriter outbox;
    private final TransactionTemplate transaction;

    public TransferService(TransferRepository transfers, WalletDirectoryRepository directory,
                           OutboxWriter outbox, TransactionTemplate transaction) {
        this.transfers = transfers;
        this.directory = directory;
        this.outbox = outbox;
        this.transaction = transaction;
    }

    public Transfer create(String userId, String idempotencyKey, UUID toWalletId, long amount) {
        var existing = transfers.findByFromUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return sameRequestOrFail(existing.get(), toWalletId, amount);
        }

        WalletDirectoryEntry from = directory.findByUserId(userId).orElseThrow(() ->
                ApiException.unprocessable("WALLET_NOT_FOUND", "You have no wallet yet, open it with GET /wallets/me"));
        if (from.getWalletId().equals(toWalletId)) {
            throw ApiException.unprocessable("SELF_TRANSFER", "Cannot transfer to your own wallet");
        }
        WalletDirectoryEntry to = directory.findById(toWalletId).orElseThrow(() ->
                ApiException.unprocessable("RECIPIENT_NOT_FOUND", "Recipient wallet " + toWalletId + " does not exist"));

        try {
            return transaction.execute(status -> {
                Transfer transfer = transfers.saveAndFlush(
                        new Transfer(idempotencyKey, userId, from.getWalletId(), to.getUserId(), to.getWalletId(), amount));
                outbox.append(TransferSaga.AGGREGATE, transfer.getFromWalletId(), Topics.WALLET_COMMANDS,
                        new HoldFunds(transfer.getId(), transfer.getFromWalletId(), amount));
                return transfer;
            });
        } catch (DataIntegrityViolationException sameKeyConcurrently) {
            // Another request with the same key won the race (T-TRF-4): return its transfer.
            Transfer winner = transfers.findByFromUserIdAndIdempotencyKey(userId, idempotencyKey).orElseThrow();
            return sameRequestOrFail(winner, toWalletId, amount);
        }
    }

    public Transfer get(String userId, UUID transferId) {
        Transfer transfer = transfers.findById(transferId)
                .orElseThrow(() -> ApiException.notFound("TRANSFER_NOT_FOUND", "Transfer " + transferId + " not found"));
        if (!transfer.involves(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "This transfer belongs to another user");
        }
        return transfer;
    }

    public List<Transfer> list(String userId) {
        return transfers.findTop50ByFromUserIdOrToUserIdOrderByCreatedAtDesc(userId, userId);
    }

    private static Transfer sameRequestOrFail(Transfer transfer, UUID toWalletId, long amount) {
        if (!transfer.isSameRequest(toWalletId, amount)) {
            throw ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different transfer");
        }
        return transfer;
    }
}
