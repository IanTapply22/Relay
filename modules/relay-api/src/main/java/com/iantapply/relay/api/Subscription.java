package com.iantapply.relay.api;

/** A removable topic subscription. */
public interface Subscription extends AutoCloseable {
    /**
     * Reports whether this subscription can still receive messages.
     *
     * @return {@code true} while active
     */
    boolean active();

    /** Removes the handler; repeated calls have no effect. */
    @Override
    void close();
}
