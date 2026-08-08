package com.iantapply.relay;

import com.iantapply.relay.api.MessagingService;
import com.iantapply.relay.core.DefaultMessagingService;
import com.iantapply.relay.core.RelayConfig;
import com.iantapply.relay.paper.PaperConfigLoader;
import com.iantapply.relay.redis.RedisPubSubTransport;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class Relay extends JavaPlugin {
    private DefaultMessagingService messaging;

    @Override
    public void onEnable() {
        try {
            RelayConfig config = PaperConfigLoader.load(this);
            RedisPubSubTransport transport = new RedisPubSubTransport(config.redisUri(), getLogger());
            messaging = new DefaultMessagingService(config, transport, getLogger());
            getServer().getServicesManager().register(MessagingService.class, messaging, this, ServicePriority.Normal);
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
}
