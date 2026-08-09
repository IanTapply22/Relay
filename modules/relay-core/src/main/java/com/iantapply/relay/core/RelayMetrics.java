package com.iantapply.relay.core;

/**
 * Cumulative messaging counters accompanied by current queue and Redis state.
 *
 * @param messagesPublished messages accepted by the transport
 * @param messagesReceived valid envelopes received from the transport
 * @param messagesRejected envelopes or dispatches rejected before handler completion
 * @param handlerFailures handlers that threw while processing a message
 * @param redisReconnects successful Redis subscriber reconnections
 * @param dispatchQueueSize handlers currently waiting to run
 * @param redisConnected whether the Redis subscriber is currently connected
 */
public record RelayMetrics(
        long messagesPublished,
        long messagesReceived,
        long messagesRejected,
        long handlerFailures,
        long redisReconnects,
        int dispatchQueueSize,
        boolean redisConnected) {}
