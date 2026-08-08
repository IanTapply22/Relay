package com.iantapply.relay.core;

import com.iantapply.relay.api.Destination;
import java.util.List;

public final class ChannelRouter {
    private final String prefix;

    public ChannelRouter(String namespace) {
        this.prefix = "relay:" + namespace + ":";
    }

    public String channel(Destination destination) {
        return switch (destination.kind()) {
            case BROADCAST -> prefix + "broadcast";
            case PAPER -> prefix + "paper";
            case VELOCITY -> prefix + "velocity";
            case NODE -> prefix + "node:" + destination.nodeId();
        };
    }

    public List<String> subscriptions(RelayConfig config) {
        return List.of(
                prefix + "broadcast",
                prefix + config.role().channelName(),
                prefix + "node:" + config.nodeId());
    }
}
