package com.paynest.web.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicPaths request paths that do not need a JWT, e.g. {@code /auth/**}
 */
@ConfigurationProperties("paynest.security")
public record SecurityProperties(List<String> publicPaths) {

    public SecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
