package ru.neverland.localization;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MaterialNameConfig {
    private MaterialNameConfig() {}

    public static Material matchMaterial(String name) {
        return Material.matchMaterial(MaterialLabels.canonicalKey(name));
    }

    public static String customName(ItemMeta meta) {
        if (meta == null) return null;
        if (meta.hasCustomName() && meta.customName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(meta.customName());
        }
        if (meta.hasDisplayName() && meta.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        return null;
    }

    public static void reload(JavaPlugin plugin, MaterialLabels names) {
        names.reset();
        try (var stream = plugin.getResource("item-names.yml")) {
            if (stream != null) apply(names, YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось загрузить словарь предметов: " + exception.getMessage());
        }
        File file = new File(plugin.getDataFolder(), "item-names.yml");
        if (file.exists()) apply(names, YamlConfiguration.loadConfiguration(file));
    }

    public static void apply(MaterialLabels names, ConfigurationSection yaml) {
        for (String key : yaml.getKeys(false)) {
            String name = yaml.getString(key);
            if (MaterialLabels.canonicalKey(key).equals("DEEPSLATE_TILES")
                    && ("Глубинносланцевая плитка".equals(name)
                    || "Глубинносланцевая плитка (полный блок)".equals(name))) {
                name = "Глубинносланцевый кафель";
            }
            if ("CHAIN".equalsIgnoreCase(key) && "Цепь".equals(name)) name = "Железная цепь";
            names.override(key, name);
        }
    }
}
