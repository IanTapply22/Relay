package com.iantapply.relay.api;

import java.util.Map;

/**
 * Optional metadata attached to a published message.
 *
 * @param correlationId related message identifier, or {@code null}
 * @param headers immutable application metadata
 */
public record PublishOptions(MessageId correlationId, Map<String, String> headers) {
    private static final PublishOptions DEFAULTS = new PublishOptions(null, Map.of());

    /** Creates options and defensively copies application headers. */
    public PublishOptions {
        headers = Map.copyOf(headers);
    }

    /**
     * Returns options with no correlation identifier or application headers.
     *
     * @return empty publish options
     */
    public static PublishOptions defaults() {
        return DEFAULTS;
    }
}
