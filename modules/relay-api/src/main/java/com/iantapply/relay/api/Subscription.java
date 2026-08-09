package com.iantapply.relay.api;

public interface Subscription extends AutoCloseable {
    boolean active();

    @Override
    void close();
}
