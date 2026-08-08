package com.iantapply.relay.api;

public record MessagingStatus(
        boolean connected,
        String node,
        int subscriptions,
        int queuedHandlers,
        int activeHandlers,
        int maximumHandlers) {}
