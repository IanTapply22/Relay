package com.iantapply.relay.core;

import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.Message;
import com.iantapply.relay.api.MessageHandler;
import com.iantapply.relay.api.MessageId;
import com.iantapply.relay.api.MessagingService;
import com.iantapply.relay.api.MessagingStatus;
import com.iantapply.relay.api.PublishOptions;
import com.iantapply.relay.api.Subscription;
import com.iantapply.relay.api.Topic;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Default typed messaging implementation backed by a pluggable {@link MessageTransport}. */
public final class DefaultMessagingService implements MessagingService, AutoCloseable {
    private final RelayConfig config;
    private final MessageTransport transport;
    private final EnvelopeCodec envelopes;
    private final ChannelRouter channels;
    private final ThreadPoolExecutor dispatch;
    private final Logger logger;
    private final Map<String, CopyOnWriteArrayList<RegisteredSubscription<?>>> subscriptions =
            new ConcurrentHashMap<>();
    private final LongAdder published = new LongAdder();
    private final LongAdder received = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder dispatchQueueDrops = new LongAdder();
    private final LongAdder handlerFailures = new LongAdder();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Starts a messaging service and its transport subscriptions.
     *
     * @param config validated node configuration
     * @param transport transport used for publication and reception
     * @param logger destination for operational warnings
     */
    public DefaultMessagingService(RelayConfig config, MessageTransport transport, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.envelopes = new EnvelopeCodec(config.maximumPayloadBytes(), config.maximumMessageAge());
        this.channels = new ChannelRouter(config.namespace());
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "relay-dispatch");
            thread.setDaemon(true);
            return thread;
        };
        this.dispatch = new ThreadPoolExecutor(
                config.dispatchWorkers(),
                config.dispatchWorkers(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(config.dispatchQueueCapacity()),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
        try {
            transport.start(channels.subscriptions(config), this::receive);
        } catch (RuntimeException exception) {
            dispatch.shutdownNow();
            transport.close();
            throw exception;
        }
    }

    @Override
    public <T> CompletionStage<MessageId> publish(Topic<T> topic, Destination destination, T payload) {
        return publish(topic, destination, payload, PublishOptions.defaults());
    }

    @Override
    public <T> CompletionStage<MessageId> publish(
            Topic<T> topic, Destination destination, T payload, PublishOptions options) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(options, "options");
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Relay is closed"));
        final MessageId id = MessageId.random();
        final byte[] encoded;
        try {
            byte[] body = topic.codec().encode(payload);
            if (body == null) throw new IllegalArgumentException("Codec returned a null payload");
            WireEnvelope envelope = new WireEnvelope(
                    WireEnvelope.CURRENT_SCHEMA,
                    id,
                    topic.name(),
                    config.nodeId(),
                    destination,
                    Instant.now(),
                    topic.codec().contentType(),
                    options.correlationId(),
                    options.headers(),
                    body);
            encoded = envelopes.encode(envelope);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return transport.publish(channels.channel(destination), encoded).thenApply(ignored -> {
            published.increment();
            return id;
        });
    }

    @Override
    public <T> Subscription subscribe(Topic<T> topic, MessageHandler<T> handler) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(handler, "handler");
        if (closed.get()) throw new IllegalStateException("Relay is closed");
        RegisteredSubscription<T> subscription = new RegisteredSubscription<>(topic, handler);
        subscriptions.compute(topic.name(), (ignored, listeners) -> {
            CopyOnWriteArrayList<RegisteredSubscription<?>> current =
                    listeners == null ? new CopyOnWriteArrayList<>() : listeners;
            if (current.stream().anyMatch(listener -> !listener.topic
                    .codec()
                    .contentType()
                    .equals(topic.codec().contentType()))) {
                throw new IllegalArgumentException("Conflicting content type for Relay topic " + topic.name());
            }
            current.add(subscription);
            return current;
        });
        return subscription;
    }

    private void receive(String channel, byte[] encoded) {
        if (closed.get()) return;
        final WireEnvelope envelope;
        try {
            envelope = envelopes.decode(encoded, Instant.now());
        } catch (Exception exception) {
            rejected.increment();
            logger.log(
                    Level.WARNING, "Rejected Relay message on {0}: {1}", new Object[] {channel, exception.getMessage()
                    });
            return;
        }
        String expectedChannel = channels.channel(envelope.destination());
        if (!expectedChannel.equals(channel)) {
            rejected.increment();
            logger.log(Level.WARNING, "Rejected Relay message {0}: destination does not match channel", envelope.id());
            return;
        }
        received.increment();
        List<RegisteredSubscription<?>> listeners = subscriptions.get(envelope.topic());
        if (listeners == null) return;
        for (RegisteredSubscription<?> listener : listeners) {
            if (!listener.active()) continue;
            try {
                dispatch.execute(() -> listener.deliver(envelope));
            } catch (RejectedExecutionException exception) {
                dispatchQueueDrops.increment();
                logger.log(Level.WARNING, "Relay dispatch queue is full; dropped message {0}", envelope.id());
            }
        }
    }

    @Override
    public MessagingStatus status() {
        int count = subscriptions.values().stream()
                .mapToInt(list -> (int)
                        list.stream().filter(RegisteredSubscription::active).count())
                .sum();
        return new MessagingStatus(
                transport.publisherConnected(),
                transport.subscriberConnected(),
                config.nodeId(),
                count,
                dispatch.getQueue().size(),
                dispatch.getActiveCount(),
                dispatch.getMaximumPoolSize());
    }

    /**
     * Returns cumulative operational counters and current transport state.
     *
     * @return current metrics snapshot
     */
    public RelayMetrics metrics() {
        return new RelayMetrics(
                published.sum(),
                received.sum(),
                rejected.sum(),
                dispatchQueueDrops.sum(),
                handlerFailures.sum(),
                transport.reconnects(),
                dispatch.getQueue().size(),
                transport.publisherConnected(),
                transport.subscriberConnected());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        subscriptions.clear();
        transport.close();
        dispatch.shutdownNow();
        awaitTermination(dispatch, "dispatch executor");
    }

    private void awaitTermination(ThreadPoolExecutor executor, String resource) {
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.log(Level.WARNING, "Relay {0} did not terminate cleanly", resource);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private final class RegisteredSubscription<T> implements Subscription {
        private final Topic<T> topic;
        private final MessageHandler<T> handler;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private RegisteredSubscription(Topic<T> topic, MessageHandler<T> handler) {
            this.topic = topic;
            this.handler = handler;
        }

        public boolean active() {
            return active.get();
        }

        public void close() {
            if (!active.compareAndSet(true, false)) return;
            CopyOnWriteArrayList<RegisteredSubscription<?>> listeners = subscriptions.get(topic.name());
            if (listeners != null) {
                listeners.remove(this);
                if (listeners.isEmpty()) subscriptions.remove(topic.name(), listeners);
            }
        }

        private void deliver(WireEnvelope envelope) {
            if (!active()) return;
            if (!topic.codec().contentType().equals(envelope.contentType())) {
                rejected.increment();
                logger.log(Level.WARNING, "Content type mismatch for Relay topic {0}", topic.name());
                return;
            }
            final T payload;
            try {
                payload = topic.codec().decode(envelope.payload());
            } catch (Exception exception) {
                rejected.increment();
                logger.log(
                        Level.WARNING,
                        "Relay codec failed for topic " + topic.name() + " (payload omitted)",
                        exception);
                return;
            }
            Message<T> message = new Message<>(
                    envelope.id(),
                    topic,
                    payload,
                    envelope.origin(),
                    envelope.destination(),
                    envelope.createdAt(),
                    envelope.correlationId(),
                    envelope.headers());
            try {
                handler.handle(message);
            } catch (Exception exception) {
                handlerFailures.increment();
                logger.log(
                        Level.WARNING,
                        "Relay handler failed for topic " + topic.name() + " (payload omitted)",
                        exception);
            }
        }
    }
}
