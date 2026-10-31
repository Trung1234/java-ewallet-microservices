package com.paynest.ledger.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paynest.ledger.domain.Direction;
import com.paynest.ledger.domain.Journal;
import com.paynest.ledger.domain.JournalRepository;
import com.paynest.ledger.domain.JournalType;
import com.paynest.ledger.domain.LedgerEntryRepository;
import com.paynest.web.ApiException;

/** Read-only views for reconciliation and debugging. */
@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private final JournalRepository journals;
    private final LedgerEntryRepository entries;

    public LedgerController(JournalRepository journals, LedgerEntryRepository entries) {
        this.journals = journals;
        this.entries = entries;
    }

    /** For {@code wallet:<id>} the balance equals the wallet balance once all events are processed. */
    @GetMapping("/accounts/{account}/balance")
    public AccountBalance balance(@PathVariable String account) {
        long debits = entries.sumByAccount(account, Direction.DEBIT);
        long credits = entries.sumByAccount(account, Direction.CREDIT);
        return new AccountBalance(account, debits, credits, credits - debits);
    }

    @GetMapping("/journals/{reference}")
    public JournalView journal(@PathVariable String reference) {
        Journal journal = journals.findByReference(reference)
                .orElseThrow(() -> ApiException.notFound("JOURNAL_NOT_FOUND", "No journal for " + reference));
        List<EntryView> lines = entries.findByJournalId(journal.getId()).stream()
                .map(e -> new EntryView(e.getAccount(), e.getDirection(), e.getAmount()))
                .toList();
        return new JournalView(journal.getId(), journal.getReference(), journal.getType(), journal.getCreatedAt(), lines);
    }

    /** INV-4 at system level: total debits must equal total credits. */
    @GetMapping("/trial-balance")
    public TrialBalance trialBalance() {
        long debits = entries.sumAll(Direction.DEBIT);
        long credits = entries.sumAll(Direction.CREDIT);
        return new TrialBalance(debits, credits, debits == credits);
    }

    public record AccountBalance(String account, long debits, long credits, long balance) {
    }

    public record EntryView(String account, Direction direction, long amount) {
    }

    public record JournalView(UUID id, String reference, JournalType type, Instant createdAt, List<EntryView> entries) {
    }

    public record TrialBalance(long totalDebits, long totalCredits, boolean balanced) {
    }
}
