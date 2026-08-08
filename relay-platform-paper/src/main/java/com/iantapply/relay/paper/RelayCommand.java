package com.iantapply.relay.paper;

import com.iantapply.relay.api.MessagingStatus;
import com.iantapply.relay.api.MessagingService;
import com.iantapply.relay.core.DefaultMessagingService;
import com.iantapply.relay.core.RelayMetrics;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import org.bukkit.Bukkit;

public final class RelayCommand implements BasicCommand {
    @Override
    public void execute(CommandSourceStack source, String[] args) {
        MessagingService relay = Bukkit.getServicesManager().load(MessagingService.class);
        if (relay == null) {
            source.getSender().sendMessage("Relay is not available.");
            return;
        }
        String subcommand = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (subcommand) {
            case "status" -> {
                MessagingStatus status = relay.status();
                source.getSender().sendMessage("Relay: connected=" + status.connected()
                        + ", node=" + status.node()
                        + ", subscriptions=" + status.subscriptions()
                        + ", queued=" + status.queuedHandlers()
                        + ", handlers=" + status.activeHandlers() + "/" + status.maximumHandlers());
            }
            case "subscriptions" -> source.getSender().sendMessage("Relay subscriptions: " + relay.status().subscriptions());
            case "diagnostics" -> {
                if (!(relay instanceof DefaultMessagingService implementation)) {
                    source.getSender().sendMessage("Relay diagnostics are unavailable.");
                    return;
                }
                RelayMetrics metrics = implementation.metrics();
                source.getSender().sendMessage("Relay diagnostics: published=" + metrics.messagesPublished()
                        + ", received=" + metrics.messagesReceived()
                        + ", rejected=" + metrics.messagesRejected()
                        + ", handlerFailures=" + metrics.handlerFailures()
                        + ", reconnects=" + metrics.redisReconnects()
                        + ", queue=" + metrics.dispatchQueueSize());
            }
            default -> source.getSender().sendMessage("Usage: /relay <status|subscriptions|diagnostics>");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length > 1) return List.of();
        String prefix = args.length == 0 ? "" : args[0].toLowerCase();
        return List.of("status", "subscriptions", "diagnostics").stream().filter(value -> value.startsWith(prefix)).toList();
    }

    @Override public String permission() { return "relay.admin"; }
}
