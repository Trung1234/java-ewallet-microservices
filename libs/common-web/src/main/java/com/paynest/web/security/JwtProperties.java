package com.paynest.web.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Shared HS256 settings. user-service signs tokens with this secret, every other service and the
 * gateway verify them with the same secret.
 *
 * @param secret at least 32 bytes (256 bits), as required by HS256
 * @param issuer value of the {@code iss} claim
 * @param ttl    lifetime of issued tokens
 */
@ConfigurationProperties("paynest.jwt")
public record JwtProperties(String secret, @DefaultValue("paynest") String issuer, @DefaultValue("1h") Duration ttl) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("paynest.jwt.secret (env JWT_SECRET) must be at least 32 bytes long");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
