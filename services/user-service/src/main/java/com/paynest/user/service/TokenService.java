package com.paynest.user.service;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.paynest.user.domain.User;
import com.paynest.web.security.JwtProperties;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties jwt;

    public TokenService(JwtEncoder encoder, JwtProperties jwt) {
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** The {@code sub} claim is the user id; every service uses it to find "my" data. */
    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.issuer())
                .subject(user.getId())
                .issuedAt(now)
                .expiresAt(now.plus(jwt.ttl()))
                .claim("email", user.getEmail())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public long ttlSeconds() {
        return jwt.ttl().toSeconds();
    }
}
