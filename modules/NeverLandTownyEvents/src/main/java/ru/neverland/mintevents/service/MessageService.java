package ru.neverland.mintevents.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
            if (stream != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось загрузить встроенные сообщения: " + exception.getMessage());
        }
    }

    public String raw(String key) {
        return template(yaml, key);
    }

    public static String template(YamlConfiguration yaml, String key) {
        String value = yaml.getString(key);
        return value == null ? key : value;
    }

    public String format(String key, Map<String, ?> placeholders) {
        return formatText(raw(key), placeholders, true);
    }

    public String formatText(String value, Map<String, ?> placeholders, boolean prefix) {
        String text = value == null ? "" : value;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            text = text.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        String base = prefix ? java.util.Objects.toString(yaml.getString("prefix"), "") + text : text;
        return ColorUtil.color(base);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        sender.sendMessage(format(key, placeholders));
    }

    public List<String> list(String key) {
        List<String> values = yaml.getStringList(key);
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) result.add(ColorUtil.color(value));
        return result;
    }
}
