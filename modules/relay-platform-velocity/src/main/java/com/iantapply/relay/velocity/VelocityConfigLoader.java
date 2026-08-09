package com.iantapply.relay.velocity;

import com.iantapply.relay.core.RelayConfig;
import com.iantapply.relay.core.RelayConfigResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

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
        return RelayConfigResolver.resolve(values, dataDirectory, "velocity-1", RelayConfig.NodeRole.VELOCITY);
    }

    private static Map<String, String> parse(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document;
        try (Reader reader = Files.newBufferedReader(file)) {
            document = new Yaml(new SafeConstructor(options)).load(reader);
        }
        if (!(document instanceof Map<?, ?> root))
            throw new IllegalArgumentException("Relay config must be a YAML map");
        Map<String, String> values = new LinkedHashMap<>();
        flatten("", root, values);
        return values;
    }

    private static void flatten(String prefix, Map<?, ?> source, Map<String, String> destination) {
        source.forEach((rawKey, rawValue) -> {
            if (!(rawKey instanceof String key))
                throw new IllegalArgumentException("Relay config keys must be strings");
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (rawValue instanceof Map<?, ?> nested) flatten(path, nested, destination);
            else if (rawValue == null) destination.put(path, "");
            else if (rawValue instanceof String || rawValue instanceof Number || rawValue instanceof Boolean) {
                destination.put(path, String.valueOf(rawValue));
            } else {
                throw new IllegalArgumentException("Unsupported Relay config value at " + path);
            }
        });
    }
}
