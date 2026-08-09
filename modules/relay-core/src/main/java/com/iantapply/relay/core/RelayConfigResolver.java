package com.iantapply.relay.core;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Resolves platform-neutral Relay settings and Redis credential precedence. */
public final class RelayConfigResolver {
    private RelayConfigResolver() {}

    /**
     * Resolves a validated configuration from flattened dotted keys.
     *
     * @param values configuration values
     * @param dataDirectory directory used to resolve relative secret files
     * @param defaultNodeId platform-specific default node identifier
     * @param defaultRole platform-specific default role
     * @return validated Relay configuration
     * @throws IOException if a configured secret file cannot be read
     */
    public static RelayConfig resolve(
            Map<String, String> values, Path dataDirectory, String defaultNodeId, RelayConfig.NodeRole defaultRole)
            throws IOException {
        String uri = firstNonBlank(
                System.getProperty("relay.redis.uri"),
                environment(values.getOrDefault("redis.uri-environment-variable", "RELAY_REDIS_URI")),
                secretFile(values.get("redis.uri-file"), dataDirectory),
                values.get("redis.uri"));
        if (uri == null) throw new IllegalArgumentException("No Redis URI configured");
        return new RelayConfig(
                values.getOrDefault("node.id", defaultNodeId),
                RelayConfig.NodeRole.parse(values.getOrDefault("node.role", defaultRole.channelName())),
                URI.create(uri),
                values.getOrDefault("redis.namespace", "production"),
                integer(values, "messaging.maximum-payload-bytes", 65_536),
                integer(values, "messaging.dispatch-workers", 2),
                integer(values, "messaging.dispatch-queue-capacity", 1_024),
                Duration.ofSeconds(longValue(values, "messaging.reject-messages-older-than-seconds", 60)));
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        return Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
    }

    private static long longValue(Map<String, String> values, String key, long fallback) {
        return Long.parseLong(values.getOrDefault(key, Long.toString(fallback)));
    }

    private static String environment(String name) {
        return name == null || name.isBlank() ? null : System.getenv(name);
    }

    private static String secretFile(String configuredPath, Path dataDirectory) throws IOException {
        if (configuredPath == null || configuredPath.isBlank()) return null;
        Path path = Path.of(configuredPath);
        if (!path.isAbsolute()) path = dataDirectory.resolve(path).normalize();
        return Files.readString(path, StandardCharsets.UTF_8).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
}
