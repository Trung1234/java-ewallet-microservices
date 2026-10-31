package com.paynest.wallet.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paynest.wallet.domain.TransactionType;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.domain.WalletTransaction;
import com.paynest.wallet.service.WalletService;

@RestController
@RequestMapping("/wallets/me")
public class WalletController {

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @GetMapping
    public WalletResponse me(@AuthenticationPrincipal Jwt jwt) {
        return WalletResponse.from(wallets.getOrCreate(jwt.getSubject()));
    }

    @PostMapping("/topup")
    public WalletResponse topUp(@AuthenticationPrincipal Jwt jwt,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                @Valid @RequestBody AmountRequest request) {
        return WalletResponse.from(wallets.topUp(jwt.getSubject(), keyOrRandom(idempotencyKey), request.amount()));
    }

    @PostMapping("/withdraw")
    public WalletResponse withdraw(@AuthenticationPrincipal Jwt jwt,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   @Valid @RequestBody AmountRequest request) {
        return WalletResponse.from(wallets.withdraw(jwt.getSubject(), keyOrRandom(idempotencyKey), request.amount()));
    }

    @GetMapping("/transactions")
    public List<TransactionResponse> transactions(@AuthenticationPrincipal Jwt jwt) {
        return wallets.history(jwt.getSubject()).stream().map(TransactionResponse::from).toList();
    }

    private static String keyOrRandom(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank() ? UUID.randomUUID().toString() : idempotencyKey;
    }

    /** @param amount integer minor units */
    public record AmountRequest(@NotNull @Positive @Max(1_000_000_000L) Long amount) {
    }

    public record WalletResponse(UUID id, String userId, String currency, long balance, long held, long available,
                                 Instant updatedAt) {

        static WalletResponse from(Wallet wallet) {
            return new WalletResponse(wallet.getId(), wallet.getUserId(), wallet.getCurrency(), wallet.getBalance(),
                    wallet.getHeld(), wallet.available(), wallet.getUpdatedAt());
        }
    }

    public record TransactionResponse(UUID id, TransactionType type, long amount, String reference, long balanceAfter,
                                      Instant createdAt) {

        static TransactionResponse from(WalletTransaction tx) {
            return new TransactionResponse(tx.getId(), tx.getType(), tx.getAmount(), tx.getReference(),
                    tx.getBalanceAfter(), tx.getCreatedAt());
        }
    }
}
