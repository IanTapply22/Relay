package com.iantapply.relay.redis;

import com.iantapply.relay.core.MessageTransport;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocketFactory;

/** Minimal RESP2 client dedicated to Relay's PUBLISH/SUBSCRIBE use case. */
public final class RedisPubSubTransport implements MessageTransport {
    private final RedisEndpoint endpoint;
    private final Logger logger;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicLong reconnects = new AtomicLong();
    private final ExecutorService publisher;
    private volatile RespConnection publishConnection;
    private volatile RespConnection subscribeConnection;
    private volatile Thread subscriberThread;

    /**
     * Creates a Redis transport for one endpoint.
     *
     * @param uri Redis endpoint, credentials, database, and optional TLS scheme
     * @param logger destination for connection warnings
     * @throws IllegalArgumentException if the URI is not a valid Redis endpoint
     */
    public RedisPubSubTransport(URI uri, Logger logger) {
        this.endpoint = RedisEndpoint.parse(uri);
        this.logger = Objects.requireNonNull(logger, "logger");
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "relay-redis-publisher");
            thread.setDaemon(true);
            return thread;
        };
        this.publisher = Executors.newSingleThreadExecutor(factory);
    }

    @Override
    public CompletionStage<Void> publish(String channel, byte[] payload) {
        if (!open.get()) return CompletableFuture.failedFuture(new IllegalStateException("Redis transport is closed"));
        CompletableFuture<Void> result = new CompletableFuture<>();
        publisher.execute(() -> {
            try {
                RespConnection connection = publishConnection;
                if (connection == null || connection.closed()) {
                    connection = connect();
                    publishConnection = connection;
                }
                Object response = connection.command(bytes("PUBLISH"), bytes(channel), payload);
                if (!(response instanceof Long)) throw new IOException("Unexpected PUBLISH response");
                result.complete(null);
            } catch (Exception exception) {
                closeQuietly(publishConnection);
                publishConnection = null;
                result.completeExceptionally(new IOException("Redis did not accept the message", exception));
            }
        });
        return result;
    }

    @Override
    public void start(Collection<String> channels, BiConsumer<String, byte[]> receiver) {
        if (subscriberThread != null) throw new IllegalStateException("Redis transport already started");
        List<String> subscribedChannels = List.copyOf(channels);
        subscriberThread = new Thread(() -> subscribeLoop(subscribedChannels, receiver), "relay-redis-subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void subscribeLoop(List<String> channels, BiConsumer<String, byte[]> receiver) {
        boolean connectedBefore = false;
        long delayMillis = 250;
        while (open.get()) {
            try {
                RespConnection connection = connect();
                subscribeConnection = connection;
                List<byte[]> command = new ArrayList<>();
                command.add(bytes("SUBSCRIBE"));
                channels.forEach(channel -> command.add(bytes(channel)));
                connection.send(command.toArray(byte[][]::new));
                for (int index = 0; index < channels.size(); index++) connection.read();
                if (connectedBefore) reconnects.incrementAndGet();
                connectedBefore = true;
                connected.set(true);
                delayMillis = 250;
                while (open.get()) {
                    Object response = connection.read();
                    if (!(response instanceof List<?> values) || values.size() < 3) continue;
                    String kind = text(values.get(0));
                    if ("message".equals(kind)) receiver.accept(text(values.get(1)), binary(values.get(2)));
                }
            } catch (Exception exception) {
                if (open.get())
                    logger.log(
                            Level.WARNING,
                            "Redis subscription disconnected; reconnecting: {0}",
                            exception.getMessage());
            } finally {
                connected.set(false);
                closeQuietly(subscribeConnection);
                subscribeConnection = null;
            }
            if (!open.get()) break;
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            delayMillis = Math.min(delayMillis * 2, 10_000);
        }
    }

    private RespConnection connect() throws IOException {
        Socket socket = endpoint.tls() ? SSLSocketFactory.getDefault().createSocket() : new Socket();
        socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 3_000);
        socket.setKeepAlive(true);
        RespConnection connection = new RespConnection(socket);
        try {
            if (endpoint.password() != null) {
                Object response = endpoint.username() == null
                        ? connection.command(bytes("AUTH"), bytes(endpoint.password()))
                        : connection.command(bytes("AUTH"), bytes(endpoint.username()), bytes(endpoint.password()));
                requireOk(response, "AUTH");
            }
            if (endpoint.database() != 0)
                requireOk(connection.command(bytes("SELECT"), bytes(Integer.toString(endpoint.database()))), "SELECT");
            return connection;
        } catch (IOException exception) {
            connection.close();
            throw exception;
        }
    }

    private static void requireOk(Object response, String command) throws IOException {
        if (!"OK".equals(response)) throw new IOException(command + " failed");
    }

    @Override
    public boolean connected() {
        return connected.get();
    }

    @Override
    public long reconnects() {
        return reconnects.get();
    }

    @Override
    public void close() {
        if (!open.compareAndSet(true, false)) return;
        connected.set(false);
        closeQuietly(subscribeConnection);
        closeQuietly(publishConnection);
        publisher.shutdownNow();
        Thread thread = subscriberThread;
        if (thread != null) thread.interrupt();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(Object value) {
        return new String(binary(value), StandardCharsets.UTF_8);
    }

    private static byte[] binary(Object value) {
        if (value instanceof byte[] bytes) return bytes;
        if (value instanceof String string) return bytes(string);
        throw new IllegalArgumentException("Expected RESP string");
    }

    private static void closeQuietly(RespConnection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (IOException ignored) {
        }
    }

    private record RedisEndpoint(String host, int port, boolean tls, String username, String password, int database) {
        private static RedisEndpoint parse(URI uri) {
            String scheme = uri.getScheme();
            if (!"redis".equals(scheme) && !"rediss".equals(scheme))
                throw new IllegalArgumentException("Unsupported Redis URI scheme");
            if (uri.getHost() == null) throw new IllegalArgumentException("Redis URI must include a host");
            String username = null;
            String password = null;
            if (uri.getRawUserInfo() != null) {
                String[] parts = uri.getRawUserInfo().split(":", 2);
                if (parts.length == 1) password = decode(parts[0]);
                else {
                    username = parts[0].isEmpty() ? null : decode(parts[0]);
                    password = decode(parts[1]);
                }
            }
            int database = 0;
            String path = uri.getPath();
            if (path != null && path.length() > 1) database = Integer.parseInt(path.substring(1));
            int port = uri.getPort() < 0 ? 6379 : uri.getPort();
            return new RedisEndpoint(uri.getHost(), port, "rediss".equals(scheme), username, password, database);
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
    }

    private static final class RespConnection implements AutoCloseable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;

        private RespConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new BufferedInputStream(socket.getInputStream());
            this.output = new BufferedOutputStream(socket.getOutputStream());
        }

        private synchronized Object command(byte[]... parts) throws IOException {
            send(parts);
            return read();
        }

        private synchronized void send(byte[]... parts) throws IOException {
            output.write(bytes("*" + parts.length + "\r\n"));
            for (byte[] part : parts) {
                output.write(bytes("$" + part.length + "\r\n"));
                output.write(part);
                output.write(bytes("\r\n"));
            }
            output.flush();
        }

        private Object read() throws IOException {
            int prefix = input.read();
            if (prefix < 0) throw new EOFException("Redis closed the connection");
            return switch (prefix) {
                case '+' -> line();
                case '-' -> throw new IOException("Redis error: " + line());
                case ':' -> Long.parseLong(line());
                case '$' -> bulk();
                case '*' -> array();
                default -> throw new IOException("Invalid RESP prefix: " + (char) prefix);
            };
        }

        private byte[] bulk() throws IOException {
            int length = Integer.parseInt(line());
            if (length < 0) return null;
            byte[] value = input.readNBytes(length);
            if (value.length != length || input.read() != '\r' || input.read() != '\n')
                throw new EOFException("Truncated RESP bulk string");
            return value;
        }

        private List<Object> array() throws IOException {
            int length = Integer.parseInt(line());
            if (length < 0) return null;
            List<Object> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) values.add(read());
            return values;
        }

        private String line() throws IOException {
            StringBuilder value = new StringBuilder();
            while (true) {
                int current = input.read();
                if (current < 0) throw new EOFException("Truncated RESP line");
                if (current == '\r') {
                    if (input.read() != '\n') throw new IOException("Malformed RESP line");
                    return value.toString();
                }
                value.append((char) current);
            }
        }

        private boolean closed() {
            return socket.isClosed() || !socket.isConnected();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
