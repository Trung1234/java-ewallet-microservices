package com.paynest.user.api;

import java.time.Instant;

import com.paynest.user.domain.KycStatus;
import com.paynest.user.domain.User;

public record UserResponse(String id, String email, String fullName, KycStatus kycStatus, Instant createdAt) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getKycStatus(), user.getCreatedAt());
    }
}
