package ru.neverland.townyideologies.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyideologies.util.ColorUtil;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    public String raw(String path) {
        return messages.getString(path, path);
    }

    public List<String> list(String path) {
        List<String> list = messages.getStringList(path);
        return list.isEmpty() ? Collections.emptyList() : list;
    }

    public String format(String path, Map<String, String> replacements) {
        return ColorUtil.color(replace(raw(path), replacements));
    }

    public String replace(String text, Map<String, String> replacements) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Collections.emptyMap());
    }

    public void send(CommandSender sender, String path, Map<String, String> replacements) {
        String prefix = raw("prefix");
        sender.sendMessage(ColorUtil.color(prefix + replace(raw(path), replacements)));
    }
}
