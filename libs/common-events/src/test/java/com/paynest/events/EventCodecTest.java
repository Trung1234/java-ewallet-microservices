package com.paynest.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.paynest.events.TransferMessages.TransferCompleted;
import com.paynest.events.WalletMessages.FundsHeld;

/** T-EVT-1: every message carries eventId, eventType, occurredAt and version. */
class EventCodecTest {

    private final EventCodec codec = new EventCodec(JsonMapper.builder().build());

    @Test
    void envelopeHasRequiredFieldsAndRoundTrips() {
        UUID eventId = UUID.randomUUID();
        var payload = new TransferCompleted(UUID.randomUUID(), UUID.randomUUID(), "user-a", UUID.randomUUID(), "user-b", 5_000);

        EventEnvelope envelope = codec.decode(codec.encode(eventId, payload));

        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.eventType()).isEqualTo("TransferCompleted");
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.version()).isEqualTo(EventEnvelope.CURRENT_VERSION);
        assertThat(codec.payloadOf(envelope)).isEqualTo(payload);
    }

    @Test
    void unknownEventTypeGivesNullPayload() {
        String json = codec.encode(UUID.randomUUID(), new FundsHeld(UUID.randomUUID(), UUID.randomUUID(), 1))
                .replace("FundsHeld", "SomethingNew");

        assertThat(codec.payloadOf(codec.decode(json))).isNull();
    }
}
