package com.iantapply.relay.core;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;

/** Deterministic transport for contract tests and local embedding. */
public final class InMemoryTransport implements MessageTransport {
    private static final Set<InMemoryTransport> INSTANCES = new CopyOnWriteArraySet<>();
    private volatile Set<String> channels = Set.of();
    private volatile BiConsumer<String, byte[]> receiver;
    private volatile boolean open;

    public CompletionStage<Void> publish(String channel, byte[] payload) {
        if (!open) return CompletableFuture.failedFuture(new IllegalStateException("Transport is disconnected"));
        for (InMemoryTransport transport : INSTANCES) {
            if (transport.open && transport.channels.contains(channel)) {
                transport.receiver.accept(channel, payload.clone());
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public void start(Collection<String> channels, BiConsumer<String, byte[]> receiver) {
        this.channels = Set.copyOf(channels);
        this.receiver = receiver;
        this.open = true;
        INSTANCES.add(this);
    }

    public boolean connected() {
        return open;
    }

    public long reconnects() {
        return 0;
    }

    public void close() {
        open = false;
        INSTANCES.remove(this);
    }
}
