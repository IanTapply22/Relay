package com.iantapply.relay.velocity;

import com.google.inject.Inject;
import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.MessageHandler;
import com.iantapply.relay.api.MessageId;
import com.iantapply.relay.api.MessagingService;
import com.iantapply.relay.api.MessagingStatus;
import com.iantapply.relay.api.Subscription;
import com.iantapply.relay.api.Topic;
import com.iantapply.relay.core.DefaultMessagingService;
import com.iantapply.relay.core.RelayConfig;
import com.iantapply.relay.redis.RedisPubSubTransport;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/** Velocity entry point and platform-facing {@link MessagingService} implementation. */
@Plugin(
        id = "relay",
        name = "Relay",
        version = "1.0.0",
        authors = {"Gucci Fox"},
        description = "Transient Redis messaging for Paper and Velocity")
public final class RelayVelocity implements MessagingService {
    private final ProxyServer proxy;
    private final org.slf4j.Logger logger;
    private final Path dataDirectory;
    private volatile DefaultMessagingService messaging;

    /**
     * Creates the Velocity plugin instance through dependency injection.
     *
     * @param proxy active Velocity proxy
     * @param logger platform logger
     * @param dataDirectory plugin data directory
     */
    @Inject
    public RelayVelocity(ProxyServer proxy, org.slf4j.Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * Starts Relay after Velocity initializes its plugins.
     *
     * @param event initialization event
     */
    @Subscribe
    public void initialize(ProxyInitializeEvent event) {
        try {
            RelayConfig config = VelocityConfigLoader.load(dataDirectory);
            if (config.role() != RelayConfig.NodeRole.VELOCITY) {
                throw new IllegalArgumentException("Velocity configuration must use node.role: velocity");
            }
            Logger coreLogger = Logger.getLogger("Relay-Velocity");
            messaging = new DefaultMessagingService(
                    config, new RedisPubSubTransport(config.redisUri(), coreLogger), coreLogger);
            proxy.getCommandManager()
                    .register(
                            proxy.getCommandManager()
                                    .metaBuilder("relay")
                                    .plugin(this)
                                    .build(),
                            new VelocityRelayCommand(messaging, config));
            logger.info("Relay enabled for node {} (transient, at-most-once delivery)", config.nodeId());
        } catch (Exception exception) {
            logger.error("Relay could not start", exception);
        }
    }

    /**
     * Releases Relay resources during proxy shutdown.
     *
     * @param event shutdown event
     */
    @Subscribe
    public void shutdown(ProxyShutdownEvent event) {
        DefaultMessagingService current = messaging;
        if (current != null) current.close();
    }

    private DefaultMessagingService service() {
        DefaultMessagingService current = messaging;
        if (current == null) throw new IllegalStateException("Relay has not initialized");
        return current;
    }

    @Override
    public <T> CompletionStage<MessageId> publish(Topic<T> topic, Destination destination, T payload) {
        return service().publish(topic, destination, payload);
    }

    @Override
    public <T> Subscription subscribe(Topic<T> topic, MessageHandler<T> handler) {
        return service().subscribe(topic, handler);
    }

    @Override
    public MessagingStatus status() {
        return service().status();
    }
}
