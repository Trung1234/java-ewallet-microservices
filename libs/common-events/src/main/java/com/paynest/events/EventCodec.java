package com.paynest.events;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import tools.jackson.databind.json.JsonMapper;

/**
 * Converts {@link Message} payloads to JSON envelopes and back.
 */
public final class EventCodec {

    @SuppressWarnings("unchecked")
    private static final Map<String, Class<? extends Message>> TYPES = Arrays.stream(Message.class.getPermittedSubclasses())
            .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, type -> (Class<? extends Message>) type));

    private final JsonMapper mapper;

    public EventCodec(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public static String typeOf(Message payload) {
        return payload.getClass().getSimpleName();
    }

    public String encode(UUID eventId, Message payload) {
        var envelope = new EventEnvelope(eventId, typeOf(payload), Instant.now(), EventEnvelope.CURRENT_VERSION,
                mapper.valueToTree(payload));
        return mapper.writeValueAsString(envelope);
    }

    public EventEnvelope decode(String json) {
        return mapper.readValue(json, EventEnvelope.class);
    }

    /**
     * Returns the typed payload, or {@code null} when the event type is not known (for example an
     * event added by a newer version of another service).
     */
    public Message payloadOf(EventEnvelope envelope) {
        Class<? extends Message> type = TYPES.get(envelope.eventType());
        return type == null ? null : mapper.treeToValue(envelope.payload(), type);
    }
}
