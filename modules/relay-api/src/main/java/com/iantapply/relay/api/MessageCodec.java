package com.iantapply.relay.api;

/**
 * Explicit, safe conversion between a plugin value and its wire representation.
 *
 * @param <T> value type handled by the codec
 */
public interface MessageCodec<T> {
    /**
     * Encodes a value for transport.
     *
     * @param value value to encode
     * @return encoded bytes
     * @throws Exception if the value cannot be encoded
     */
    byte[] encode(T value) throws Exception;

    /**
     * Decodes a transported payload.
     *
     * @param payload encoded bytes
     * @return decoded value
     * @throws Exception if the payload cannot be decoded
     */
    T decode(byte[] payload) throws Exception;

    /**
     * Identifies the encoded representation so subscribers can reject incompatible codecs.
     *
     * @return MIME-style content type
     */
    default String contentType() {
        return "application/octet-stream";
    }
}
