package ru.neverland.townybuilds.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townybuilds.util.ItemCodec;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final int storageSize;
    private final Map<UUID, TownData> towns = new LinkedHashMap<>();
    private boolean dirty;

    public DataStore(JavaPlugin plugin, int storageSize) {
        this.plugin = plugin;
        this.storageSize = storageSize;
        this.file = new File(plugin.getDataFolder(), "town-data.yml");
    }

    public synchronized void load() {
        towns.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("towns");
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            try {
                UUID townId = UUID.fromString(rawId);
                TownData data = new TownData(townId, storageSize);
                ConfigurationSection section = root.getConfigurationSection(rawId);
                Map<String, Integer> levels = new HashMap<>();
                ConfigurationSection levelSection = section == null ? null : section.getConfigurationSection("levels");
                if (levelSection != null) {
                    for (String project : levelSection.getKeys(false)) {
                        levels.put(project, levelSection.getInt(project));
                    }
                }
                data.loadLevels(levels);
                Map<String, ConstructionSite> sites = new HashMap<>();
                ConfigurationSection construction = section == null ? null : section.getConfigurationSection("construction");
                if (construction != null) {
                    for (String projectId : construction.getKeys(false)) {
                        ConfigurationSection site = construction.getConfigurationSection(projectId);
                        if (site == null) continue;
                        try {
                            UUID worldId = UUID.fromString(site.getString("world", ""));
                            BlockFace facing = BlockFace.valueOf(site.getString("facing", "NORTH"));
                            sites.put(projectId, new ConstructionSite(projectId, worldId,
                                    site.getInt("x"), site.getInt("y"), site.getInt("z"), facing,
                                    site.getInt("completed-stage"), site.getInt("target-stage"),
                                    site.getInt("build-from-stage", 1), site.getBoolean("active"),
                                    site.getInt("architecture-version", 1)));
                        } catch (IllegalArgumentException exception) {
                            plugin.getLogger().warning("Повреждена строительная площадка " + projectId
                                    + " города " + townId + ": " + exception.getMessage());
                        }
                    }
                }
                data.loadConstructionSites(sites);
                Map<String, ResourceFund> funds = new HashMap<>();
                ConfigurationSection fundSection = section == null ? null : section.getConfigurationSection("resource-funds");
                if (fundSection != null) {
                    for (String projectId : fundSection.getKeys(false)) {
                        ConfigurationSection projectFund = fundSection.getConfigurationSection(projectId);
                        if (projectFund == null) continue;
                        ResourceFund fund = new ResourceFund(projectFund.getInt("target-level", 1));
                        ConfigurationSection entries = projectFund.getConfigurationSection("entries");
                        if (entries != null) {
                            for (String entryId : entries.getKeys(false)) {
                                ConfigurationSection entry = entries.getConfigurationSection(entryId);
                                if (entry == null) continue;
                                String encoded = entry.getString("item", "");
                                int amount = entry.getInt("amount");
                                if (encoded.isBlank() || amount <= 0) continue;
                                fund.add(ItemCodec.decodeSingle(encoded), amount);
                            }
                        }
                        funds.put(projectId, fund);
                    }
                }
                data.loadResourceFunds(funds);
                if (section != null) {
                    ItemStack[] contents = ItemCodec.decode(section.getString("inventory"), storageSize);
                    data.setStorage(contents, storageSize);
                }
                towns.put(townId, data);
            } catch (IllegalArgumentException | IOException | ClassNotFoundException exception) {
                plugin.getLogger().warning("Пропущены повреждённые данные города " + rawId + ": " + exception.getMessage());
            }
        }
        dirty = false;
    }

    public synchronized TownData town(UUID townId) {
        return towns.computeIfAbsent(townId, id -> {
            dirty = true;
            return new TownData(id, storageSize);
        });
    }

    public synchronized void markDirty() {
        dirty = true;
    }

    public synchronized Map<UUID, TownData> towns() {
        return Map.copyOf(towns);
    }

    public synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (TownData data : towns.values()) {
            String path = "towns." + data.townId();
            for (Map.Entry<String, Integer> level : data.levels().entrySet()) {
                yaml.set(path + ".levels." + level.getKey(), level.getValue());
            }
            for (ConstructionSite site : data.constructionSites().values()) {
                String root = path + ".construction." + site.projectId();
                yaml.set(root + ".world", site.worldId().toString());
                yaml.set(root + ".x", site.originX());
                yaml.set(root + ".y", site.originY());
                yaml.set(root + ".z", site.originZ());
                yaml.set(root + ".facing", site.facing().name());
                yaml.set(root + ".completed-stage", site.completedStage());
                yaml.set(root + ".target-stage", site.targetStage());
                yaml.set(root + ".build-from-stage", site.buildFromStage());
                yaml.set(root + ".active", site.active());
                yaml.set(root + ".architecture-version", site.architectureVersion());
            }
            for (Map.Entry<String, ResourceFund> fundEntry : data.resourceFunds().entrySet()) {
                String root = path + ".resource-funds." + fundEntry.getKey();
                ResourceFund fund = fundEntry.getValue();
                yaml.set(root + ".target-level", fund.targetLevel());
                int index = 0;
                for (ResourceFund.Snapshot entry : fund.entries()) {
                    try {
                        yaml.set(root + ".entries." + index + ".item", ItemCodec.encodeSingle(entry.template()));
                        yaml.set(root + ".entries." + index + ".amount", entry.amount());
                        index++;
                    } catch (IOException exception) {
                        plugin.getLogger().severe("Не удалось сериализовать фонд проекта " + fundEntry.getKey()
                                + " города " + data.townId() + ": " + exception.getMessage());
                        return;
                    }
                }
            }
            try {
                yaml.set(path + ".inventory", ItemCodec.encode(data.storage()));
            } catch (IOException exception) {
                plugin.getLogger().severe("Не удалось сериализовать склад города " + data.townId() + ": " + exception.getMessage());
                return;
            }
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить town-data.yml: " + exception.getMessage());
        }
    }
}
