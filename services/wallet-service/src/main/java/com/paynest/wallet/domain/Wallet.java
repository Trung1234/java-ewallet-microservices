package com.paynest.wallet.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A user's wallet. {@code balance} is the money owned, {@code held} is the part reserved by
 * pending transfers or withdrawals, and {@code available = balance - held} is what can be spent.
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long balance;

    @Column(nullable = false)
    private long held;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    public Wallet(String userId, String currency) {
        this.userId = userId;
        this.currency = currency;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public long available() {
        return balance - held;
    }

    public void credit(long amount) {
        requirePositive(amount);
        balance = Math.addExact(balance, amount);
        touch();
    }

    public void hold(long amount) {
        requirePositive(amount);
        if (available() < amount) {
            throw new InsufficientFundsException(id, amount, available());
        }
        held += amount;
        touch();
    }

    public void release(long amount) {
        requirePositive(amount);
        if (held < amount) {
            throw new IllegalStateException("Cannot release " + amount + ", only " + held + " is held on wallet " + id);
        }
        held -= amount;
        touch();
    }

    /** Takes a previously held amount out of the wallet. */
    public void capture(long amount) {
        requirePositive(amount);
        if (held < amount) {
            throw new IllegalStateException("Cannot capture " + amount + ", only " + held + " is held on wallet " + id);
        }
        held -= amount;
        balance -= amount;
        touch();
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalance() {
        return balance;
    }

    public long getHeld() {
        return held;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
