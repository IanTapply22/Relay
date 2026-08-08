package com.iantapply.relay.api;

import java.util.Objects;
import java.util.regex.Pattern;

public record Topic<T>(String name, MessageCodec<T> codec) {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9][a-z0-9._:-]{1,127}");

    public Topic {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(codec, "codec");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid topic '" + name + "' (expected " + VALID_NAME + ")");
        }
    }

    public static <T> Topic<T> of(String name, MessageCodec<T> codec) {
        return new Topic<>(name, codec);
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }
}
