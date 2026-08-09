package com.iantapply.relay.api;

import java.util.UUID;

/**
 * Strongly typed identifier for a Relay message.
 *
 * @param value underlying UUID
 */
public record MessageId(UUID value) {
    /**
     * Creates an identifier from a UUID.
     *
     * @param value underlying UUID
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public MessageId {
        if (value == null) throw new NullPointerException("value");
    }

    /**
     * Generates a new random identifier.
     *
     * @return generated identifier
     */
    public static MessageId random() {
        return new MessageId(UUID.randomUUID());
    }

    /**
     * Parses a standard UUID string.
     *
     * @param value UUID text
     * @return parsed identifier
     * @throws IllegalArgumentException if {@code value} is not a UUID
     */
    public static MessageId parse(String value) {
        return new MessageId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
