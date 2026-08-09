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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Minimal RESP2 client dedicated to Relay's PUBLISH/SUBSCRIBE use case. */
public final class RedisPubSubTransport implements MessageTransport {
    static final int MAXIMUM_RESP_LINE_LENGTH = 4_096;
    static final int MAXIMUM_RESP_BULK_BYTES = 16 * 1_024 * 1_024;
    static final int MAXIMUM_RESP_ARRAY_ELEMENTS = 1_024;
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int PUBLISH_READ_TIMEOUT_MILLIS = 3_000;

    private final RedisEndpoint endpoint;
    private final Logger logger;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean subscriberConnected = new AtomicBoolean();
    private final AtomicBoolean publisherConnected = new AtomicBoolean();
    private final AtomicLong reconnects = new AtomicLong();
    private final ExecutorService publisher;
    private final Collection<PendingPublication> pendingPublications =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
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
        PendingPublication publication = new PendingPublication(channel, payload.clone(), result);
        pendingPublications.add(publication);
        if (!open.get()) {
            publication.fail(closedException());
            return result;
        }
        try {
            publisher.execute(publication);
        } catch (RejectedExecutionException exception) {
            publication.fail(closedException());
        }
        return result;
    }

    @Override
    public void start(Collection<String> channels, BiConsumer<String, byte[]> receiver) {
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(receiver, "receiver");
        if (channels.isEmpty()) throw new IllegalArgumentException("At least one Redis channel is required");
        if (!open.get()) throw new IllegalStateException("Redis transport is closed");
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("Redis transport already started");
        if (!open.get()) throw new IllegalStateException("Redis transport is closed");
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
                RespConnection connection = connect(true);
                subscribeConnection = connection;
                if (!open.get()) {
                    closeQuietly(connection);
                    break;
                }
                List<byte[]> command = new ArrayList<>();
                command.add(bytes("SUBSCRIBE"));
                channels.forEach(channel -> command.add(bytes(channel)));
                connection.send(command.toArray(byte[][]::new));
                for (int index = 0; index < channels.size(); index++) connection.read();
                if (connectedBefore) reconnects.incrementAndGet();
                connectedBefore = true;
                subscriberConnected.set(true);
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
                subscriberConnected.set(false);
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

    private RespConnection connect(boolean subscriber) throws IOException {
        Socket socket;
        if (endpoint.tls()) {
            SSLSocket secureSocket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            configureTlsSocket(secureSocket);
            socket = secureSocket;
        } else {
            socket = new Socket();
        }
        try {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(PUBLISH_READ_TIMEOUT_MILLIS);
            socket.setKeepAlive(true);
            if (socket instanceof SSLSocket secureSocket) secureSocket.startHandshake();
            RespConnection connection = new RespConnection(socket);
            if (endpoint.password() != null) {
                Object response = endpoint.username() == null
                        ? connection.command(bytes("AUTH"), bytes(endpoint.password()))
                        : connection.command(bytes("AUTH"), bytes(endpoint.username()), bytes(endpoint.password()));
                requireOk(response, "AUTH");
            }
            if (endpoint.database() != 0)
                requireOk(connection.command(bytes("SELECT"), bytes(Integer.toString(endpoint.database()))), "SELECT");
            if (subscriber) socket.setSoTimeout(0);
            return connection;
        } catch (IOException exception) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    static void configureTlsSocket(SSLSocket socket) {
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
    }

    private static void requireOk(Object response, String command) throws IOException {
        if (!"OK".equals(response)) throw new IOException(command + " failed");
    }

    @Override
    public boolean connected() {
        return publisherConnected() && subscriberConnected();
    }

    @Override
    public boolean publisherConnected() {
        return publisherConnected.get();
    }

    @Override
    public boolean subscriberConnected() {
        return subscriberConnected.get();
    }

    @Override
    public long reconnects() {
        return reconnects.get();
    }

    @Override
    public void close() {
        if (!open.compareAndSet(true, false)) return;
        subscriberConnected.set(false);
        publisherConnected.set(false);
        closeQuietly(subscribeConnection);
        closeQuietly(publishConnection);
        publisher.shutdownNow();
        IOException exception = closedException();
        pendingPublications.forEach(publication -> publication.fail(exception));
        Thread thread = subscriberThread;
        if (thread != null) thread.interrupt();
        awaitTermination(publisher, "publisher");
        if (thread != null) {
            try {
                thread.join(2_000);
                if (thread.isAlive()) logger.warning("Relay Redis subscriber did not terminate cleanly");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void awaitTermination(ExecutorService executor, String resource) {
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.log(Level.WARNING, "Relay Redis {0} did not terminate cleanly", resource);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static IOException closedException() {
        return new IOException("Redis transport is closed");
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

    private final class PendingPublication implements Runnable {
        private final String channel;
        private final byte[] payload;
        private final CompletableFuture<Void> result;

        private PendingPublication(String channel, byte[] payload, CompletableFuture<Void> result) {
            this.channel = channel;
            this.payload = payload;
            this.result = result;
        }

        @Override
        public void run() {
            if (!open.get()) {
                fail(closedException());
                return;
            }
            try {
                RespConnection connection = publishConnection;
                if (connection == null || connection.closed()) {
                    connection = connect(false);
                    publishConnection = connection;
                }
                Object response = connection.command(bytes("PUBLISH"), bytes(channel), payload);
                if (!(response instanceof Long)) throw new IOException("Unexpected PUBLISH response");
                if (open.get()) {
                    publisherConnected.set(true);
                    complete();
                } else {
                    fail(closedException());
                }
            } catch (Exception exception) {
                publisherConnected.set(false);
                closeQuietly(publishConnection);
                publishConnection = null;
                fail(new IOException("Redis did not accept the message", exception));
            }
        }

        private void complete() {
            pendingPublications.remove(this);
            result.complete(null);
        }

        private void fail(Exception exception) {
            pendingPublications.remove(this);
            result.completeExceptionally(exception);
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
            int length = integer(line(), "bulk length");
            if (length < 0) return null;
            if (length > MAXIMUM_RESP_BULK_BYTES) throw new IOException("RESP bulk string exceeds configured limit");
            byte[] value = input.readNBytes(length);
            if (value.length != length || input.read() != '\r' || input.read() != '\n')
                throw new EOFException("Truncated RESP bulk string");
            return value;
        }

        private List<Object> array() throws IOException {
            int length = integer(line(), "array length");
            if (length < 0) return null;
            if (length > MAXIMUM_RESP_ARRAY_ELEMENTS) throw new IOException("RESP array exceeds configured limit");
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
                if (value.length() >= MAXIMUM_RESP_LINE_LENGTH)
                    throw new IOException("RESP line exceeds configured limit");
                value.append((char) current);
            }
        }

        private static int integer(String value, String field) throws IOException {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid RESP " + field, exception);
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
