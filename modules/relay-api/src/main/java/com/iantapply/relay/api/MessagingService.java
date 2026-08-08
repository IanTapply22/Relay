package com.iantapply.relay.api;

import java.util.concurrent.CompletionStage;

/** Transient, at-most-once messaging. A successful publish only means Redis accepted the message. */
public interface MessagingService {
    <T> CompletionStage<MessageId> publish(Topic<T> topic, Destination destination, T payload);
    <T> Subscription subscribe(Topic<T> topic, MessageHandler<T> handler);
    MessagingStatus status();
}
