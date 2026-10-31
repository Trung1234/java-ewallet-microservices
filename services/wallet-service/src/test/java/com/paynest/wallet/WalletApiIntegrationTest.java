package com.paynest.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.paynest.wallet.bank.BankClient;
import com.paynest.wallet.bank.BankResult;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.service.WalletService;

/** Week 1 tests T-WAL-1 .. T-WAL-8 plus bank behaviour (T-BNK-1 from the wallet side). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class WalletApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    WalletService walletService;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    BankClient bank;

    String user;

    @BeforeEach
    void setUp() {
        user = "user-" + UUID.randomUUID();
        when(bank.charge(any(), anyLong())).thenReturn(BankResult.ok());
        when(bank.payout(any(), anyLong())).thenReturn(BankResult.ok());
    }

    @Test
    void newWalletHasZeroBalance() throws Exception { // T-WAL-1
        mvc.perform(get("/wallets/me").with(jwt().jwt(j -> j.subject(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("VND"));
    }

    @Test
    void topUpAddsAmountAndRecordsTransaction() throws Exception { // T-WAL-2
        mvc.perform(topUp(user, 50_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50_000));

        mvc.perform(get("/wallets/me/transactions").with(jwt().jwt(j -> j.subject(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TOPUP"))
                .andExpect(jsonPath("$[0].amount").value(50_000));
    }

    @Test
    void withdrawWithinBalance() throws Exception { // T-WAL-3
        mvc.perform(topUp(user, 1_000)).andExpect(status().isOk());

        mvc.perform(withdraw(user, 400))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(600))
                .andExpect(jsonPath("$.held").value(0));
    }

    @Test
    void withdrawMoreThanBalanceIsRejected() throws Exception { // T-WAL-4, INV-1
        mvc.perform(topUp(user, 1_000)).andExpect(status().isOk());

        mvc.perform(withdraw(user, 1_001))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(walletService.getOrCreate(user).getBalance()).isEqualTo(1_000);
    }

    @Test
    void zeroOrNegativeAmountIsRejected() throws Exception { // T-WAL-5
        mvc.perform(topUp(user, 0)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(withdraw(user, -5)).andExpect(status().isBadRequest());
    }

    @Test
    void missingOrInvalidTokenIsRejected() throws Exception { // T-WAL-6
        mvc.perform(get("/wallets/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/wallets/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void databaseRejectsNegativeBalance() { // T-WAL-7
        Wallet wallet = walletService.getOrCreate(user);

        assertThatThrownBy(() -> jdbc.update("UPDATE wallets SET balance = -1 WHERE id = ?", wallet.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentTopUpsLoseNoUpdate() throws Exception { // T-WAL-8
        walletService.getOrCreate(user);
        int requests = 20;
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            List<Callable<Wallet>> calls = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                calls.add(() -> walletService.topUp(user, UUID.randomUUID().toString(), 100));
            }
            for (Future<Wallet> result : pool.invokeAll(calls)) {
                result.get();
            }
        }
        assertThat(walletService.getOrCreate(user).getBalance()).isEqualTo(requests * 100L);
    }

    @Test
    void sameIdempotencyKeyTopsUpOnce() throws Exception {
        mvc.perform(topUp(user, 700).header("Idempotency-Key", "k-1")).andExpect(status().isOk());
        mvc.perform(topUp(user, 700).header("Idempotency-Key", "k-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(700));
        mvc.perform(topUp(user, 999).header("Idempotency-Key", "k-1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        verify(bank, times(1)).charge(any(), anyLong());
    }

    @Test
    void bankUnavailableMovesNoMoney() throws Exception { // T-BNK-1 (wallet side)
        mvc.perform(topUp(user, 1_000)).andExpect(status().isOk());
        when(bank.charge(any(), anyLong())).thenReturn(BankResult.unavailable("timeout"));
        when(bank.payout(any(), anyLong())).thenReturn(BankResult.unavailable("timeout"));

        mvc.perform(topUp(user, 500)).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("BANK_UNAVAILABLE"));
        mvc.perform(withdraw(user, 300)).andExpect(status().isBadGateway());

        Wallet wallet = walletService.getOrCreate(user);
        assertThat(wallet.getBalance()).isEqualTo(1_000);
        assertThat(wallet.getHeld()).as("hold released after failed payout").isZero();
    }

    private MockHttpServletRequestBuilder topUp(String userId, long amount) {
        return post("/wallets/me/topup").with(jwt().jwt(j -> j.subject(userId)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}");
    }

    private MockHttpServletRequestBuilder withdraw(String userId, long amount) {
        return post("/wallets/me/withdraw").with(jwt().jwt(j -> j.subject(userId)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}");
    }
}
