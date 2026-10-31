package com.paynest.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.paynest.transfer.domain.Transfer;
import com.paynest.transfer.domain.TransferStatus;

import static com.paynest.transfer.domain.TransferStatus.COMPENSATING;
import static com.paynest.transfer.domain.TransferStatus.COMPLETED;
import static com.paynest.transfer.domain.TransferStatus.FAILED;
import static com.paynest.transfer.domain.TransferStatus.HELD;
import static com.paynest.transfer.domain.TransferStatus.PENDING;

/** T-TRF-7: only the transitions of product spec §5 are allowed. */
class TransferStatusTest {

    private static final Map<TransferStatus, Set<TransferStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(HELD, FAILED),
            HELD, EnumSet.of(COMPLETED, COMPENSATING),
            COMPENSATING, EnumSet.of(FAILED),
            COMPLETED, EnumSet.noneOf(TransferStatus.class),
            FAILED, EnumSet.noneOf(TransferStatus.class));

    @Test
    void transitionTableMatchesSpec() {
        for (TransferStatus from : TransferStatus.values()) {
            for (TransferStatus to : TransferStatus.values()) {
                assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    void illegalTransitionThrows() {
        Transfer transfer = new Transfer("key", "u1", UUID.randomUUID(), "u2", UUID.randomUUID(), 10);

        assertThatThrownBy(() -> transfer.transitionTo(COMPLETED)).isInstanceOf(IllegalStateException.class);
        transfer.transitionTo(HELD);
        transfer.transitionTo(COMPLETED);
        assertThatThrownBy(() -> transfer.transitionTo(FAILED)).isInstanceOf(IllegalStateException.class);
    }
}
