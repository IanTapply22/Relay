package com.iantapply.relay.core;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record RelayConfig(
        String nodeId,
        NodeRole role,
        URI redisUri,
        String namespace,
        int maximumPayloadBytes,
        int dispatchWorkers,
        int dispatchQueueCapacity,
        Duration maximumMessageAge) {
    private static final Pattern NODE = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public enum NodeRole {
        PAPER,
        VELOCITY;

        public static NodeRole parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        public String channelName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public RelayConfig {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(redisUri, "redisUri");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(maximumMessageAge, "maximumMessageAge");
        if (!NODE.matcher(nodeId).matches()) throw new IllegalArgumentException("Invalid node id: " + nodeId);
        if (!NAMESPACE.matcher(namespace).matches())
            throw new IllegalArgumentException("Invalid Redis namespace: " + namespace);
        if (!"redis".equals(redisUri.getScheme()) && !"rediss".equals(redisUri.getScheme())) {
            throw new IllegalArgumentException("Redis URI must use redis:// or rediss://");
        }
        if (maximumPayloadBytes < 1) throw new IllegalArgumentException("maximumPayloadBytes must be positive");
        if (dispatchWorkers < 1) throw new IllegalArgumentException("dispatchWorkers must be positive");
        if (dispatchQueueCapacity < 1) throw new IllegalArgumentException("dispatchQueueCapacity must be positive");
        if (maximumMessageAge.isNegative())
            throw new IllegalArgumentException("maximumMessageAge must not be negative");
    }
}
