package ru.neverland.townypopulation.service;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.Map;

public final class Messages {
    private YamlConfiguration values;
    public Messages(YamlConfiguration values) { this.values=values; }
    public void reload(YamlConfiguration values) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { this.values=values; } }
    public void send(CommandSender sender, String key) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { send(sender,key,Map.of()); } }
    public void send(CommandSender sender, String key, Map<String,String> variables) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        String text=values.getString(key,key);
        for (var entry : variables.entrySet()) text=text.replace("{"+entry.getKey()+"}",entry.getValue());
        tell(sender,text);
    } }
    public void tell(CommandSender sender, String text) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { sender.sendMessage(color(values.getString("prefix","")+text)); } }
    public static String color(String text) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return ChatColor.translateAlternateColorCodes('&',text); } }
    public static String number(double value) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return String.format(java.util.Locale.ROOT,"%.1f",value); } }
}
