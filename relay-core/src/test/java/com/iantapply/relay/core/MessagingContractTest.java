package com.iantapply.relay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.iantapply.relay.api.Codecs;
import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.MessageCodec;
import com.iantapply.relay.api.Subscription;
import com.iantapply.relay.api.Topic;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagingContractTest {
    private DefaultMessagingService publisher;
    private DefaultMessagingService receiver;

    @AfterEach
    void closeServices() {
        if (publisher != null) publisher.close();
        if (receiver != null) receiver.close();
    }

    @Test
    void deliversPaperToVelocityAndDirectedMessages() throws Exception {
        publisher = service("paper-1", RelayConfig.NodeRole.PAPER);
        receiver = service("proxy-1", RelayConfig.NodeRole.VELOCITY);
        Topic<String> topic = Topic.of("network:notice", Codecs.utf8());
        CountDownLatch received = new CountDownLatch(2);
        receiver.subscribe(topic, message -> received.countDown());

        publisher.publish(topic, Destination.velocityProxies(), "role").toCompletableFuture().join();
        publisher.publish(topic, Destination.node("proxy-1"), "node").toCompletableFuture().join();

        assertTrue(received.await(2, TimeUnit.SECONDS));
        assertEquals(2, receiver.metrics().messagesReceived());
    }

    @Test
    void isolatesFailingSubscribersAndSupportsUnsubscription() throws Exception {
        publisher = service("paper-1", RelayConfig.NodeRole.PAPER);
        receiver = service("paper-2", RelayConfig.NodeRole.PAPER);
        Topic<String> topic = Topic.of("party:updated", Codecs.utf8());
        CountDownLatch healthy = new CountDownLatch(1);
        receiver.subscribe(topic, message -> { throw new IllegalStateException("consumer failure"); });
        Subscription removed = receiver.subscribe(topic, message -> { throw new AssertionError("closed subscription invoked"); });
        removed.close();
        receiver.subscribe(topic, message -> healthy.countDown());

        publisher.publish(topic, Destination.paperServers(), "changed").toCompletableFuture().join();

        assertTrue(healthy.await(2, TimeUnit.SECONDS));
        awaitMetric(() -> receiver.metrics().handlerFailures() == 1);
        assertFalse(removed.active());
        assertEquals(1, receiver.metrics().handlerFailures());
    }

    @Test
    void reportsDisconnectedPublish() {
        InMemoryTransport transport = new InMemoryTransport();
        transport.close();
        assertTrue(transport.publish("channel", new byte[0]).toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void boundsTheDispatchQueue() throws Exception {
        publisher = service("paper-1", RelayConfig.NodeRole.PAPER);
        RelayConfig constrained = new RelayConfig("paper-2", RelayConfig.NodeRole.PAPER,
                URI.create("redis://localhost:6379"), "production", 65_536, 1, 1, Duration.ofSeconds(60));
        receiver = new DefaultMessagingService(constrained, new InMemoryTransport(), Logger.getAnonymousLogger());
        Topic<String> topic = Topic.of("queue:test", Codecs.utf8());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        receiver.subscribe(topic, message -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
        });

        publisher.publish(topic, Destination.paperServers(), "one").toCompletableFuture().join();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        publisher.publish(topic, Destination.paperServers(), "two").toCompletableFuture().join();
        publisher.publish(topic, Destination.paperServers(), "three").toCompletableFuture().join();

        assertEquals(1, receiver.metrics().messagesRejected());
        assertEquals(1, receiver.metrics().dispatchQueueSize());
        release.countDown();
    }

    @Test
    void containsCodecFailures() throws Exception {
        publisher = service("paper-1", RelayConfig.NodeRole.PAPER);
        receiver = service("paper-2", RelayConfig.NodeRole.PAPER);
        Topic<String> publisherTopic = Topic.of("codec:test", Codecs.utf8());
        Topic<String> receiverTopic = Topic.of("codec:test", new MessageCodec<>() {
            @Override public byte[] encode(String value) { return value.getBytes(); }
            @Override public String decode(byte[] payload) { throw new IllegalArgumentException("bad payload"); }
            @Override public String contentType() { return "text/plain; charset=utf-8"; }
        });
        receiver.subscribe(receiverTopic, message -> { throw new AssertionError("handler must not run"); });

        publisher.publish(publisherTopic, Destination.paperServers(), "bad").toCompletableFuture().join();

        awaitMetric(() -> receiver.metrics().messagesRejected() == 1);
        assertEquals(1, receiver.metrics().messagesRejected());
        assertEquals(0, receiver.metrics().handlerFailures());
    }

    private static DefaultMessagingService service(String node, RelayConfig.NodeRole role) {
        return new DefaultMessagingService(ValidationAndRoutingTest.config(node, role), new InMemoryTransport(), Logger.getAnonymousLogger());
    }

    private static void awaitMetric(Check check) throws InterruptedException {
        for (int attempt = 0; attempt < 100 && !check.done(); attempt++) Thread.sleep(10);
    }

    @FunctionalInterface private interface Check { boolean done(); }
}
