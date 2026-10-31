package ru.neverland.core;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** An optional per-addon messages.yml override for existing player-facing wording. */
public final class EditableMessages {
    private record Cache(YamlConfiguration yaml, Map<String, String> replacements) { }
    // Core classes can be shared by plugin class loaders. Never share one addon's YAML with another.
    private static final Map<JavaPlugin, Cache> messages = new ConcurrentHashMap<>();
    private EditableMessages() { }

    private static Cache load(Class<?> owner) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(owner);
        return messages.computeIfAbsent(plugin, EditableMessages::read);
    }

    private static Cache read(JavaPlugin plugin) {
        var yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        try (var stream = plugin.getResource("messages.yml")) {
            if (stream != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (Exception error) {
            plugin.getLogger().warning("Не удалось прочитать стандартные сообщения: " + error.getMessage());
        }
        Map<String, String> replacements = new HashMap<>();
        for (Map<?, ?> entry : yaml.getMapList("overrides")) {
            Object original = entry.get("source"), replacement = entry.get("text");
            if (original instanceof String from && replacement instanceof String to)
                replacements.put(from, to);
        }
        return new Cache(yaml, Map.copyOf(replacements));
    }

    /** Exact text keys avoid changing placeholders, permissions and internal IDs. */
    public static String text(Class<?> owner, String original) {
        return load(owner).replacements().getOrDefault(original, original);
    }

    public static String prefix(Class<?> owner, String fallback) {
        var yaml = load(owner).yaml();
        return yaml.getString("message-prefix-" + owner.getSimpleName(), yaml.getString("message-prefix", fallback));
    }

    public static String decorate(Class<?> owner, String fallbackPrefix, String original) {
        return prefix(owner, fallbackPrefix) + text(owner, original);
    }
}
