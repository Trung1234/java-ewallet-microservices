package com.paynest.transfer.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Which user owns which wallet, copied from {@code WalletCreated} events. */
@Entity
@Table(name = "wallet_directory")
public class WalletDirectoryEntry {

    @Id
    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletDirectoryEntry() {
    }

    public WalletDirectoryEntry(UUID walletId, String userId) {
        this.walletId = walletId;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    public UUID getWalletId() {
        return walletId;
    }

    public String getUserId() {
        return userId;
    }
}
