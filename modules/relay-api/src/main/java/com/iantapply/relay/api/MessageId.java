package com.iantapply.relay.api;

import java.util.UUID;

public record MessageId(UUID value) {
    public MessageId {
        if (value == null) throw new NullPointerException("value");
    }

    public static MessageId random() {
        return new MessageId(UUID.randomUUID());
    }

    public static MessageId parse(String value) {
        return new MessageId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
