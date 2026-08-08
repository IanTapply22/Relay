package com.iantapply.relay.api;

import java.time.Instant;
import java.util.Map;

public record Message<T>(
        MessageId id,
        Topic<T> topic,
        T payload,
        String origin,
        Destination destination,
        Instant createdAt,
        MessageId correlationId,
        Map<String, String> headers) {
    public Message {
        headers = Map.copyOf(headers);
    }
}
