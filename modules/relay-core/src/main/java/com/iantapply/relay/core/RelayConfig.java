package com.iantapply.relay.core;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validated runtime configuration shared by Relay's platform adapters and core.
 *
 * @param nodeId unique identifier for this server or proxy
 * @param role platform role of this node
 * @param redisUri Redis connection URI using {@code redis} or {@code rediss}
 * @param namespace logical Redis channel namespace
 * @param maximumPayloadBytes largest accepted decoded payload
 * @param dispatchWorkers number of concurrent handler workers
 * @param dispatchQueueCapacity maximum number of queued handler invocations
 * @param maximumMessageAge maximum accepted message age, or zero to disable the check
 */
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

    /** Platform roles understood by Relay routing. */
    public enum NodeRole {
        /** Paper game server. */
        PAPER,
        /** Velocity proxy. */
        VELOCITY;

        /**
         * Parses a case-insensitive role name.
         *
         * @param value role name
         * @return parsed role
         * @throws IllegalArgumentException if the name is unknown
         */
        public static NodeRole parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        /**
         * Returns the lower-case name used in transport channels.
         *
         * @return channel role name
         */
        public String channelName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Validates all runtime settings.
     *
     * @param nodeId unique identifier for this server or proxy
     * @param role platform role of this node
     * @param redisUri Redis connection URI
     * @param namespace logical Redis channel namespace
     * @param maximumPayloadBytes largest accepted decoded payload
     * @param dispatchWorkers number of concurrent handler workers
     * @param dispatchQueueCapacity maximum queued handler invocations
     * @param maximumMessageAge maximum accepted message age
     */
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
