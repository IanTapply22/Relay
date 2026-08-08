package com.iantapply.relay.core;

import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.MessageId;
import java.time.Instant;
import java.util.Map;

public record WireEnvelope(
        int schema,
        MessageId id,
        String topic,
        String origin,
        Destination destination,
        Instant createdAt,
        String contentType,
        MessageId correlationId,
        Map<String, String> headers,
        byte[] payload) {
    public static final int CURRENT_SCHEMA = 1;

    public WireEnvelope {
        headers = Map.copyOf(headers);
        payload = payload.clone();
    }

    @Override public byte[] payload() { return payload.clone(); }
}
