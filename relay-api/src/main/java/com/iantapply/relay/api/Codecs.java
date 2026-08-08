package com.iantapply.relay.api;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public final class Codecs {
    private static final Gson GSON = new Gson();

    private Codecs() {}

    public static MessageCodec<String> utf8() {
        return new MessageCodec<>() {
            public byte[] encode(String value) { return Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8); }
            public String decode(byte[] payload) { return new String(payload, StandardCharsets.UTF_8); }
            public String contentType() { return "text/plain; charset=utf-8"; }
        };
    }

    public static MessageCodec<byte[]> bytes() {
        return new MessageCodec<>() {
            public byte[] encode(byte[] value) { return Arrays.copyOf(value, value.length); }
            public byte[] decode(byte[] payload) { return Arrays.copyOf(payload, payload.length); }
        };
    }

    public static <T> MessageCodec<T> json(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return new MessageCodec<>() {
            public byte[] encode(T value) { return GSON.toJson(value).getBytes(StandardCharsets.UTF_8); }
            public T decode(byte[] payload) { return GSON.fromJson(new String(payload, StandardCharsets.UTF_8), type); }
            public String contentType() { return "application/json"; }
        };
    }
}
