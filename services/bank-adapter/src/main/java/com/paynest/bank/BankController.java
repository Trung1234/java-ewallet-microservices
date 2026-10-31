package com.paynest.bank;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paynest.bank.FakeBank.BankReceipt;
import com.paynest.bank.FakeBank.Chaos;

/** Internal API, only reachable from inside the cluster (not routed by the gateway). */
@RestController
@RequestMapping("/bank")
public class BankController {

    private final FakeBank bank;

    public BankController(FakeBank bank) {
        this.bank = bank;
    }

    /** Pull money from the customer's bank account (wallet top-up). */
    @PostMapping("/charges")
    public BankReceipt charge(@Valid @RequestBody BankRequest request) {
        return bank.process("CHARGE", request.reference(), request.amount());
    }

    /** Send money to the customer's bank account (wallet withdraw). */
    @PostMapping("/payouts")
    public BankReceipt payout(@Valid @RequestBody BankRequest request) {
        return bank.process("PAYOUT", request.reference(), request.amount());
    }

    @GetMapping("/chaos")
    public Chaos chaos() {
        return bank.chaos();
    }

    /** Change failure rates at runtime, e.g. for a failure drill. */
    @PutMapping("/chaos")
    public Chaos updateChaos(@Valid @RequestBody ChaosRequest request) {
        return bank.updateChaos(new Chaos(request.failureRate(), request.timeoutRate()));
    }

    public record BankRequest(@NotNull UUID reference, @Positive long amount) {
    }

    public record ChaosRequest(@DecimalMin("0.0") @DecimalMax("1.0") double failureRate,
                               @DecimalMin("0.0") @DecimalMax("1.0") double timeoutRate) {
    }
}
