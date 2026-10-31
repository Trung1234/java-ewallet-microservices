package com.paynest.notification.service;

import java.time.Instant;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.paynest.notification.domain.Preference;
import com.paynest.notification.domain.PreferenceRepository;

/** Preferences are read for every notification, so they are cached in Redis (README §6). */
@Service
public class PreferenceService {

    static final String CACHE = "preferences";

    private final PreferenceRepository preferences;

    public PreferenceService(PreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Cacheable(cacheNames = CACHE, key = "#userId")
    public PreferenceView get(String userId) {
        return preferences.findById(userId).map(PreferenceView::from).orElseGet(() -> PreferenceView.defaults(userId));
    }

    /** Writes MongoDB and refreshes the cache entry in the same call. */
    @CachePut(cacheNames = CACHE, key = "#userId")
    public PreferenceView update(String userId, boolean emailEnabled, boolean pushEnabled) {
        return PreferenceView.from(preferences.save(new Preference(userId, emailEnabled, pushEnabled, Instant.now())));
    }
}
