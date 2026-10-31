package com.paynest.ledger.domain;

import java.util.UUID;

/** Ledger account names. */
public final class Accounts {

    /** Money that entered or left PayNest through the bank. */
    public static final String BANK = "external:bank";

    public static String wallet(UUID walletId) {
        return "wallet:" + walletId;
    }

    private Accounts() {
    }
}
