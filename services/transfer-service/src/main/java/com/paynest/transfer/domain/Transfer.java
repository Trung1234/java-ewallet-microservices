package com.paynest.transfer.domain;

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

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "from_user_id", nullable = false, updatable = false)
    private String fromUserId;

    @Column(name = "from_wallet_id", nullable = false, updatable = false)
    private UUID fromWalletId;

    @Column(name = "to_user_id", nullable = false, updatable = false)
    private String toUserId;

    @Column(name = "to_wallet_id", nullable = false, updatable = false)
    private UUID toWalletId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transfer() {
    }

    public Transfer(String idempotencyKey, String fromUserId, UUID fromWalletId, String toUserId, UUID toWalletId, long amount) {
        this.idempotencyKey = idempotencyKey;
        this.fromUserId = fromUserId;
        this.fromWalletId = fromWalletId;
        this.toUserId = toUserId;
        this.toWalletId = toWalletId;
        this.amount = amount;
        this.status = TransferStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    /** @throws IllegalStateException for a transition the state machine does not allow (T-TRF-7) */
    public void transitionTo(TransferStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Transfer " + id + ": illegal transition " + status + " -> " + next);
        }
        status = next;
        updatedAt = Instant.now();
    }

    public void transitionTo(TransferStatus next, String reason) {
        transitionTo(next);
        failureReason = reason;
    }

    public boolean isSameRequest(UUID toWalletId, long amount) {
        return this.toWalletId.equals(toWalletId) && this.amount == amount;
    }

    public boolean involves(String userId) {
        return fromUserId.equals(userId) || toUserId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public UUID getFromWalletId() {
        return fromWalletId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public UUID getToWalletId() {
        return toWalletId;
    }

    public long getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
