package com.iantapply.relay.core;

import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

/** Byte-oriented publish/subscribe transport used by Relay's typed messaging layer. */
public interface MessageTransport extends AutoCloseable {
    /**
     * Publishes bytes to one transport channel.
     *
     * @param channel destination channel
     * @param payload encoded Relay envelope
     * @return stage completed when the transport accepts the publication
     */
    CompletionStage<Void> publish(String channel, byte[] payload);

    /**
     * Starts receiving messages from a fixed set of channels.
     *
     * @param channels channels to consume
     * @param receiver callback receiving a channel and encoded envelope
     */
    void start(Collection<String> channels, BiConsumer<String, byte[]> receiver);

    /**
     * Reports current transport connectivity.
     *
     * @return {@code true} when connected
     */
    boolean connected();

    /**
     * Reports publication-side connectivity.
     *
     * @return {@code true} when the transport can currently publish
     */
    default boolean publisherConnected() {
        return connected();
    }

    /**
     * Reports subscription-side connectivity.
     *
     * @return {@code true} when the transport is currently subscribed
     */
    default boolean subscriberConnected() {
        return connected();
    }

    /**
     * Returns the number of successful subscriber reconnections.
     *
     * @return reconnect count
     */
    long reconnects();

    /** Stops publication and reception and releases transport resources. */
    @Override
    void close();
}
