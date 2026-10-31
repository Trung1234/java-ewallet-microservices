package com.paynest.ledger.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalRepository extends JpaRepository<Journal, UUID> {

    Optional<Journal> findByReference(String reference);
}
