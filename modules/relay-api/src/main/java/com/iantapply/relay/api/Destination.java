package com.iantapply.relay.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Selects the Relay nodes that should receive a message.
 *
 * @param kind destination category
 * @param nodeId target node identifier for {@link Kind#NODE}, otherwise {@code null}
 */
public record Destination(Kind kind, String nodeId) {
    /** Categories of message destination. */
    public enum Kind {
        /** Every Relay node. */
        BROADCAST,
        /** Every Paper server. */
        PAPER,
        /** Every Velocity proxy. */
        VELOCITY,
        /** One specifically identified node. */
        NODE
    }

    private static final Pattern VALID_NODE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * Validates a destination's category and optional node identifier.
     *
     * @param kind destination category
     * @param nodeId target node identifier for node destinations
     */
    public Destination {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.NODE) {
            if (nodeId == null || !VALID_NODE.matcher(nodeId).matches()) {
                throw new IllegalArgumentException("Invalid node id: " + nodeId);
            }
        } else if (nodeId != null) {
            throw new IllegalArgumentException("Only NODE destinations may have a node id");
        }
    }

    /**
     * Creates a destination containing every Relay node.
     *
     * @return broadcast destination
     */
    public static Destination broadcast() {
        return new Destination(Kind.BROADCAST, null);
    }

    /**
     * Creates a destination containing every Paper server.
     *
     * @return Paper destination
     */
    public static Destination paperServers() {
        return new Destination(Kind.PAPER, null);
    }

    /**
     * Creates a destination containing every Velocity proxy.
     *
     * @return Velocity destination
     */
    public static Destination velocityProxies() {
        return new Destination(Kind.VELOCITY, null);
    }

    /**
     * Creates a destination for one Relay node.
     *
     * @param id node identifier
     * @return node destination
     * @throws IllegalArgumentException if the identifier is invalid
     */
    public static Destination node(String id) {
        return new Destination(Kind.NODE, id);
    }

    /**
     * Returns the stable representation used in Relay envelopes.
     *
     * @return wire destination name
     */
    public String wireName() {
        return switch (kind) {
            case BROADCAST -> "broadcast";
            case PAPER -> "paper";
            case VELOCITY -> "velocity";
            case NODE -> "node:" + nodeId;
        };
    }
}
