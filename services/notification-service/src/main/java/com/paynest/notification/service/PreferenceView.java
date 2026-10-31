package com.paynest.notification.service;

import java.io.Serializable;

import com.paynest.notification.domain.Channel;
import com.paynest.notification.domain.Preference;

/** Cached in Redis, hence {@link Serializable}. In-app notifications cannot be turned off. */
public record PreferenceView(String userId, boolean emailEnabled, boolean pushEnabled) implements Serializable {

    static PreferenceView defaults(String userId) {
        return new PreferenceView(userId, true, false);
    }

    static PreferenceView from(Preference preference) {
        return new PreferenceView(preference.userId(), preference.emailEnabled(), preference.pushEnabled());
    }

    public boolean allows(Channel channel) {
        return switch (channel) {
            case IN_APP -> true;
            case EMAIL -> emailEnabled;
            case PUSH -> pushEnabled;
        };
    }
}
