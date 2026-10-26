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

    public void reload() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    } }

    public String raw(String path) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return messages.getString(path, path);
    } }

    public List<String> list(String path) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        List<String> list = messages.getStringList(path);
        return list.isEmpty() ? Collections.emptyList() : list;
    } }

    public String format(String path, Map<String, String> replacements) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        return ColorUtil.color(replace(raw(path), replacements));
    } }

    public String replace(String text, Map<String, String> replacements) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    } }

    public void send(CommandSender sender, String path) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        send(sender, path, Collections.emptyMap());
    } }

    public void send(CommandSender sender, String path, Map<String, String> replacements) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        String prefix = raw("prefix");
        sender.sendMessage(ColorUtil.color(prefix + replace(raw(path), replacements)));
    } }
}
