package com.iantapply.relay.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.MessageId;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EnvelopeCodecTest {
    private final EnvelopeCodec codec = new EnvelopeCodec(64, Duration.ofSeconds(60));

    @Test
    void roundTripsEveryEnvelopeField() {
        MessageId id = MessageId.random();
        MessageId correlation = MessageId.random();
        Instant created = Instant.now();
        WireEnvelope original = new WireEnvelope(1, id, "party:updated", "lobby-1",
                Destination.node("survival-1"), created, "application/json", correlation,
                Map.of("trace", "abc"), new byte[]{1, 2, 3});

        WireEnvelope decoded = codec.decode(codec.encode(original), created.plusSeconds(1));

        assertEquals(id, decoded.id());
        assertEquals("party:updated", decoded.topic());
        assertEquals(Destination.node("survival-1"), decoded.destination());
        assertEquals(correlation, decoded.correlationId());
        assertEquals(Map.of("trace", "abc"), decoded.headers());
        assertArrayEquals(new byte[]{1, 2, 3}, decoded.payload());
    }

    @Test
    void rejectsUnknownSchemaAndOversizedPayload() {
        assertThrows(IllegalArgumentException.class, () -> codec.encode(envelope(2, new byte[1])));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(envelope(1, new byte[65])));
    }

    @Test
    void rejectsHeaderCountAndLengthViolations() {
        Map<String, String> tooMany = IntStream.range(0, 17).boxed()
                .collect(Collectors.toMap(index -> "key" + index, index -> "value"));
        WireEnvelope excessiveCount = new WireEnvelope(1, MessageId.random(), "valid:topic", "lobby-1",
                Destination.broadcast(), Instant.now(), "application/octet-stream", null, tooMany, new byte[0]);
        WireEnvelope excessiveKey = new WireEnvelope(1, MessageId.random(), "valid:topic", "lobby-1",
                Destination.broadcast(), Instant.now(), "application/octet-stream", null,
                Map.of("x".repeat(65), "value"), new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> codec.encode(excessiveCount));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(excessiveKey));
    }

    @Test
    void rejectsStaleMessages() {
        WireEnvelope stale = new WireEnvelope(1, MessageId.random(), "party:updated", "lobby-1",
                Destination.broadcast(), Instant.now().minusSeconds(61), "application/octet-stream", null,
                Map.of(), new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> codec.encode(stale));
    }

    @Test
    void rejectsInvalidEnvelopeIdentity() {
        WireEnvelope invalidTopic = new WireEnvelope(1, MessageId.random(), "UPPERCASE", "lobby-1",
                Destination.broadcast(), Instant.now(), "application/octet-stream", null, Map.of(), new byte[0]);
        WireEnvelope invalidOrigin = new WireEnvelope(1, MessageId.random(), "valid:topic", "bad origin",
                Destination.broadcast(), Instant.now(), "application/octet-stream", null, Map.of(), new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> codec.encode(invalidTopic));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(invalidOrigin));
    }

    private static WireEnvelope envelope(int schema, byte[] payload) {
        return new WireEnvelope(schema, MessageId.random(), "party:updated", "lobby-1",
                Destination.broadcast(), Instant.now(), "application/octet-stream", null, Map.of(), payload);
    }
}
