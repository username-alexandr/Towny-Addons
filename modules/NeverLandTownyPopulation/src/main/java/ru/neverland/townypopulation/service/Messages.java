package ru.neverland.townypopulation.service;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.Map;

public final class Messages {
    private YamlConfiguration values;
    public Messages(YamlConfiguration values) { this.values=values; }
    public void reload(YamlConfiguration values) { synchronized (ru.neverland.core.SuiteState.lock()) { this.values=values; } }
    public void send(CommandSender sender, String key) { synchronized (ru.neverland.core.SuiteState.lock()) { send(sender,key,Map.of()); } }
    public void send(CommandSender sender, String key, Map<String,String> variables) { synchronized (ru.neverland.core.SuiteState.lock()) {
        String text=values.getString(key,key);
        for (var entry : variables.entrySet()) text=text.replace("{"+entry.getKey()+"}",entry.getValue());
        tell(sender,text);
    } }
    public void tell(CommandSender sender, String text) { synchronized (ru.neverland.core.SuiteState.lock()) { sender.sendMessage(color(values.getString("prefix","")+text)); } }
    public static String color(String text) { synchronized (ru.neverland.core.SuiteState.lock()) { return ChatColor.translateAlternateColorCodes('&',text); } }
    public static String number(double value) { synchronized (ru.neverland.core.SuiteState.lock()) { return String.format(java.util.Locale.ROOT,"%.1f",value); } }
}
