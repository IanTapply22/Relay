package com.iantapply.relay.api;

/**
 * Point-in-time operational status of a messaging service.
 *
 * @param connected whether the backing transport is connected
 * @param node local node identifier
 * @param subscriptions number of active topic subscriptions
 * @param queuedHandlers handlers waiting for an executor thread
 * @param activeHandlers handlers currently executing
 * @param maximumHandlers maximum number of concurrently executing handlers
 */
public record MessagingStatus(
        boolean connected,
        String node,
        int subscriptions,
        int queuedHandlers,
        int activeHandlers,
        int maximumHandlers) {}
