package com.iantapply.relay.api;

/** Explicit, safe conversion between a plugin value and its wire representation. */
public interface MessageCodec<T> {
    byte[] encode(T value) throws Exception;

    T decode(byte[] payload) throws Exception;

    default String contentType() {
        return "application/octet-stream";
    }
}
