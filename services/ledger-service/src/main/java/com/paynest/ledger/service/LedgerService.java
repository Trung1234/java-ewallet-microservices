package com.paynest.ledger.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.paynest.ledger.domain.Direction;
import com.paynest.ledger.domain.Journal;
import com.paynest.ledger.domain.JournalRepository;
import com.paynest.ledger.domain.JournalType;
import com.paynest.ledger.domain.LedgerEntry;
import com.paynest.ledger.domain.LedgerEntryRepository;
import com.paynest.ledger.domain.Posting;

@Service
public class LedgerService {

    private final JournalRepository journals;
    private final LedgerEntryRepository entries;

    public LedgerService(JournalRepository journals, LedgerEntryRepository entries) {
        this.journals = journals;
        this.entries = entries;
    }

    /**
     * Writes a journal once per reference (T-LED-3). Debits must equal credits; the database
     * checks it again at commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Journal post(String reference, JournalType type, List<Posting> postings) {
        var existing = journals.findByReference(reference);
        if (existing.isPresent()) {
            return existing.get();
        }
        long debits = postings.stream().filter(p -> p.direction() == Direction.DEBIT).mapToLong(Posting::amount).sum();
        long credits = postings.stream().filter(p -> p.direction() == Direction.CREDIT).mapToLong(Posting::amount).sum();
        if (debits != credits) {
            throw new IllegalArgumentException("Unbalanced journal " + reference + ": debits " + debits + " != credits " + credits);
        }
        Journal journal = journals.save(new Journal(reference, type));
        postings.forEach(posting -> entries.save(new LedgerEntry(journal.getId(), posting)));
        return journal;
    }
}
