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

/** A business event recorded in the ledger; groups balanced {@link LedgerEntry} rows. */
@Entity
@Immutable
@Table(name = "journals")
public class Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Journal() {
    }

    public Journal(String reference, JournalType type) {
        this.reference = reference;
        this.type = type;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public JournalType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
