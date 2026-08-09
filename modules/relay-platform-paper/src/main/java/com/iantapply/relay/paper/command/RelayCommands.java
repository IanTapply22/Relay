package com.iantapply.relay.paper.command;

import com.iantapply.relay.api.MessagingStatus;
import com.iantapply.relay.core.DefaultMessagingService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/** Small command tree that adapts Paper senders to Relay's operational services. */
public final class RelayCommands {
    /** Permission required for every Relay administration command. */
    public static final String PERMISSION = "relay.admin";

    private final DefaultMessagingService messaging;
    private final Supplier<List<String>> diagnostics;

    /**
     * Creates the command adapter.
     *
     * @param messaging active Relay messaging service
     * @param diagnostics safe platform diagnostic lines
     */
    public RelayCommands(DefaultMessagingService messaging, Supplier<List<String>> diagnostics) {
        this.messaging = messaging;
        this.diagnostics = diagnostics;
    }

    /**
     * Builds the complete {@code /relay} command tree.
     *
     * @return lifecycle-registerable command root
     */
    public LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("relay")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(context -> usage(context.getSource().getSender()))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("subscriptions").executes(this::subscriptions))
                .then(Commands.literal("diagnostics").executes(this::diagnostics))
                .build();
    }

    private int status(CommandContext<CommandSourceStack> context) {
        success(context, statusSummary(messaging.status()));
        return Command.SINGLE_SUCCESS;
    }

    private int subscriptions(CommandContext<CommandSourceStack> context) {
        success(context, messaging.status().subscriptions() + " active subscription(s)");
        return Command.SINGLE_SUCCESS;
    }

    private int diagnostics(CommandContext<CommandSourceStack> context) {
        success(context, "Relay diagnostics");
        diagnostics.get().forEach(line -> detail(context, line));
        detail(context, statusSummary(messaging.status()));
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /relay <status|subscriptions|diagnostics>", NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static String statusSummary(MessagingStatus status) {
        return "connected=%s, node=%s, subscriptions=%d, queued=%d, handlers=%d/%d"
                .formatted(
                        status.connected(),
                        status.node(),
                        status.subscriptions(),
                        status.queuedHandlers(),
                        status.activeHandlers(),
                        status.maximumHandlers());
    }

    private static void success(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().getSender().sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private static void detail(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().getSender().sendMessage(Component.text(message, NamedTextColor.GRAY));
    }
}
