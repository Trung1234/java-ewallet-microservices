package com.paynest.ledger.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByJournalId(UUID journalId);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.account = :account and e.direction = :direction")
    long sumByAccount(@Param("account") String account, @Param("direction") Direction direction);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.direction = :direction")
    long sumAll(@Param("direction") Direction direction);
}
