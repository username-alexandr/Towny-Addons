package ru.neverland.townyideologies.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyideologies.model.TownIdeology;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, TownIdeology> towns = new HashMap<>();
    private boolean dirty;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "towns.yml");
    }

    public void load() {
        towns.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("towns");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "towns." + key + ".";
                String ideology = yaml.getString(path + "ideology", "");
                if (ideology.isBlank()) continue;
                int level = Math.max(1, Math.min(5, yaml.getInt(path + "level", 1)));
                towns.put(uuid, new TownIdeology(
                        uuid,
                        yaml.getString(path + "town-name", "Неизвестный город"),
                        ideology,
                        level,
                        yaml.getLong(path + "selected-at", System.currentTimeMillis())
                ));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Пропущен город с некорректным UUID в towns.yml: " + key);
            }
        }
        dirty = false;
    }

    public Optional<TownIdeology> get(UUID townId) {
        return Optional.ofNullable(towns.get(townId));
    }

    public void set(TownIdeology ideology) {
        towns.put(ideology.townId(), ideology);
        dirty = true;
    }

    public void remove(UUID townId) {
        if (towns.remove(townId) != null) dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (TownIdeology data : towns.values()) {
            String path = "towns." + data.townId() + ".";
            yaml.set(path + "town-name", data.townName());
            yaml.set(path + "ideology", data.ideologyId());
            yaml.set(path + "level", data.level());
            yaml.set(path + "selected-at", data.selectedAt());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку данных плагина.");
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить towns.yml: " + exception.getMessage());
        }
    }
}
