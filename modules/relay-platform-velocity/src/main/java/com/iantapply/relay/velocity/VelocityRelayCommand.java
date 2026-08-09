package com.iantapply.relay.velocity;

import com.iantapply.relay.api.MessagingStatus;
import com.iantapply.relay.core.DefaultMessagingService;
import com.iantapply.relay.core.RelayConfig;
import com.iantapply.relay.core.RelayMetrics;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class VelocityRelayCommand implements SimpleCommand {
    private final DefaultMessagingService relay;
    private final RelayConfig config;

    VelocityRelayCommand(DefaultMessagingService relay, RelayConfig config) {
        this.relay = relay;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        String subcommand = invocation.arguments().length == 0 ? "status" : invocation.arguments()[0].toLowerCase();
        String response =
                switch (subcommand) {
                    case "status" -> {
                        MessagingStatus status = relay.status();
                        yield "Relay: connected=" + status.connected() + ", node=" + status.node()
                                + ", subscriptions=" + status.subscriptions() + ", queued=" + status.queuedHandlers()
                                + ", handlers=" + status.activeHandlers() + "/" + status.maximumHandlers();
                    }
                    case "subscriptions" ->
                        "Relay subscriptions: " + relay.status().subscriptions();
                    case "diagnostics" -> {
                        RelayMetrics metrics = relay.metrics();
                        yield "Relay diagnostics: role=" + config.role().channelName() + ", namespace="
                                + config.namespace()
                                + ", published=" + metrics.messagesPublished() + ", received="
                                + metrics.messagesReceived()
                                + ", rejected=" + metrics.messagesRejected() + ", queueDrops="
                                + metrics.dispatchQueueDrops() + ", handlerFailures="
                                + metrics.handlerFailures()
                                + ", reconnects=" + metrics.redisReconnects() + ", queue="
                                + metrics.dispatchQueueSize() + ", publisherConnected="
                                + metrics.publisherConnected() + ", subscriberConnected="
                                + metrics.subscriberConnected();
                    }
                    default -> "Usage: /relay <status|subscriptions|diagnostics>";
                };
        invocation.source().sendRichMessage(response);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("relay.admin");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        if (invocation.arguments().length > 1) return CompletableFuture.completedFuture(List.of());
        String prefix = invocation.arguments().length == 0 ? "" : invocation.arguments()[0].toLowerCase();
        return CompletableFuture.completedFuture(List.of("status", "subscriptions", "diagnostics").stream()
                .filter(value -> value.startsWith(prefix))
                .toList());
    }
}
