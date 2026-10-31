package com.paynest.events;

import java.util.UUID;

/**
 * Messages owned by ledger-service. Amounts are integer minor units.
 */
public final class LedgerMessages {

    /** Command: write the double entry (debit sender, credit recipient) for a transfer. */
    public record PostTransfer(UUID transferId, UUID fromWalletId, UUID toWalletId, long amount) implements Message {
    }

    public record LedgerPosted(UUID transferId, UUID journalId) implements Message {
    }

    public record LedgerPostingFailed(UUID transferId, String reason) implements Message {
    }

    private LedgerMessages() {
    }
}
