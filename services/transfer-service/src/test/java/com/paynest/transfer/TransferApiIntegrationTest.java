package com.paynest.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.paynest.events.EventCodec;
import com.paynest.events.Topics;
import com.paynest.events.WalletMessages.HoldFunds;
import com.paynest.outbox.OutboxRepository;
import com.paynest.transfer.domain.Transfer;
import com.paynest.transfer.domain.WalletDirectoryEntry;
import com.paynest.transfer.domain.WalletDirectoryRepository;
import com.paynest.transfer.service.TransferService;

/** T-TRF-3 .. T-TRF-6, T-TRF-9. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TransferApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    WalletDirectoryRepository directory;

    @Autowired
    TransferService transferService;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    EventCodec codec;

    @Autowired
    JdbcTemplate jdbc;

    String alice;
    String bob;
    UUID aliceWallet;
    UUID bobWallet;

    @BeforeEach
    void setUp() {
        alice = "alice-" + UUID.randomUUID();
        bob = "bob-" + UUID.randomUUID();
        aliceWallet = directory.save(new WalletDirectoryEntry(UUID.randomUUID(), alice)).getWalletId();
        bobWallet = directory.save(new WalletDirectoryEntry(UUID.randomUUID(), bob)).getWalletId();
    }

    @Test
    void createReturns202AndQueuesHoldCommand() throws Exception {
        String body = mvc.perform(transfer(alice, "key-1", bobWallet, 2_500))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        UUID transferId = UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        var command = outbox.findByTopicOrderByCreatedAt(Topics.WALLET_COMMANDS).stream()
                .filter(event -> event.getAggregateId().equals(aliceWallet.toString()))
                .map(event -> codec.payloadOf(codec.decode(event.getPayload())))
                .toList();
        assertThat(command).containsExactly(new HoldFunds(transferId, aliceWallet, 2_500));
    }

    @Test
    void sameKeyTwiceCreatesOneTransfer() throws Exception { // T-TRF-3, INV-3
        String first = mvc.perform(transfer(alice, "key-2", bobWallet, 100)).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(transfer(alice, "key-2", bobWallet, 100))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(idOf(second)).isEqualTo(idOf(first));
        assertThat(countTransfers(alice)).isEqualTo(1);

        mvc.perform(transfer(alice, "key-2", bobWallet, 999))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void sameKeyConcurrentlyCreatesExactlyOneTransfer() throws Exception { // T-TRF-4, INV-3
        Set<UUID> ids = new HashSet<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            List<Callable<Transfer>> calls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                calls.add(() -> transferService.create(alice, "key-race", bobWallet, 300));
            }
            for (Future<Transfer> result : pool.invokeAll(calls)) {
                ids.add(result.get().getId());
            }
        }
        assertThat(ids).hasSize(1);
        assertThat(countTransfers(alice)).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception { // T-TRF-5
        mvc.perform(post("/transfers").with(jwt().jwt(j -> j.subject(alice)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toWalletId\":\"" + bobWallet + "\",\"amount\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void selfTransferAndUnknownRecipientAreRejected() throws Exception { // T-TRF-6
        mvc.perform(transfer(alice, "k-self", aliceWallet, 100))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_TRANSFER"));
        mvc.perform(transfer(alice, "k-unknown", UUID.randomUUID(), 100))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"));
    }

    @Test
    void statusOfUnknownOrForeignTransfer() throws Exception { // T-TRF-9
        String created = mvc.perform(transfer(alice, "k-9", bobWallet, 100)).andReturn().getResponse().getContentAsString();

        mvc.perform(get("/transfers/" + UUID.randomUUID()).with(jwt().jwt(j -> j.subject(alice))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/transfers/" + idOf(created)).with(jwt().jwt(j -> j.subject("mallory"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/transfers/" + idOf(created)).with(jwt().jwt(j -> j.subject(bob))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100));
    }

    private MockHttpServletRequestBuilder transfer(String userId, String key, UUID toWalletId, long amount) {
        return post("/transfers").with(jwt().jwt(j -> j.subject(userId)))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toWalletId\":\"" + toWalletId + "\",\"amount\":" + amount + "}");
    }

    private static String idOf(String json) {
        return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private int countTransfers(String userId) {
        return jdbc.queryForObject("SELECT count(*) FROM transfers WHERE from_user_id = ?", Integer.class, userId);
    }
}
