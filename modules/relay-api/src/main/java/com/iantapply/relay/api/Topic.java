package com.iantapply.relay.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named messaging route paired with the codec for its payload type.
 *
 * @param name stable topic name shared by publishers and subscribers
 * @param codec payload codec
 * @param <T> payload type
 */
public record Topic<T>(String name, MessageCodec<T> codec) {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,127}");

    /**
     * Validates a topic name and codec.
     *
     * @param name topic name
     * @param codec payload codec
     */
    public Topic {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(codec, "codec");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid topic '" + name + "' (expected " + VALID_NAME + ")");
        }
    }

    /**
     * Creates and validates a topic.
     *
     * @param name topic name
     * @param codec payload codec
     * @param <T> payload type
     * @return validated topic
     */
    public static <T> Topic<T> of(String name, MessageCodec<T> codec) {
        return new Topic<>(name, codec);
    }

    /**
     * Tests whether a name can be used as a Relay topic.
     *
     * @param name candidate name
     * @return {@code true} if the name is valid
     */
    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }
}
