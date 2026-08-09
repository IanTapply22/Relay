package com.iantapply.relay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.iantapply.relay.api.Codecs;
import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.Topic;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ValidationAndRoutingTest {
    @Test
    void validatesTopicsAndNodes() {
        assertEquals("party:updated", Topic.of("party:updated", Codecs.utf8()).name());
        assertEquals("a", Topic.of("a", Codecs.utf8()).name());
        assertThrows(IllegalArgumentException.class, () -> Topic.of("Uppercase", Codecs.utf8()));
        assertThrows(IllegalArgumentException.class, () -> Destination.node("contains spaces"));
        assertThrows(IllegalArgumentException.class, () -> config("bad node!", RelayConfig.NodeRole.PAPER));
    }

    @Test
    void rejectsEnvelopePublishedOnTheWrongChannel() {
        InMemoryTransport receivingTransport = new InMemoryTransport();
        DefaultMessagingService receiver = new DefaultMessagingService(
                config("paper-1", RelayConfig.NodeRole.PAPER),
                receivingTransport,
                java.util.logging.Logger.getAnonymousLogger());
        InMemoryTransport rawPublisher = new InMemoryTransport();
        rawPublisher.start(List.of(), (channel, payload) -> {});
        AtomicBoolean delivered = new AtomicBoolean();
        receiver.subscribe(Topic.of("route:test", Codecs.utf8()), message -> delivered.set(true));
        WireEnvelope envelope = new WireEnvelope(
                WireEnvelope.CURRENT_SCHEMA,
                com.iantapply.relay.api.MessageId.random(),
                "route:test",
                "source-1",
                Destination.node("different-node"),
                Instant.now(),
                Codecs.utf8().contentType(),
                null,
                Map.of(),
                "payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        rawPublisher
                .publish(
                        "relay:production:broadcast",
                        new EnvelopeCodec(65_536, Duration.ofSeconds(60)).encode(envelope))
                .toCompletableFuture()
                .join();

        assertEquals(1, receiver.metrics().messagesRejected());
        assertEquals(false, delivered.get());
        rawPublisher.close();
        receiver.close();
    }

    @Test
    void mapsDestinationsAndRelevantSubscriptions() {
        ChannelRouter router = new ChannelRouter("production");
        assertEquals("relay:production:broadcast", router.channel(Destination.broadcast()));
        assertEquals("relay:production:paper", router.channel(Destination.paperServers()));
        assertEquals("relay:production:velocity", router.channel(Destination.velocityProxies()));
        assertEquals("relay:production:node:survival-1", router.channel(Destination.node("survival-1")));
        assertEquals(
                List.of("relay:production:broadcast", "relay:production:paper", "relay:production:node:lobby-1"),
                router.subscriptions(config("lobby-1", RelayConfig.NodeRole.PAPER)));
    }

    static RelayConfig config(String node, RelayConfig.NodeRole role) {
        return new RelayConfig(
                node, role, URI.create("redis://localhost:6379"), "production", 65_536, 2, 32, Duration.ofSeconds(60));
    }
}
