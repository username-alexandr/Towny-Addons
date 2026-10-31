package ru.neverland.townymarket;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Editable player-facing text with bundled defaults for existing installations. */
public final class MarketMessages {
    private final JavaPlugin plugin;
    private volatile YamlConfiguration yaml;

    public MarketMessages(JavaPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        reload();
    }

    public void reload() {
        var loaded = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        try (var stream = plugin.getResource("messages.yml")) {
            if (stream != null) loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (Exception error) {
            plugin.getLogger().warning("Не удалось загрузить стандартные сообщения рынка: " + error.getMessage());
        }
        yaml = loaded;
    }

    public String text(String key) { return text(key, Map.of()); }

    public String text(String key, Map<String, ?> values) {
        String value = yaml.getString(key, key);
        for (var entry : values.entrySet()) value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        return value;
    }
}
