package ru.neverland.reputation.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.reputation.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration yaml;
    public MessageService(JavaPlugin plugin) { this.plugin = plugin; reload(); }
    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (Exception exception) { plugin.getLogger().warning("Не удалось загрузить messages.yml: " + exception.getMessage()); }
    }
    public String raw(String path) { String value = yaml.getString(path); return value == null ? path : value; }
    public String text(String path, Map<String, ?> replacements, boolean prefix) {
        String value = raw(path);
        for (Map.Entry<String, ?> entry : replacements.entrySet()) value = value.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        return ColorUtil.color((prefix ? yaml.getString("prefix", "") : "") + value);
    }
    public void send(CommandSender sender, String path) { send(sender, path, Map.of()); }
    public void send(CommandSender sender, String path, Map<String, ?> replacements) { sender.sendMessage(text(path, replacements, true)); }
}
