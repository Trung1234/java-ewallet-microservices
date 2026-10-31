package com.paynest.gateway;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Optional;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    /**
     * Rate-limit bucket per user (JWT subject); anonymous calls such as /auth/login are limited per
     * client IP, which also slows down password guessing.
     */
    @Bean
    KeyResolver principalOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(InetSocketAddress::getAddress)
                        .map(address -> "ip:" + address.getHostAddress())
                        .orElse("ip:unknown")));
    }
}
