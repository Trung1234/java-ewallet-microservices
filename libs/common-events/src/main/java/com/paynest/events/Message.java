package com.paynest.events;

/**
 * A command or event payload carried inside an {@link EventEnvelope}.
 * <p>
 * The interface is sealed so that {@link EventCodec} can discover every known type, and the
 * wire name of a type is its simple class name (for example {@code "FundsHeld"}).
 */
public sealed interface Message permits
        WalletMessages.WalletCreated,
        WalletMessages.HoldFunds,
        WalletMessages.FundsHeld,
        WalletMessages.FundsHoldFailed,
        WalletMessages.CaptureFunds,
        WalletMessages.FundsCaptured,
        WalletMessages.ReleaseFunds,
        WalletMessages.FundsReleased,
        WalletMessages.WalletToppedUp,
        WalletMessages.WalletWithdrawn,
        LedgerMessages.PostTransfer,
        LedgerMessages.LedgerPosted,
        LedgerMessages.LedgerPostingFailed,
        TransferMessages.TransferCompleted,
        TransferMessages.TransferFailed {
}
