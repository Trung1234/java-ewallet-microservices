package com.paynest.notification.sender;

public class TemporarySendException extends RuntimeException {

    public TemporarySendException(String message) {
        super(message);
    }
}
