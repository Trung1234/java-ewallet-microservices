package com.paynest.events;

import java.util.UUID;

/**
 * Final outcome of a transfer, published by transfer-service. Amounts are integer minor units.
 */
public final class TransferMessages {

    public record TransferCompleted(UUID transferId, UUID fromWalletId, String fromUserId,
                                    UUID toWalletId, String toUserId, long amount) implements Message {
    }

    public record TransferFailed(UUID transferId, UUID fromWalletId, String fromUserId,
                                 UUID toWalletId, String toUserId, long amount, String reason) implements Message {
    }

    private TransferMessages() {
    }
}
