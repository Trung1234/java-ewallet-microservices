package com.paynest.wallet.domain;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID walletId, long requested, long available) {
        super("Wallet " + walletId + " has " + available + " available, " + requested + " requested");
    }
}
