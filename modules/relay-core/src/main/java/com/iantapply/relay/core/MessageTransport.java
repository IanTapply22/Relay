package com.iantapply.relay.core;

import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

public interface MessageTransport extends AutoCloseable {
    CompletionStage<Void> publish(String channel, byte[] payload);

    void start(Collection<String> channels, BiConsumer<String, byte[]> receiver);

    boolean connected();

    long reconnects();

    @Override
    void close();
}
