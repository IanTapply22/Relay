package com.iantapply.relay.velocity;

import com.iantapply.relay.core.RelayConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

final class VelocityConfigLoader {
    private VelocityConfigLoader() {}

    static RelayConfig load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve("config.yml");
        if (Files.notExists(file)) {
            try (InputStream source = VelocityConfigLoader.class.getResourceAsStream("/velocity-config.yml")) {
                if (source == null) throw new IOException("Bundled velocity-config.yml is missing");
                Files.copy(source, file);
            }
        }
        Map<String, String> values = parse(file);
        String uri = firstNonBlank(
                System.getProperty("relay.redis.uri"),
                environment(values.getOrDefault("redis.uri-environment-variable", "RELAY_REDIS_URI")),
                secretFile(values.get("redis.uri-file"), dataDirectory),
                values.get("redis.uri"));
        if (uri == null) throw new IllegalArgumentException("No Redis URI configured");
        return new RelayConfig(
                values.getOrDefault("node.id", "velocity-1"),
                RelayConfig.NodeRole.parse(values.getOrDefault("node.role", "velocity")),
                URI.create(uri),
                values.getOrDefault("redis.namespace", "production"),
                integer(values, "messaging.maximum-payload-bytes", 65_536),
                integer(values, "messaging.dispatch-workers", 2),
                integer(values, "messaging.dispatch-queue-capacity", 1_024),
                Duration.ofSeconds(integer(values, "messaging.reject-messages-older-than-seconds", 60)));
    }

    private static Map<String, String> parse(Path file) throws IOException {
        Map<String, String> values = new HashMap<>();
        String section = null;
        for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String withoutComment = rawLine.split("#", 2)[0];
            if (withoutComment.isBlank()) continue;
            int indent = withoutComment.length() - withoutComment.stripLeading().length();
            String line = withoutComment.trim();
            int separator = line.indexOf(':');
            if (separator < 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (indent == 0 && value.isEmpty()) {
                section = key;
                continue;
            }
            if (section != null) values.put(section + "." + key, unquote(value));
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1);
        return value;
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        return Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
    }

    private static String environment(String name) { return name == null || name.isBlank() ? null : System.getenv(name); }

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
