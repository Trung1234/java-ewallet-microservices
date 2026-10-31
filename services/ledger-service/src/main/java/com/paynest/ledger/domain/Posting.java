package com.paynest.ledger.domain;

/** One side of a journal before it is written. */
public record Posting(String account, Direction direction, long amount) {

    public static Posting debit(String account, long amount) {
        return new Posting(account, Direction.DEBIT, amount);
    }

    public static Posting credit(String account, long amount) {
        return new Posting(account, Direction.CREDIT, amount);
    }

    long signed() {
        return direction == Direction.DEBIT ? amount : -amount;
    }
}
