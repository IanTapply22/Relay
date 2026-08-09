package com.iantapply.relay.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.Test;

class RedisPubSubTransportTest {
    @Test
    void enablesTlsHostnameVerification() throws Exception {
        SSLSocket socket =
                (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket();
        try (socket) {
            RedisPubSubTransport.configureTlsSocket(socket);
            assertEquals("HTTPS", socket.getSSLParameters().getEndpointIdentificationAlgorithm());
        }
    }

    @Test
    void authenticatesSelectsDatabaseAndPublishes() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<List<List<String>>> observed = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = server.accept()) {
                    BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                    BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
                    List<List<String>> commands = new ArrayList<>();
                    for (int index = 0; index < 3; index++) {
                        List<String> command = readCommand(input);
                        commands.add(command);
                        write(output, index < 2 ? "+OK\r\n" : ":1\r\n");
                    }
                    return commands;
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            RedisPubSubTransport transport =
                    transport("redis://relay-user:secret@127.0.0.1:" + server.getLocalPort() + "/2");
            try {
                transport
                        .publish("relay:test", bytes("payload"))
                        .toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                List<List<String>> commands = observed.get(2, TimeUnit.SECONDS);
                assertEquals(List.of("AUTH", "relay-user", "secret"), commands.get(0));
                assertEquals(List.of("SELECT", "2"), commands.get(1));
                assertEquals(List.of("PUBLISH", "relay:test", "payload"), commands.get(2));
                assertTrue(transport.publisherConnected());
            } finally {
                transport.close();
            }
        }
    }

    @Test
    void reconnectsSubscriptions() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch secondSubscription = new CountDownLatch(1);
            CompletableFuture<Void> redis = CompletableFuture.runAsync(() -> {
                try {
                    for (int attempt = 0; attempt < 2; attempt++) {
                        try (Socket socket = server.accept()) {
                            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
                            assertEquals(List.of("SUBSCRIBE", "relay:test"), readCommand(input));
                            write(output, "*3\r\n$9\r\nsubscribe\r\n$10\r\nrelay:test\r\n:1\r\n");
                            if (attempt == 1) {
                                secondSubscription.countDown();
                                while (!socket.isClosed() && input.read() >= 0) {}
                            }
                        }
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            RedisPubSubTransport transport = transport("redis://127.0.0.1:" + server.getLocalPort());
            try {
                transport.start(List.of("relay:test"), (channel, payload) -> {});
                assertTrue(secondSubscription.await(3, TimeUnit.SECONDS));
                await(() -> transport.reconnects() == 1 && transport.subscriberConnected());
                assertEquals(1, transport.reconnects());
            } finally {
                transport.close();
            }
            redis.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void failsRunningAndQueuedPublicationsDuringShutdown() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch firstReceived = new CountDownLatch(1);
            CompletableFuture<Void> redis = CompletableFuture.runAsync(() -> {
                try (Socket socket = server.accept()) {
                    readCommand(new BufferedInputStream(socket.getInputStream()));
                    firstReceived.countDown();
                    while (socket.getInputStream().read() >= 0) {}
                } catch (IOException ignored) {
                }
            });
            RedisPubSubTransport transport = transport("redis://127.0.0.1:" + server.getLocalPort());
            CompletableFuture<Void> first =
                    transport.publish("relay:test", bytes("one")).toCompletableFuture();
            assertTrue(firstReceived.await(2, TimeUnit.SECONDS));
            CompletableFuture<Void> second =
                    transport.publish("relay:test", bytes("two")).toCompletableFuture();

            transport.close();

            assertTrue(first.isCompletedExceptionally());
            assertTrue(second.isCompletedExceptionally());
            redis.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void timesOutAnUnresponsivePublisher() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch received = new CountDownLatch(1);
            CompletableFuture<Void> redis = CompletableFuture.runAsync(() -> {
                try (Socket socket = server.accept()) {
                    readCommand(new BufferedInputStream(socket.getInputStream()));
                    received.countDown();
                    while (socket.getInputStream().read() >= 0) {}
                } catch (IOException ignored) {
                }
            });
            RedisPubSubTransport transport = transport("redis://127.0.0.1:" + server.getLocalPort());
            try {
                CompletableFuture<Void> publication =
                        transport.publish("relay:test", bytes("payload")).toCompletableFuture();
                assertTrue(received.await(2, TimeUnit.SECONDS));
                try {
                    publication.get(5, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException expected) {
                    assertTrue(expected.getCause() instanceof IOException);
                }
                assertTrue(publication.isCompletedExceptionally());
            } finally {
                transport.close();
            }
            redis.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsOversizedAndMalformedResponses() throws Exception {
        assertInvalidResponse("$" + (RedisPubSubTransport.MAXIMUM_RESP_BULK_BYTES + 1) + "\r\n");
        assertInvalidResponse("*" + (RedisPubSubTransport.MAXIMUM_RESP_ARRAY_ELEMENTS + 1) + "\r\n");
        assertInvalidResponse("+" + "a".repeat(RedisPubSubTransport.MAXIMUM_RESP_LINE_LENGTH + 1) + "\r\n");
        assertInvalidResponse("?invalid\r\n");
    }

    private static void assertInvalidResponse(String response) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> redis = CompletableFuture.runAsync(() -> {
                try (Socket socket = server.accept()) {
                    readCommand(new BufferedInputStream(socket.getInputStream()));
                    write(new BufferedOutputStream(socket.getOutputStream()), response);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            RedisPubSubTransport transport = transport("redis://127.0.0.1:" + server.getLocalPort());
            try {
                CompletableFuture<Void> publication =
                        transport.publish("relay:test", bytes("payload")).toCompletableFuture();
                try {
                    publication.get(2, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException expected) {
                    assertTrue(expected.getCause() instanceof IOException);
                }
                assertTrue(publication.isCompletedExceptionally());
            } finally {
                transport.close();
            }
            redis.get(2, TimeUnit.SECONDS);
        }
    }

    private static RedisPubSubTransport transport(String uri) {
        return new RedisPubSubTransport(URI.create(uri), Logger.getAnonymousLogger());
    }

    private static List<String> readCommand(BufferedInputStream input) throws IOException {
        String array = line(input);
        if (!array.startsWith("*")) throw new IOException("Expected RESP array");
        int length = Integer.parseInt(array.substring(1));
        List<String> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            String bulk = line(input);
            if (!bulk.startsWith("$")) throw new IOException("Expected RESP bulk string");
            int bytes = Integer.parseInt(bulk.substring(1));
            byte[] value = input.readNBytes(bytes);
            if (value.length != bytes || input.read() != '\r' || input.read() != '\n') throw new EOFException();
            values.add(new String(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String line(BufferedInputStream input) throws IOException {
        StringBuilder value = new StringBuilder();
        while (true) {
            int current = input.read();
            if (current < 0) throw new EOFException();
            if (current == '\r') {
                if (input.read() != '\n') throw new IOException("Malformed line");
                return value.toString();
            }
            value.append((char) current);
        }
    }

    private static void write(BufferedOutputStream output, String response) throws IOException {
        output.write(bytes(response));
        output.flush();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void await(Check check) throws Exception {
        for (int attempt = 0; attempt < 100 && !check.done(); attempt++) Thread.sleep(20);
    }

    @FunctionalInterface
    private interface Check {
        boolean done();
    }
}
