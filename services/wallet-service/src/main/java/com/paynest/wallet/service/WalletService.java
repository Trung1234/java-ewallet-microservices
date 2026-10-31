package com.paynest.wallet.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paynest.events.Topics;
import com.paynest.events.WalletMessages.WalletCreated;
import com.paynest.events.WalletMessages.WalletToppedUp;
import com.paynest.events.WalletMessages.WalletWithdrawn;
import com.paynest.outbox.OutboxWriter;
import com.paynest.wallet.bank.BankClient;
import com.paynest.wallet.bank.BankResult;
import com.paynest.wallet.domain.BankOperation;
import com.paynest.wallet.domain.BankOperationRepository;
import com.paynest.wallet.domain.InsufficientFundsException;
import com.paynest.wallet.domain.TransactionType;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.domain.WalletRepository;
import com.paynest.wallet.domain.WalletTransaction;
import com.paynest.wallet.domain.WalletTransactionRepository;
import com.paynest.web.ApiException;

/**
 * User-facing wallet operations.
 * <p>
 * Bank calls are slow and can fail, so they never run inside a database transaction. A top-up or
 * withdrawal is split into: (1) record the operation (and hold the money for a withdrawal),
 * (2) call the bank, (3) apply the result. Each database step is its own short transaction and is
 * retried on optimistic-lock conflicts.
 */
@Service
public class WalletService {

    static final String AGGREGATE = "wallet";
    private static final int MAX_CONFLICT_RETRIES = 10;

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final BankOperationRepository operations;
    private final OutboxWriter outbox;
    private final BankClient bank;
    private final TransactionTemplate transaction;
    private final String currency;

    public WalletService(WalletRepository wallets, WalletTransactionRepository transactions,
                         BankOperationRepository operations, OutboxWriter outbox, BankClient bank,
                         TransactionTemplate transaction, @Value("${paynest.wallet.currency:VND}") String currency) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.operations = operations;
        this.outbox = outbox;
        this.bank = bank;
        this.transaction = transaction;
        this.currency = currency;
    }

    /** Wallets are created lazily on first access and announced with a {@code WalletCreated} event. */
    public Wallet getOrCreate(String userId) {
        return wallets.findByUserId(userId).orElseGet(() -> {
            try {
                return transaction.execute(status -> {
                    Wallet wallet = wallets.saveAndFlush(new Wallet(userId, currency));
                    outbox.append(AGGREGATE, wallet.getId(), Topics.WALLET_EVENTS, new WalletCreated(wallet.getId(), userId));
                    return wallet;
                });
            } catch (DataIntegrityViolationException createdConcurrently) {
                return wallets.findByUserId(userId).orElseThrow();
            }
        });
    }

    public List<WalletTransaction> history(String userId) {
        return transactions.findTop50ByWalletIdOrderByCreatedAtDesc(getOrCreate(userId).getId());
    }

    public Wallet topUp(String userId, String idempotencyKey, long amount) {
        Wallet wallet = getOrCreate(userId);
        BankOperation operation = retryOnConflict(() -> start(wallet.getId(), BankOperation.Type.TOPUP, idempotencyKey, amount));
        if (operation.isPending()) {
            BankResult result = bank.charge(operation.getId(), amount);
            retryOnConflict(() -> finish(operation.getId(), result));
        }
        return outcome(operation.getId());
    }

    public Wallet withdraw(String userId, String idempotencyKey, long amount) {
        Wallet wallet = getOrCreate(userId);
        BankOperation operation = retryOnConflict(() -> start(wallet.getId(), BankOperation.Type.WITHDRAW, idempotencyKey, amount));
        if (operation.isPending()) {
            BankResult result = bank.payout(operation.getId(), amount);
            retryOnConflict(() -> finish(operation.getId(), result));
        }
        return outcome(operation.getId());
    }

    /** Step 1: record the operation. A withdrawal also holds the money so it cannot be spent twice. */
    private BankOperation start(UUID walletId, BankOperation.Type type, String key, long amount) {
        var existing = operations.findByWalletIdAndIdempotencyKey(walletId, key);
        if (existing.isPresent()) {
            return sameRequestOrFail(existing.get(), type, amount);
        }
        try {
            return transaction.execute(status -> {
                BankOperation operation = operations.saveAndFlush(new BankOperation(walletId, type, amount, key));
                if (type == BankOperation.Type.WITHDRAW) {
                    Wallet wallet = wallets.findById(walletId).orElseThrow();
                    try {
                        wallet.hold(amount);
                    } catch (InsufficientFundsException e) {
                        throw ApiException.unprocessable("INSUFFICIENT_FUNDS", "Available balance is " + wallet.available());
                    }
                    transactions.save(WalletTransaction.of(wallet, TransactionType.HOLD, amount, operation.getId()));
                }
                return operation;
            });
        } catch (DataIntegrityViolationException sameKeyConcurrently) {
            BankOperation operation = operations.findByWalletIdAndIdempotencyKey(walletId, key).orElseThrow();
            return sameRequestOrFail(operation, type, amount);
        }
    }

    /** Step 3: apply the bank result exactly once. */
    private Void finish(UUID operationId, BankResult result) {
        return transaction.execute(status -> {
            BankOperation operation = operations.findById(operationId).orElseThrow();
            if (!operation.isPending()) {
                return null; // a concurrent request with the same key already finished it
            }
            Wallet wallet = wallets.findById(operation.getWalletId()).orElseThrow();
            long amount = operation.getAmount();
            if (result.isOk()) {
                if (operation.getType() == BankOperation.Type.TOPUP) {
                    wallet.credit(amount);
                    transactions.save(WalletTransaction.of(wallet, TransactionType.TOPUP, amount, operationId));
                    outbox.append(AGGREGATE, wallet.getId(), Topics.WALLET_EVENTS, new WalletToppedUp(operationId, wallet.getId(), amount));
                } else {
                    wallet.capture(amount);
                    transactions.save(WalletTransaction.of(wallet, TransactionType.WITHDRAW, amount, operationId));
                    outbox.append(AGGREGATE, wallet.getId(), Topics.WALLET_EVENTS, new WalletWithdrawn(operationId, wallet.getId(), amount));
                }
                operation.complete();
            } else {
                if (operation.getType() == BankOperation.Type.WITHDRAW) {
                    wallet.release(amount);
                    transactions.save(WalletTransaction.of(wallet, TransactionType.RELEASE, amount, operationId));
                }
                operation.fail(result.outcome() == BankResult.Outcome.DECLINED ? "BANK_DECLINED" : "BANK_UNAVAILABLE");
            }
            return null;
        });
    }

    private Wallet outcome(UUID operationId) {
        BankOperation operation = operations.findById(operationId).orElseThrow();
        return switch (operation.getStatus()) {
            case COMPLETED -> wallets.findById(operation.getWalletId()).orElseThrow();
            case PENDING -> throw ApiException.conflict("OPERATION_IN_PROGRESS",
                    "A request with this Idempotency-Key is still being processed");
            case FAILED -> {
                if ("BANK_DECLINED".equals(operation.getFailureReason())) {
                    throw ApiException.unprocessable("BANK_DECLINED", "The bank declined the operation");
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY, "BANK_UNAVAILABLE",
                        "The bank did not respond, no money was moved. Please try again later");
            }
        };
    }

    private static BankOperation sameRequestOrFail(BankOperation operation, BankOperation.Type type, long amount) {
        if (!operation.matches(type, amount)) {
            throw ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request");
        }
        return operation;
    }

    private static <T> T retryOnConflict(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (OptimisticLockingFailureException e) {
                if (attempt >= MAX_CONFLICT_RETRIES) {
                    throw ApiException.conflict("CONCURRENT_UPDATE", "The wallet is busy, please retry");
                }
                sleepJitter(attempt);
            }
        }
    }

    private static void sleepJitter(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 20) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
