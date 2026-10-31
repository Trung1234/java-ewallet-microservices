package com.paynest.wallet.bank;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client for bank-adapter with a timeout per call and retries with exponential backoff.
 * <p>
 * Every retry sends the same {@code reference}. The bank is idempotent on it, so a call that
 * timed out on our side but succeeded at the bank is not executed twice (T-BNK-1, T-BNK-2).
 */
@Component
public class BankClient {

    private static final Logger log = LoggerFactory.getLogger(BankClient.class);

    private final RestClient rest;
    private final int maxAttempts;
    private final Duration backoff;

    public BankClient(RestClient.Builder builder,
                      @Value("${paynest.bank.url}") String baseUrl,
                      @Value("${paynest.bank.timeout:2s}") Duration timeout,
                      @Value("${paynest.bank.max-attempts:3}") int maxAttempts,
                      @Value("${paynest.bank.backoff:200ms}") Duration backoff) {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.rest = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    /** Pulls money from the user's bank account into PayNest (top-up). */
    public BankResult charge(UUID reference, long amount) {
        return call("/bank/charges", reference, amount);
    }

    /** Pushes money from PayNest to the user's bank account (withdraw). */
    public BankResult payout(UUID reference, long amount) {
        return call("/bank/payouts", reference, amount);
    }

    private BankResult call(String path, UUID reference, long amount) {
        String lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                rest.post().uri(path).body(new BankRequest(reference, amount)).retrieve().toBodilessEntity();
                return BankResult.ok();
            } catch (HttpClientErrorException e) {
                log.info("Bank declined {} {}: {}", path, reference, e.getStatusCode());
                return BankResult.declined("BANK_DECLINED");
            } catch (RestClientException e) {
                lastError = e.getMessage();
                log.warn("Bank call {} {} failed (attempt {}/{}): {}", path, reference, attempt, maxAttempts, lastError);
                if (attempt < maxAttempts) {
                    sleep(backoff.multipliedBy(1L << (attempt - 1)));
                }
            }
        }
        return BankResult.unavailable(lastError);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record BankRequest(UUID reference, long amount) {
    }
}
