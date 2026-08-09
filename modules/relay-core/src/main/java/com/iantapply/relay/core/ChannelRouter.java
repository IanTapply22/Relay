package com.iantapply.relay.core;

import com.iantapply.relay.api.Destination;
import java.util.List;

/** Maps logical destinations and node roles to namespaced transport channels. */
public final class ChannelRouter {
    private final String prefix;

    /**
     * Creates a router for one Redis namespace.
     *
     * @param namespace namespace shared by communicating Relay nodes
     */
    public ChannelRouter(String namespace) {
        this.prefix = "relay:" + namespace + ":";
    }

    /**
     * Resolves the publication channel for a destination.
     *
     * @param destination logical destination
     * @return transport channel name
     */
    public String channel(Destination destination) {
        return switch (destination.kind()) {
            case BROADCAST -> prefix + "broadcast";
            case PAPER -> prefix + "paper";
            case VELOCITY -> prefix + "velocity";
            case NODE -> prefix + "node:" + destination.nodeId();
        };
    }

    /**
     * Returns the channels a node must consume for broadcasts, its platform, and its node ID.
     *
     * @param config local node configuration
     * @return channel names to subscribe to
     */
    public List<String> subscriptions(RelayConfig config) {
        return List.of(prefix + "broadcast", prefix + config.role().channelName(), prefix + "node:" + config.nodeId());
    }
}
