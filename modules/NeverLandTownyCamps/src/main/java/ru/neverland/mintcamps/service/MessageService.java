package ru.neverland.mintcamps.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcamps.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration yaml;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
                yaml.options().copyDefaults(true);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось загрузить встроенные сообщения: " + exception.getMessage());
        }
    }

    public String raw(String path) {
        return yaml.getString(path, path);
    }

    public List<String> list(String path) {
        return yaml.getStringList(path);
    }

    public String replace(String text, Map<String, ?> replacements) {
        String value = text == null ? "" : text;
        for (Map.Entry<String, ?> entry : replacements.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return value;
    }

    public String format(String path, Map<String, ?> replacements) {
        return ColorUtil.legacy(replace(raw(path), replacements));
    }

    public String formatConfig(String text) {
        return ColorUtil.legacy(text == null ? "" : text);
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Collections.emptyMap());
    }

    public void send(CommandSender sender, String path, Map<String, ?> replacements) {
        sender.sendMessage(ColorUtil.legacy(raw("prefix") + replace(raw(path), replacements)));
    }
}
