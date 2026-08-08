package com.iantapply.relay.api;

/** Runs on Relay's dispatch executor, never on Paper's server thread. */
@FunctionalInterface
public interface MessageHandler<T> {
    void handle(Message<T> message) throws Exception;
}
