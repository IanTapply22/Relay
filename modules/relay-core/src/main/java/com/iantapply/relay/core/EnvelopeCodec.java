package com.iantapply.relay.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.MessageId;
import com.iantapply.relay.api.Topic;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Encodes, decodes, and validates Relay's JSON wire envelope. */
public final class EnvelopeCodec {
    /** Maximum number of application headers accepted in one envelope. */
    public static final int MAXIMUM_HEADERS = 16;
    /** Maximum length of an application header name. */
    public static final int MAXIMUM_HEADER_KEY_LENGTH = 64;
    /** Maximum length of an application header value. */
    public static final int MAXIMUM_HEADER_VALUE_LENGTH = 256;

    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final int maximumPayloadBytes;
    private final Duration maximumAge;

    /**
     * Creates an envelope codec with inbound and outbound limits.
     *
     * @param maximumPayloadBytes largest accepted decoded payload
     * @param maximumAge maximum accepted message age, or zero to disable age checks
     */
    public EnvelopeCodec(int maximumPayloadBytes, Duration maximumAge) {
        this.maximumPayloadBytes = maximumPayloadBytes;
        this.maximumAge = maximumAge;
    }

    /**
     * Validates and serializes an envelope as UTF-8 JSON.
     *
     * @param envelope envelope to encode
     * @return encoded wire payload
     * @throws IllegalArgumentException if the envelope violates the wire contract
     */
    public byte[] encode(WireEnvelope envelope) {
        validate(envelope, Instant.now());
        JsonObject root = new JsonObject();
        root.addProperty("schema", envelope.schema());
        root.addProperty("id", envelope.id().toString());
        root.addProperty("topic", envelope.topic());
        root.addProperty("origin", envelope.origin());
        root.addProperty("destination", envelope.destination().wireName());
        root.addProperty("createdAt", envelope.createdAt().toString());
        root.addProperty("contentType", envelope.contentType());
        if (envelope.correlationId() == null) root.add("correlationId", null);
        else root.addProperty("correlationId", envelope.correlationId().toString());
        JsonObject headers = new JsonObject();
        envelope.headers().forEach(headers::addProperty);
        root.add("headers", headers);
        root.addProperty("payload", Base64.getEncoder().encodeToString(envelope.payload()));
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deserializes and validates an envelope relative to the supplied clock value.
     *
     * @param encoded UTF-8 JSON envelope
     * @param now reference time used for age validation
     * @return decoded envelope
     * @throws IllegalArgumentException if the input is malformed or violates the wire contract
     */
    public WireEnvelope decode(byte[] encoded, Instant now) {
        try {
            JsonObject root = JsonParser.parseString(new String(encoded, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            int schema = required(root, "schema").getAsInt();
            MessageId id = MessageId.parse(required(root, "id").getAsString());
            String topic = required(root, "topic").getAsString();
            String origin = required(root, "origin").getAsString();
            Destination destination =
                    parseDestination(required(root, "destination").getAsString());
            Instant createdAt = Instant.parse(required(root, "createdAt").getAsString());
            String contentType = required(root, "contentType").getAsString();
            JsonElement correlation = root.get("correlationId");
            MessageId correlationId =
                    correlation == null || correlation.isJsonNull() ? null : MessageId.parse(correlation.getAsString());
            Map<String, String> headers = new LinkedHashMap<>();
            JsonObject jsonHeaders = required(root, "headers").getAsJsonObject();
            jsonHeaders
                    .entrySet()
                    .forEach(entry ->
                            headers.put(entry.getKey(), entry.getValue().getAsString()));
            byte[] payload =
                    Base64.getDecoder().decode(required(root, "payload").getAsString());
            WireEnvelope envelope = new WireEnvelope(
                    schema, id, topic, origin, destination, createdAt, contentType, correlationId, headers, payload);
            validate(envelope, now);
            return envelope;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Malformed Relay envelope", exception);
        }
    }

    private static JsonElement required(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing envelope field: " + name);
        return value;
    }

    private void validate(WireEnvelope envelope, Instant now) {
        if (envelope.schema() != WireEnvelope.CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported envelope schema: " + envelope.schema());
        }
        if (!Topic.isValidName(envelope.topic())) throw new IllegalArgumentException("Invalid envelope topic");
        if (envelope.origin() == null || !NODE_ID.matcher(envelope.origin()).matches()) {
            throw new IllegalArgumentException("Invalid envelope origin");
        }
        if (envelope.contentType() == null
                || envelope.contentType().isBlank()
                || envelope.contentType().length() > 128) {
            throw new IllegalArgumentException("Invalid content type");
        }
        if (envelope.payload().length > maximumPayloadBytes)
            throw new IllegalArgumentException("Payload exceeds configured limit");
        if (envelope.headers().size() > MAXIMUM_HEADERS) throw new IllegalArgumentException("Too many headers");
        envelope.headers().forEach((key, value) -> {
            if (key.isEmpty() || key.length() > MAXIMUM_HEADER_KEY_LENGTH)
                throw new IllegalArgumentException("Invalid header key length");
            if (value.length() > MAXIMUM_HEADER_VALUE_LENGTH)
                throw new IllegalArgumentException("Invalid header value length");
        });
        if (!maximumAge.isZero() && envelope.createdAt().isBefore(now.minus(maximumAge))) {
            throw new IllegalArgumentException("Stale message");
        }
        if (envelope.createdAt().isAfter(now.plusSeconds(30)))
            throw new IllegalArgumentException("Message timestamp is in the future");
    }

    private static Destination parseDestination(String value) {
        return switch (value) {
            case "broadcast" -> Destination.broadcast();
            case "paper" -> Destination.paperServers();
            case "velocity" -> Destination.velocityProxies();
            default -> parseNodeDestination(value);
        };
    }

    private static Destination parseNodeDestination(String value) {
        if (value.startsWith("node:")) return Destination.node(value.substring(5));
        throw new IllegalArgumentException("Unknown destination: " + value);
    }
}
