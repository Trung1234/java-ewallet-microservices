package com.paynest.wallet.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * History line of a wallet. The unique key (wallet, type, reference) makes every step idempotent.
 */
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType type;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletTransaction() {
    }

    public static WalletTransaction of(Wallet wallet, TransactionType type, long amount, Object reference) {
        var tx = new WalletTransaction();
        tx.walletId = wallet.getId();
        tx.type = type;
        tx.amount = amount;
        tx.reference = reference.toString();
        tx.balanceAfter = wallet.getBalance();
        tx.createdAt = Instant.now();
        return tx;
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public String getReference() {
        return reference;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
