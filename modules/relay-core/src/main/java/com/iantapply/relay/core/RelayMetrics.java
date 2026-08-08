package com.iantapply.relay.core;

public record RelayMetrics(
        long messagesPublished,
        long messagesReceived,
        long messagesRejected,
        long handlerFailures,
        long redisReconnects,
        int dispatchQueueSize,
        boolean redisConnected) {}
