package com.iantapply.relay.core;

import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.MessageId;
import java.time.Instant;
import java.util.Map;

/**
 * Transport-neutral representation of one encoded Relay message.
 *
 * @param schema wire schema version
 * @param id unique message identifier
 * @param topic topic name
 * @param origin publishing node identifier
 * @param destination intended recipients
 * @param createdAt publishing timestamp
 * @param contentType codec representation identifier
 * @param correlationId related message identifier, or {@code null}
 * @param headers immutable application metadata
 * @param payload encoded application payload
 */
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
    /** Current Relay envelope schema version. */
    public static final int CURRENT_SCHEMA = 1;

    /**
     * Creates an envelope and defensively copies mutable data.
     *
     * @param schema wire schema version
     * @param id unique message identifier
     * @param topic topic name
     * @param origin publishing node identifier
     * @param destination intended recipients
     * @param createdAt publishing timestamp
     * @param contentType codec representation identifier
     * @param correlationId related message identifier, or {@code null}
     * @param headers application metadata
     * @param payload encoded application payload
     */
    public WireEnvelope {
        headers = Map.copyOf(headers);
        payload = payload.clone();
    }

    /**
     * Returns a defensive copy of the encoded application payload.
     *
     * @return copied payload bytes
     */
    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
