package com.iantapply.relay;

import com.iantapply.relay.api.MessagingService;
import com.iantapply.relay.core.DefaultMessagingService;
import com.iantapply.relay.core.RelayConfig;
import com.iantapply.relay.core.RelayMetrics;
import com.iantapply.relay.paper.PaperConfigLoader;
import com.iantapply.relay.paper.command.RelayCommands;
import com.iantapply.relay.redis.RedisPubSubTransport;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper entry point that exposes Relay through Bukkit's service registry. */
public final class Relay extends JavaPlugin {
    private DefaultMessagingService messaging;

    /** Creates the Paper plugin entry point. */
    public Relay() {}

    @Override
    public void onEnable() {
        try {
            RelayConfig config = PaperConfigLoader.load(this);
            RedisPubSubTransport transport = new RedisPubSubTransport(config.redisUri(), getLogger());
            messaging = new DefaultMessagingService(config, transport, getLogger());
            getServer().getServicesManager().register(MessagingService.class, messaging, this, ServicePriority.Normal);
            registerCommands(messaging, config);
            getLogger().info("Relay enabled for node " + config.nodeId() + " (transient, at-most-once delivery)");
        } catch (Exception exception) {
            getLogger().severe("Relay could not start: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (messaging != null) messaging.close();
    }

    private void registerCommands(DefaultMessagingService service, RelayConfig config) {
        RelayCommands commands = new RelayCommands(service, () -> diagnostics(service, config));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register(commands.create(), "Administer Relay messaging"));
    }

    private static List<String> diagnostics(DefaultMessagingService service, RelayConfig config) {
        RelayMetrics metrics = service.metrics();
        return List.of(
                "role=" + config.role().channelName() + ", namespace=" + config.namespace(),
                "published=" + metrics.messagesPublished() + ", received=" + metrics.messagesReceived(),
                "rejected=" + metrics.messagesRejected() + ", handlerFailures=" + metrics.handlerFailures(),
                "reconnects=" + metrics.redisReconnects() + ", queue=" + metrics.dispatchQueueSize(),
                "redisConnected=" + metrics.redisConnected());
    }
}
