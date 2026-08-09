package com.iantapply.relay.api;

/**
 * Point-in-time operational status of a messaging service.
 *
 * @param publisherConnected whether the publication connection is healthy
 * @param subscriberConnected whether the subscription connection is healthy
 * @param node local node identifier
 * @param subscriptions number of active topic subscriptions
 * @param queuedHandlers handlers waiting for an executor thread
 * @param activeHandlers handlers currently executing
 * @param maximumHandlers maximum number of concurrently executing handlers
 */
public record MessagingStatus(
        boolean publisherConnected,
        boolean subscriberConnected,
        String node,
        int subscriptions,
        int queuedHandlers,
        int activeHandlers,
        int maximumHandlers) {
    /**
     * Creates a status for transports that expose only aggregate connectivity.
     *
     * @param connected whether both transport roles are connected
     * @param node local node identifier
     * @param subscriptions active topic subscriptions
     * @param queuedHandlers handlers waiting to run
     * @param activeHandlers handlers currently running
     * @param maximumHandlers maximum concurrent handlers
     */
    public MessagingStatus(
            boolean connected,
            String node,
            int subscriptions,
            int queuedHandlers,
            int activeHandlers,
            int maximumHandlers) {
        this(connected, connected, node, subscriptions, queuedHandlers, activeHandlers, maximumHandlers);
    }

    /**
     * Reports whether both sides of the transport are connected.
     *
     * @return {@code true} when publication and subscription are healthy
     */
    public boolean connected() {
        return publisherConnected && subscriberConnected;
    }
}
