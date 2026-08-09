package com.iantapply.relay.paper;

import com.iantapply.relay.core.RelayConfig;
import com.iantapply.relay.core.RelayConfigResolver;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads and validates Relay configuration from a Paper plugin's data directory. */
public final class PaperConfigLoader {
    private PaperConfigLoader() {}

    /**
     * Ensures the default configuration exists and resolves the effective Relay settings.
     * Redis URI sources are checked in system-property, environment, secret-file, then YAML order.
     *
     * @param plugin owning Paper plugin
     * @return validated Relay configuration
     * @throws IOException if a configuration resource or secret file cannot be read
     * @throws IllegalArgumentException if the resolved configuration is invalid
     */
    public static RelayConfig load(JavaPlugin plugin) throws IOException {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        Map<String, String> values = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key) && config.get(key) != null) {
                values.put(key, String.valueOf(config.get(key)));
            }
        }
        return RelayConfigResolver.resolve(
                values, plugin.getDataFolder().toPath(), "survival-1", RelayConfig.NodeRole.PAPER);
    }
}
