package ru.neverland.townybuilds.service;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townybuilds.util.ColorUtil;

import java.io.File;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;
    private YamlConfiguration defaults;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                defaults = YamlConfiguration.loadConfiguration(reader);
                messages.setDefaults(defaults);
                messages.options().copyDefaults(true);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось загрузить стандартные сообщения: " + exception.getMessage());
        }
    } }

    public Component component(String key) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return component(key, Map.of());
    } }

    public Component component(String key, Map<String, ?> replacements) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        String value = value(key);
        for (Map.Entry<String, ?> replacement : replacements.entrySet()) {
            value = value.replace("{" + replacement.getKey() + "}", String.valueOf(replacement.getValue()));
        }
        return ColorUtil.component(value);
    } }

    public void send(CommandSender sender, String key) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        send(sender, key, Map.of());
    } }

    public void send(CommandSender sender, String key, Map<String, ?> replacements) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        String prefix = messages.getString("prefix", "");
        String value = value(key);
        for (Map.Entry<String, ?> replacement : replacements.entrySet()) {
            value = value.replace("{" + replacement.getKey() + "}", String.valueOf(replacement.getValue()));
        }
        sender.sendMessage(ColorUtil.component(prefix + value));
    } }

    private String value(String key) {
        String value = messages.getString(key);
        if (value == null || value.equalsIgnoreCase(key)) {
            value = defaults == null ? null : defaults.getString(key);
        }
        return value == null ? key : value;
    }
}
