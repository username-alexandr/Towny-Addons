package ru.neverland.mintcamps.data;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcamps.model.BlockPos;
import ru.neverland.mintcamps.model.BlockSnapshot;
import ru.neverland.mintcamps.model.Camp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CampRepository {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Camp> camps = new LinkedHashMap<>();
    private final Map<UUID, Map<Long, Set<UUID>>> chunkIndex = new HashMap<>();
    private boolean dirty;

    public CampRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "camps.yml");
    }

    public void load() {
        camps.clear();
        chunkIndex.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("camps");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                String path = "camps." + key + ".";
                UUID worldId = UUID.fromString(yaml.getString(path + "world-id"));
                Camp camp = new Camp(owner, yaml.getString(path + "owner-name", key), worldId,
                        yaml.getString(path + "world-name", "world"), BlockPos.parse(yaml.getString(path + "anchor")),
                        BlockFace.valueOf(yaml.getString(path + "facing", "NORTH").toUpperCase(Locale.ROOT)),
                        yaml.getInt(path + "level", 1), yaml.getString(path + "style", "plains"),
                        yaml.getLong(path + "created-at"), yaml.getLong(path + "burn-until"),
                        yaml.getBoolean(path + "mobs-disabled", true), yaml.getInt(path + "stash-size", 9));
                ConfigurationSection trust = yaml.getConfigurationSection(path + "trusted");
                if (trust != null) {
                    for (String trustedId : trust.getKeys(false)) {
                        camp.trusted().put(UUID.fromString(trustedId), trust.getString(trustedId, trustedId));
                    }
                }
                camp.stash(readItems(yaml.getList(path + "stash", Collections.emptyList())).toArray(ItemStack[]::new),
                        yaml.getInt(path + "stash-size", 9));
                String stashPos = yaml.getString(path + "stash-block", "");
                if (!stashPos.isBlank()) camp.stashBlock(BlockPos.parse(stashPos));
                String hologram = yaml.getString(path + "hologram-id", "");
                if (!hologram.isBlank()) camp.hologramId(UUID.fromString(hologram));
                for (Map<?, ?> entry : yaml.getMapList(path + "snapshots")) {
                    BlockPos position = BlockPos.parse(String.valueOf(entry.get("pos")));
                    Material material = Material.matchMaterial(String.valueOf(entry.get("material")));
                    if (material == null) continue;
                    String data = String.valueOf(entry.get("data"));
                    List<ItemStack> inventory = readItems(entry.get("inventory") instanceof List<?> list ? list : List.of());
                    camp.snapshots().put(position, new BlockSnapshot(material, data, inventory));
                }
                for (String value : yaml.getStringList(path + "placed-blocks")) camp.placedBlocks().add(BlockPos.parse(value));
                camps.put(owner, camp);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Не удалось загрузить лагерь " + key + ": " + exception.getMessage());
            }
        }
        rebuildIndex();
        dirty = false;
        plugin.getLogger().info("Загружено активных лагерей: " + camps.size() + ".");
    }

    public Collection<Camp> all() {
        return Collections.unmodifiableCollection(camps.values());
    }

    public Optional<Camp> get(UUID ownerId) {
        return Optional.ofNullable(camps.get(ownerId));
    }

    public Optional<Camp> getByOwnerName(String name) {
        return camps.values().stream().filter(camp -> camp.ownerName().equalsIgnoreCase(name)).findFirst();
    }

    public void put(Camp camp) {
        Camp old = camps.put(camp.ownerId(), camp);
        if (old != null) unindex(old);
        index(camp);
        dirty = true;
    }

    public void remove(Camp camp) {
        camps.remove(camp.ownerId());
        unindex(camp);
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    public Optional<Camp> findAt(Location location) {
        if (location.getWorld() == null) return Optional.empty();
        Map<Long, Set<UUID>> worldIndex = chunkIndex.get(location.getWorld().getUID());
        if (worldIndex == null) return Optional.empty();
        Set<UUID> ids = worldIndex.get(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (ids == null) return Optional.empty();
        for (UUID id : ids) {
            Camp camp = camps.get(id);
            if (camp == null) continue;
            int radius = radius(camp.level());
            double dx = location.getX() - (camp.anchor().x() + 0.5D);
            double dz = location.getZ() - (camp.anchor().z() + 0.5D);
            if (dx * dx + dz * dz <= radius * radius && Math.abs(location.getY() - camp.anchor().y()) <= radius + 10) {
                return Optional.of(camp);
            }
        }
        return Optional.empty();
    }

    public boolean isTooClose(Location location, int minimumDistance) {
        if (location.getWorld() == null) return true;
        double squared = (double) minimumDistance * minimumDistance;
        for (Camp camp : camps.values()) {
            if (!camp.worldId().equals(location.getWorld().getUID())) continue;
            double dx = camp.anchor().x() - location.getBlockX();
            double dz = camp.anchor().z() - location.getBlockZ();
            if (dx * dx + dz * dz < squared) return true;
        }
        return false;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Camp camp : camps.values()) {
            String path = "camps." + camp.ownerId() + ".";
            yaml.set(path + "owner-name", camp.ownerName());
            yaml.set(path + "world-id", camp.worldId().toString());
            yaml.set(path + "world-name", camp.worldName());
            yaml.set(path + "anchor", camp.anchor().key());
            yaml.set(path + "facing", camp.facing().name());
            yaml.set(path + "level", camp.level());
            yaml.set(path + "style", camp.styleId());
            yaml.set(path + "created-at", camp.createdAt());
            yaml.set(path + "burn-until", camp.burnUntil());
            yaml.set(path + "mobs-disabled", camp.mobsDisabled());
            yaml.set(path + "stash-size", camp.stash().length);
            // Arrays.asList deliberately permits null slots; List.of does not.
            yaml.set(path + "stash", Arrays.asList(camp.stash()));
            yaml.set(path + "stash-block", camp.stashBlock() == null ? null : camp.stashBlock().key());
            yaml.set(path + "hologram-id", camp.hologramId() == null ? null : camp.hologramId().toString());
            for (Map.Entry<UUID, String> trusted : camp.trusted().entrySet()) {
                yaml.set(path + "trusted." + trusted.getKey(), trusted.getValue());
            }
            List<Map<String, Object>> snapshots = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockSnapshot> entry : camp.snapshots().entrySet()) {
                Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("pos", entry.getKey().key());
                serialized.put("material", entry.getValue().material().name());
                serialized.put("data", entry.getValue().blockData());
                if (!entry.getValue().inventory().isEmpty()) serialized.put("inventory", entry.getValue().inventory());
                snapshots.add(serialized);
            }
            yaml.set(path + "snapshots", snapshots);
            yaml.set(path + "placed-blocks", camp.placedBlocks().stream().map(BlockPos::key).toList());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку данных NeverLandTownyCamps.");
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить camps.yml: " + exception.getMessage());
        }
    }

    private List<ItemStack> readItems(List<?> values) {
        List<ItemStack> items = new ArrayList<>();
        for (Object value : values) if (value instanceof ItemStack item) items.add(item.clone()); else items.add(null);
        return items;
    }

    private int radius(int level) {
        return Math.max(1, plugin.getConfig().getInt("settings.levels." + level + ".radius", 6 + (level - 1) * 3));
    }

    private void rebuildIndex() {
        chunkIndex.clear();
        for (Camp camp : camps.values()) index(camp);
    }

    private void index(Camp camp) {
        int radius = radius(camp.level());
        int minX = (camp.anchor().x() - radius) >> 4;
        int maxX = (camp.anchor().x() + radius) >> 4;
        int minZ = (camp.anchor().z() - radius) >> 4;
        int maxZ = (camp.anchor().z() + radius) >> 4;
        Map<Long, Set<UUID>> worldIndex = chunkIndex.computeIfAbsent(camp.worldId(), ignored -> new HashMap<>());
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            worldIndex.computeIfAbsent(chunkKey(x, z), ignored -> new LinkedHashSet<>()).add(camp.ownerId());
        }
    }

    private void unindex(Camp camp) {
        Map<Long, Set<UUID>> worldIndex = chunkIndex.get(camp.worldId());
        if (worldIndex == null) return;
        for (Set<UUID> ids : worldIndex.values()) ids.remove(camp.ownerId());
        worldIndex.values().removeIf(Set::isEmpty);
        if (worldIndex.isEmpty()) chunkIndex.remove(camp.worldId());
    }

    private long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
