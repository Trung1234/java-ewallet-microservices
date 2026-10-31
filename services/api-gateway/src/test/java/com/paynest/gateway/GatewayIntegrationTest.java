package com.paynest.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** T-GW-1 .. T-GW-3 against a stub downstream service and a real Redis. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "RATE_LIMIT_REPLENISH_RATE=1", "RATE_LIMIT_BURST_CAPACITY=2"})
@AutoConfigureWebTestClient
@Testcontainers
class GatewayIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8.6.8").withExposedPorts(6379);

    static final Map<String, String> received = new ConcurrentHashMap<>();
    static final HttpServer downstream = startDownstream();

    @Autowired
    WebTestClient client;

    @Autowired
    JwtProperties jwt;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + downstream.getAddress().getPort();
        for (String service : List.of("USER", "WALLET", "TRANSFER", "LEDGER", "NOTIFICATION")) {
            registry.add(service + "_SERVICE_URL", () -> url);
        }
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop(0);
    }

    @Test
    void requestWithoutValidTokenIsRejected() { // T-GW-1
        client.get().uri("/wallets/me/transactions").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
        client.get().uri("/wallets/me/transactions").header("Authorization", "Bearer forged.token.value").exchange()
                .expectStatus().isUnauthorized();
        assertThat(received).doesNotContainKey("/wallets/me/transactions");
    }

    @Test
    void requestsAreRoutedWithTheTokenForwarded() { // T-GW-3
        String token = token("user-routing");
        for (String path : List.of("/users/me", "/transfers/123", "/ledger/trial-balance", "/notifications", "/preferences")) {
            client.get().uri(path).header("Authorization", "Bearer " + token).exchange().expectStatus().isOk();
            assertThat(received.get(path)).as(path).isEqualTo("Bearer " + token);
        }
        client.post().uri("/auth/login").exchange().expectStatus().isOk();
    }

    @Test
    void tooManyRequestsGet429() { // T-GW-2
        String token = token("user-greedy");
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            HttpStatusCode status = client.get().uri("/wallets/me").header("Authorization", "Bearer " + token)
                    .exchange().returnResult(String.class).getStatus();
            statuses.add(status.value());
        }
        assertThat(statuses).contains(200, 429);
    }

    private String token(String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(jwt.issuer()).subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwt.secretKey()))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** Stands in for every downstream service: records the Authorization header per path. */
    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                received.put(exchange.getRequestURI().getPath(), auth == null ? "" : auth);
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
