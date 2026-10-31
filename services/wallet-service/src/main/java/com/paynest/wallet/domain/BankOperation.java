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
import jakarta.persistence.Version;

/**
 * A top-up or withdrawal through the bank. Its id is the reference sent to the bank, so retrying
 * the same operation can never charge or pay out twice.
 */
@Entity
@Table(name = "bank_operations")
public class BankOperation {

    public enum Type { TOPUP, WITHDRAW }

    public enum Status { PENDING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BankOperation() {
    }

    public BankOperation(UUID walletId, Type type, long amount, String idempotencyKey) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean matches(Type type, long amount) {
        return this.type == type && this.amount == amount;
    }

    public void complete() {
        status = Status.COMPLETED;
        updatedAt = Instant.now();
    }

    public void fail(String reason) {
        status = Status.FAILED;
        failureReason = reason;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public Type getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
