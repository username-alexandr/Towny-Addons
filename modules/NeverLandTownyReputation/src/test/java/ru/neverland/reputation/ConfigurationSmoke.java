package ru.neverland.reputation;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class ConfigurationSmoke {
    public static void main(String[] args) {
        File root = new File(args[0]);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(root, "config.yml"));
        YamlConfiguration levels = YamlConfiguration.loadConfiguration(new File(root, "levels.yml"));
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new File(root, "messages.yml"));
        if (config.getConfigurationSection("features") == null) throw new AssertionError("features missing");
        if (levels.getConfigurationSection("levels.exalted") == null) throw new AssertionError("exalted tier missing");
        if (messages.getString("prefix") == null) throw new AssertionError("prefix missing");
        if (levels.getConfigurationSection("levels").getKeys(false).size() != 8) throw new AssertionError("expected 8 tiers");
    }
}
