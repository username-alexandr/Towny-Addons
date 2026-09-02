package ru.neverland.townytaxes.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townytaxes.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin; private YamlConfiguration yaml;
    public MessageService(JavaPlugin plugin) { this.plugin = plugin; reload(); }
    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (Exception exception) { plugin.getLogger().warning("Не удалось загрузить messages.yml: " + exception.getMessage()); }
    }
    public String text(String path, Map<String, ?> values, boolean prefix) {
        String value = yaml.getString(path, path); for (Map.Entry<String, ?> entry : values.entrySet()) value = value.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        return ColorUtil.color((prefix ? yaml.getString("prefix", "") : "") + value);
    }
    public void send(CommandSender sender, String path) { send(sender, path, Map.of()); }
    public void send(CommandSender sender, String path, Map<String, ?> values) { sender.sendMessage(text(path, values, true)); }
    public List<String> list(String path) { return yaml.getStringList(path).stream().map(ColorUtil::color).toList(); }
}
