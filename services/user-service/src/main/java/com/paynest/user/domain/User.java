package com.paynest.user.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private String fullName;

    private KycStatus kycStatus;

    private Instant kycUpdatedAt;

    private Instant createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.kycStatus = KycStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void updateKyc(KycStatus status) {
        this.kycStatus = status;
        this.kycUpdatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
