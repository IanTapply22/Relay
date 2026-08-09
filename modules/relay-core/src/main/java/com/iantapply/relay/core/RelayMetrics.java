package com.iantapply.relay.core;

/**
 * Cumulative messaging counters accompanied by current queue and Redis state.
 *
 * @param messagesPublished messages accepted by the transport
 * @param messagesReceived valid envelopes received from the transport
 * @param messagesRejected invalid envelopes or payloads rejected before handler completion
 * @param dispatchQueueDrops handler invocations dropped because the dispatch queue was full
 * @param handlerFailures handlers that threw while processing a message
 * @param redisReconnects successful Redis subscriber reconnections
 * @param dispatchQueueSize handlers currently waiting to run
 * @param publisherConnected whether the publication connection is healthy
 * @param subscriberConnected whether the subscription connection is healthy
 */
public record RelayMetrics(
        long messagesPublished,
        long messagesReceived,
        long messagesRejected,
        long dispatchQueueDrops,
        long handlerFailures,
        long redisReconnects,
        int dispatchQueueSize,
        boolean publisherConnected,
        boolean subscriberConnected) {
    /**
     * Reports whether both Redis connection roles are healthy.
     *
     * @return whether both Redis connection roles are healthy
     */
    public boolean redisConnected() {
        return publisherConnected && subscriberConnected;
    }
}
