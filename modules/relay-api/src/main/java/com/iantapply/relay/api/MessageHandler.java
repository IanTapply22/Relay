package com.iantapply.relay.api;

/**
 * Runs on Relay's dispatch executor, never on Paper's server thread.
 *
 * @param <T> handled payload type
 */
@FunctionalInterface
public interface MessageHandler<T> {
    /**
     * Processes one decoded message.
     *
     * @param message received message
     * @throws Exception if processing fails; Relay records and logs the failure
     */
    void handle(Message<T> message) throws Exception;
}
