package com.paynest.transfer.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paynest.transfer.domain.Transfer;
import com.paynest.transfer.domain.TransferStatus;
import com.paynest.transfer.service.TransferService;
import com.paynest.web.ApiException;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    /** Returns 202: the transfer runs asynchronously, poll {@code GET /transfers/{id}} for the result. */
    @PostMapping
    public ResponseEntity<TransferResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @Valid @RequestBody CreateTransferRequest request) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-100 characters");
        }
        Transfer transfer = transfers.create(jwt.getSubject(), idempotencyKey, request.toWalletId(), request.amount());
        return ResponseEntity.accepted()
                .location(URI.create("/transfers/" + transfer.getId()))
                .body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return TransferResponse.from(transfers.get(jwt.getSubject(), id));
    }

    @GetMapping
    public List<TransferResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return transfers.list(jwt.getSubject()).stream().map(TransferResponse::from).toList();
    }

    /** @param amount integer minor units */
    public record CreateTransferRequest(@NotNull UUID toWalletId, @NotNull @Positive @Max(1_000_000_000L) Long amount) {
    }

    public record TransferResponse(UUID id, TransferStatus status, UUID fromWalletId, UUID toWalletId, long amount,
                                   String failureReason, Instant createdAt, Instant updatedAt) {

        static TransferResponse from(Transfer t) {
            return new TransferResponse(t.getId(), t.getStatus(), t.getFromWalletId(), t.getToWalletId(), t.getAmount(),
                    t.getFailureReason(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
