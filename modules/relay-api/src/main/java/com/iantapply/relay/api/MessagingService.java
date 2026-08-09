package com.iantapply.relay.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Transient, at-most-once messaging. A successful publish only means Redis accepted the message. */
public interface MessagingService {
    /**
     * Publishes a payload asynchronously.
     *
     * @param topic topic and codec used to encode the payload
     * @param destination target nodes
     * @param payload value to publish
     * @param <T> payload type
     * @return a stage completed with the message identifier once the transport accepts it
     */
    <T> CompletionStage<MessageId> publish(Topic<T> topic, Destination destination, T payload);

    /**
     * Publishes a payload with correlation and application metadata.
     *
     * <p>The default implementation preserves compatibility with third-party implementations that
     * only support metadata-free publication.
     *
     * @param topic topic and codec used to encode the payload
     * @param destination target nodes
     * @param payload value to publish
     * @param options optional correlation identifier and headers
     * @param <T> payload type
     * @return a stage completed when the transport accepts the publication
     */
    default <T> CompletionStage<MessageId> publish(
            Topic<T> topic, Destination destination, T payload, PublishOptions options) {
        if (PublishOptions.defaults().equals(options)) return publish(topic, destination, payload);
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("This messaging service does not support publish options"));
    }

    /**
     * Registers a handler for a topic.
     *
     * @param topic topic and codec used to decode messages
     * @param handler asynchronous message handler
     * @param <T> payload type
     * @return a handle that removes the handler when closed
     */
    <T> Subscription subscribe(Topic<T> topic, MessageHandler<T> handler);

    /**
     * Returns a point-in-time view of messaging health and dispatch capacity.
     *
     * @return current status
     */
    MessagingStatus status();
}
