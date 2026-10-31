package com.paynest.user.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.paynest.web.security.JwtProperties;

@Configuration(proxyBeanMethods = false)
public class AuthConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Signs tokens with the same HS256 secret the other services use to verify them. */
    @Bean
    JwtEncoder jwtEncoder(JwtProperties jwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwt.secretKey()));
    }
}
