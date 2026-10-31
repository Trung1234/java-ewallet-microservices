package com.paynest.user.domain;

public enum KycStatus {
    /** Default for a new user: no identity document submitted yet. */
    PENDING,
    VERIFIED,
    REJECTED
}
