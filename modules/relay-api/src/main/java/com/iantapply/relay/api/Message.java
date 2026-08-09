package com.iantapply.relay.api;

import java.time.Instant;
import java.util.Map;

/**
 * A decoded message delivered to a topic subscriber.
 *
 * @param id unique message identifier
 * @param topic topic through which the message was decoded
 * @param payload decoded payload
 * @param origin identifier of the publishing node
 * @param destination destination selected by the publisher
 * @param createdAt publishing timestamp
 * @param correlationId related message identifier, or {@code null}
 * @param headers immutable application metadata
 * @param <T> payload type
 */
public record Message<T>(
        MessageId id,
        Topic<T> topic,
        T payload,
        String origin,
        Destination destination,
        Instant createdAt,
        MessageId correlationId,
        Map<String, String> headers) {
    /**
     * Creates a message and defensively copies its headers.
     *
     * @param id unique message identifier
     * @param topic decoding topic
     * @param payload decoded payload
     * @param origin publishing node identifier
     * @param destination intended recipients
     * @param createdAt publishing timestamp
     * @param correlationId related message identifier, or {@code null}
     * @param headers application metadata
     */
    public Message {
        headers = Map.copyOf(headers);
    }
}
