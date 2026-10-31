package com.paynest.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FakeBankTest {

    @Test
    void sameReferenceIsProcessedOnce() { // T-BNK-2 (bank side)
        FakeBank bank = new FakeBank(0, 0, Duration.ZERO, 1_000);
        UUID reference = UUID.randomUUID();

        var first = bank.process("CHARGE", reference, 100);
        var second = bank.process("CHARGE", reference, 100);

        assertThat(second).isSameAs(first);
    }

    @Test
    void outageAnswers503AndDoesNothing() {
        FakeBank bank = new FakeBank(1.0, 0, Duration.ZERO, 1_000);
        UUID reference = UUID.randomUUID();

        assertThatThrownBy(() -> bank.process("CHARGE", reference, 100))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        bank.updateChaos(new FakeBank.Chaos(0, 0));
        assertThat(bank.process("CHARGE", reference, 100).status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void slowAnswerHasAlreadyMovedTheMoney() {
        FakeBank bank = new FakeBank(0, 1.0, Duration.ofMillis(50), 1_000);
        UUID reference = UUID.randomUUID();

        var slow = bank.process("PAYOUT", reference, 100);
        bank.updateChaos(new FakeBank.Chaos(0, 0));

        assertThat(bank.process("PAYOUT", reference, 100)).isSameAs(slow);
    }

    @Test
    void amountAboveLimitIsDeclined() {
        FakeBank bank = new FakeBank(0, 0, Duration.ZERO, 1_000);

        assertThatThrownBy(() -> bank.process("CHARGE", UUID.randomUUID(), 1_001))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
