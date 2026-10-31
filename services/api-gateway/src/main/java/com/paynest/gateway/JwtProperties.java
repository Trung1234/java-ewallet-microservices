package com.paynest.gateway;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Same HS256 secret as user-service, which signs the tokens. (The gateway is reactive, so it does
 * not use the servlet-based common-web library.)
 */
@ConfigurationProperties("paynest.jwt")
public record JwtProperties(String secret, @DefaultValue("paynest") String issuer) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("paynest.jwt.secret (env JWT_SECRET) must be at least 32 bytes long");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
