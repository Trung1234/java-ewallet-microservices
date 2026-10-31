package com.paynest.ledger.domain;

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

import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "journal_id", nullable = false)
    private UUID journalId;

    @Column(nullable = false)
    private String account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(nullable = false)
    private long amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID journalId, Posting posting) {
        this.journalId = journalId;
        this.account = posting.account();
        this.direction = posting.direction();
        this.amount = posting.amount();
        this.createdAt = Instant.now();
    }

    public String getAccount() {
        return account;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmount() {
        return amount;
    }
}
