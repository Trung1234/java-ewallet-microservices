package com.paynest.web.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Every servlet service validates the JWT itself (defence in depth behind the gateway) and reads
 * the user id from the {@code sub} claim.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class})
public class SecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(JwtProperties jwt) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwt.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwt.issuer()));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties security) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid access token");
        AccessDeniedHandler forbidden = (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access denied");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/error").permitAll();
                    security.publicPaths().forEach(path -> auth.requestMatchers(path).permitAll());
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
