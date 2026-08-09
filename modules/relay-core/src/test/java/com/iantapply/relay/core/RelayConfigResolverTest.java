package com.iantapply.relay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelayConfigResolverTest {
    @TempDir
    Path directory;

    @Test
    void resolvesSecretFilesAndSharedDefaults() throws Exception {
        Files.writeString(directory.resolve("redis-uri.txt"), "redis://secret-host:6380/2\n");

        RelayConfig config = RelayConfigResolver.resolve(
                Map.of("redis.uri-file", "redis-uri.txt"), directory, "paper-1", RelayConfig.NodeRole.PAPER);

        assertEquals("secret-host", config.redisUri().getHost());
        assertEquals("paper-1", config.nodeId());
        assertEquals(1_024, config.dispatchQueueCapacity());
    }

    @Test
    void rejectsInvalidNumericConfiguration() {
        Map<String, String> values = Map.of(
                "redis.uri", "redis://localhost:6379",
                "messaging.dispatch-workers", "many");

        assertThrows(
                NumberFormatException.class,
                () -> RelayConfigResolver.resolve(values, directory, "velocity-1", RelayConfig.NodeRole.VELOCITY));
    }
}
