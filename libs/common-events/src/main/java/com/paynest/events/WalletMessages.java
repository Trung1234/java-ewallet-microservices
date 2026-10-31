package com.paynest.events;

import java.util.UUID;

/**
 * Messages owned by wallet-service. Amounts are integer minor units.
 */
public final class WalletMessages {

    public record WalletCreated(UUID walletId, String userId) implements Message {
    }

    /** Command: reserve {@code amount} on the sender's wallet. */
    public record HoldFunds(UUID transferId, UUID walletId, long amount) implements Message {
    }

    public record FundsHeld(UUID transferId, UUID walletId, long amount) implements Message {
    }

    public record FundsHoldFailed(UUID transferId, UUID walletId, String reason) implements Message {
    }

    /** Command: take the held amount from the sender and credit the recipient, in one local transaction. */
    public record CaptureFunds(UUID transferId, UUID fromWalletId, UUID toWalletId, long amount) implements Message {
    }

    public record FundsCaptured(UUID transferId, UUID fromWalletId, UUID toWalletId, long amount) implements Message {
    }

    /** Command (compensation): give a held amount back to the sender. */
    public record ReleaseFunds(UUID transferId, UUID walletId, long amount) implements Message {
    }

    public record FundsReleased(UUID transferId, UUID walletId, long amount) implements Message {
    }

    public record WalletToppedUp(UUID operationId, UUID walletId, long amount) implements Message {
    }

    public record WalletWithdrawn(UUID operationId, UUID walletId, long amount) implements Message {
    }

    private WalletMessages() {
    }
}
