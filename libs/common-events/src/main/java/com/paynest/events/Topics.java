package com.paynest.events;

import java.util.List;

/**
 * Kafka topic names. Message keys: wallet topics use the wallet id, the others use the transfer id,
 * so all messages of one aggregate land on the same partition and keep their order.
 */
public final class Topics {

    /** Commands from the transfer saga to wallet-service: HoldFunds, CaptureFunds, ReleaseFunds. */
    public static final String WALLET_COMMANDS = "wallet.commands";

    /** Events from wallet-service: WalletCreated, FundsHeld, FundsCaptured, WalletToppedUp, ... */
    public static final String WALLET_EVENTS = "wallet.events";

    /** Commands from the transfer saga to ledger-service: PostTransfer. */
    public static final String LEDGER_COMMANDS = "ledger.commands";

    /** Events from ledger-service: LedgerPosted, LedgerPostingFailed. */
    public static final String LEDGER_EVENTS = "ledger.events";

    /** Outcome of a transfer: TransferCompleted, TransferFailed. */
    public static final String TRANSFER_EVENTS = "wallet.transfer.events";

    public static final List<String> ALL = List.of(WALLET_COMMANDS, WALLET_EVENTS, LEDGER_COMMANDS, LEDGER_EVENTS, TRANSFER_EVENTS);

    private Topics() {
    }
}
