package com.paynest.wallet.bank;

public record BankResult(Outcome outcome, String reason) {

    public enum Outcome {
        OK,
        /** The bank said no (4xx). Retrying will not change the answer. */
        DECLINED,
        /** Errors or timeouts on every attempt. The money did not move. */
        UNAVAILABLE
    }

    public static BankResult ok() {
        return new BankResult(Outcome.OK, null);
    }

    public static BankResult declined(String reason) {
        return new BankResult(Outcome.DECLINED, reason);
    }

    public static BankResult unavailable(String reason) {
        return new BankResult(Outcome.UNAVAILABLE, reason);
    }

    public boolean isOk() {
        return outcome == Outcome.OK;
    }
}
