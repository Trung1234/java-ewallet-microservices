package com.paynest.notification.domain;

public enum Channel {
    /** Shown in the app inbox ({@code GET /notifications}); always on. */
    IN_APP,
    EMAIL,
    PUSH
}
