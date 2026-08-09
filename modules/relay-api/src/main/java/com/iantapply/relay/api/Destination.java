package com.iantapply.relay.api;

import java.util.Objects;
import java.util.regex.Pattern;

public record Destination(Kind kind, String nodeId) {
    public enum Kind {
        BROADCAST,
        PAPER,
        VELOCITY,
        NODE
    }

    private static final Pattern VALID_NODE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

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

    public static Destination broadcast() {
        return new Destination(Kind.BROADCAST, null);
    }

    public static Destination paperServers() {
        return new Destination(Kind.PAPER, null);
    }

    public static Destination velocityProxies() {
        return new Destination(Kind.VELOCITY, null);
    }

    public static Destination node(String id) {
        return new Destination(Kind.NODE, id);
    }

    public String wireName() {
        return switch (kind) {
            case BROADCAST -> "broadcast";
            case PAPER -> "paper";
            case VELOCITY -> "velocity";
            case NODE -> "node:" + nodeId;
        };
    }
}
