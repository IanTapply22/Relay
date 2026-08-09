package com.iantapply.relay.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;

class VelocityConfigLoaderTest {
    @TempDir
    Path directory;

    @Test
    void parsesQuotedHashesWithARealYamlParser() throws Exception {
        Files.writeString(directory.resolve("secret#redis.txt"), "redis://localhost:6380/3");
        Files.writeString(directory.resolve("config.yml"), """
                node:
                  id: velocity-test
                  role: velocity
                redis:
                  uri: redis://ignored:6379
                  uri-file: 'secret#redis.txt'
                  namespace: test
                messaging:
                  dispatch-workers: 3
                """);

        var config = VelocityConfigLoader.load(directory);

        assertEquals(6380, config.redisUri().getPort());
        assertEquals(3, config.dispatchWorkers());
    }

    @Test
    void rejectsDuplicateYamlKeys() throws Exception {
        Files.writeString(directory.resolve("config.yml"), """
                redis:
                  uri: redis://first:6379
                  uri: redis://second:6379
                """);

        assertThrows(DuplicateKeyException.class, () -> VelocityConfigLoader.load(directory));
    }
}
