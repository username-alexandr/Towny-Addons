package ru.neverland.morstownstick.service;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.morstownstick.util.ColorUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String raw(String path) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return plugin.getConfig().getString("messages." + path, path);
    } }

    public String replace(String input, Map<String, ?> replacements) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        String value = input == null ? "" : input;
        for (Map.Entry<String, ?> entry : replacements.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return value;
    } }

    public String colored(String path, Map<String, ?> replacements) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        return ColorUtil.legacy(raw("prefix") + replace(raw(path), replacements));
    } }

    public void send(CommandSender sender, String path) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        send(sender, path, Collections.emptyMap());
    } }

    public void send(CommandSender sender, String path, Map<String, ?> replacements) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        sender.sendMessage(colored(path, replacements));
    } }

    public void sendFallback(CommandSender sender, String path, String fallback, Map<String, ?> replacements) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        String configured = plugin.getConfig().getString("messages." + path);
        String text = configured == null || configured.isBlank() ? fallback : configured;
        sender.sendMessage(ColorUtil.legacy(raw("prefix") + replace(text, replacements)));
    } }

    public void sendList(CommandSender sender, String path) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        List<String> lines = plugin.getConfig().getStringList("messages." + path);
        for (String line : lines) sender.sendMessage(ColorUtil.legacy(line));
    } }
}
