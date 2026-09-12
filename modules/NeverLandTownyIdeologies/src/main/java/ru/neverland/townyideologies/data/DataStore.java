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
    private boolean ready;
    private void loaded(){ru.neverland.core.AtomicFiles.loaded(file.toPath());ready=true;}
    private void gate(){if(!ready||!ru.neverland.core.AtomicFiles.writable(file.toPath()))throw new IllegalStateException("Хранилище заблокировано: восстановите данные и перезапустите сервер");}

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, TownIdeology> towns = new HashMap<>();
    private boolean dirty;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "towns.yml");
    }

    public void load() {ready=false;
        towns.clear();

        YamlConfiguration yaml = ru.neverland.core.SafeYaml.load(file.toPath());ru.neverland.core.SafeYaml.keys(yaml,"towns");
        ConfigurationSection root = ru.neverland.core.SafeYaml.section(yaml,"towns");
        if(root==null){loaded();return;}
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "towns." + key + ".";
                String ideology = ru.neverland.core.SafeYaml.stringValue(yaml,path + "ideology", "");
                if (ideology.isBlank()) throw new IllegalArgumentException("Пустая идеология");
                int level = ru.neverland.core.SafeYaml.intValue(yaml,path + "level", 1);if(level<1||level>5)throw new IllegalArgumentException("Неверный уровень идеологии");
                towns.put(uuid, new TownIdeology(
                        uuid,
                        ru.neverland.core.SafeYaml.stringValue(yaml,path + "town-name", "Неизвестный город"),
                        ideology,
                        level,
                        ru.neverland.core.SafeYaml.longValue(yaml,path + "selected-at", System.currentTimeMillis())
                ));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Повреждены сохранённые данные: DataStore");
            }
        }
        dirty = false;
    loaded();}

    public Optional<TownIdeology> get(UUID townId) {gate();
        return Optional.ofNullable(towns.get(townId));
    }

    public void set(TownIdeology ideology) {gate();
        towns.put(ideology.townId(), ideology);
        dirty = true;
    }

    public void remove(UUID townId) {gate();
        if (towns.remove(townId) != null) dirty = true;
    }

    public void saveIfDirty() {gate();
        if (dirty) save();
    }

    public void save() {gate();
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
            ru.neverland.core.AtomicFiles.write(file.toPath(),yaml::saveToString);
            dirty = false;
        } catch(IOException exception){throw new java.io.UncheckedIOException(exception);}
    }
}
