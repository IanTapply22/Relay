package com.iantapply.relay.paper;

import com.iantapply.relay.core.RelayConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperConfigLoader {
    private PaperConfigLoader() {}

    public static RelayConfig load(JavaPlugin plugin) throws IOException {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        String uri = firstNonBlank(
                System.getProperty("relay.redis.uri"),
                environment(config.getString("redis.uri-environment-variable", "RELAY_REDIS_URI")),
                secretFile(
                        config.getString("redis.uri-file", ""),
                        plugin.getDataFolder().toPath()),
                config.getString("redis.uri", ""));
        if (uri == null) throw new IllegalArgumentException("No Redis URI configured");
        return new RelayConfig(
                config.getString("node.id", "survival-1"),
                RelayConfig.NodeRole.parse(config.getString("node.role", "paper")),
                URI.create(uri),
                config.getString("redis.namespace", "production"),
                config.getInt("messaging.maximum-payload-bytes", 65_536),
                config.getInt("messaging.dispatch-workers", 2),
                config.getInt("messaging.dispatch-queue-capacity", 1_024),
                Duration.ofSeconds(config.getLong("messaging.reject-messages-older-than-seconds", 60)));
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
