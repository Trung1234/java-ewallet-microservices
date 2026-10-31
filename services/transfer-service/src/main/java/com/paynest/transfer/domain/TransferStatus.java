package com.paynest.transfer.domain;

/**
 * Transfer state machine (product spec §5):
 * <pre>
 * PENDING -> HELD -> COMPLETED
 * PENDING -> FAILED                 (hold refused, e.g. insufficient funds)
 * HELD    -> COMPENSATING -> FAILED (error after the hold, money released)
 * </pre>
 */
public enum TransferStatus {
    PENDING,
    HELD,
    COMPLETED,
    COMPENSATING,
    FAILED;

    public boolean canTransitionTo(TransferStatus next) {
        return switch (this) {
            case PENDING -> next == HELD || next == FAILED;
            case HELD -> next == COMPLETED || next == COMPENSATING;
            case COMPENSATING -> next == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED;
    }
}
