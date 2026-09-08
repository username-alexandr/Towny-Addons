package ru.neverland.mintexpeditions.service;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import ru.neverland.mintexpeditions.integration.TownyHook;
import ru.neverland.mintexpeditions.model.ActiveExpedition;
import ru.neverland.mintexpeditions.model.BlockPos;
import ru.neverland.mintexpeditions.model.ExpeditionDefinition;
import ru.neverland.mintexpeditions.model.ExpeditionStatus;
import ru.neverland.mintexpeditions.model.ExpeditionType;
import ru.neverland.mintexpeditions.model.SitePlan;
import ru.neverland.mintexpeditions.model.SiteSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class SiteService {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final ExpeditionRepository repository;
    private final SiteGenerator generator = new SiteGenerator();
    private final NamespacedKey mobKey;
    private final NamespacedKey legacyMobKey;

    public SiteService(JavaPlugin plugin, TownyHook towny, ExpeditionRepository repository) {
        this.plugin = plugin;
        this.towny = towny;
        this.repository = repository;
        mobKey = new NamespacedKey(plugin, "expedition_id");
        legacyMobKey = NamespacedKey.fromString("minttownyexpeditions:expedition_id");
    }

    public void findSite(World world, Location camp, ExpeditionDefinition definition, Consumer<BlockPos> callback) {
        List<int[]> candidates = new ArrayList<>();
        Random random = new Random();
        int tries = plugin.getConfig().getInt("expeditions.search-attempts", 12);
        for (int index = 0; index < tries; index++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = random.nextInt(Math.max(1,
                    definition.maxDistance() - definition.minDistance() + 1)) + definition.minDistance();
            candidates.add(new int[]{
                    camp.getBlockX() + (int) (Math.cos(angle) * distance),
                    camp.getBlockZ() + (int) (Math.sin(angle) * distance)
            });
        }
        find(world, definition, candidates, 0, callback);
    }

    private void find(World world, ExpeditionDefinition definition, List<int[]> candidates,
                      int index, Consumer<BlockPos> callback) {
        if (index >= candidates.size()) {
            callback.accept(null);
            return;
        }
        int[] candidate = candidates.get(index);
        CompletableFuture<org.bukkit.Chunk> future =
                world.getChunkAtAsync(candidate[0] >> 4, candidate[1] >> 4, true);
        future.whenComplete((chunk, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                find(world, definition, candidates, index + 1, callback);
                return;
            }
            int y;
            if (definition.type() == ExpeditionType.SHIPWRECK) {
                y = world.getHighestBlockYAt(candidate[0], candidate[1], HeightMap.OCEAN_FLOOR) + 1;
                if (world.getBlockAt(candidate[0], y, candidate[1]).getType() != Material.WATER) {
                    find(world, definition, candidates, index + 1, callback);
                    return;
                }
            } else {
                y = world.getHighestBlockYAt(candidate[0], candidate[1],
                        HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            }
            Location location = new Location(world, candidate[0], y, candidate[1]);
            if (plugin.getConfig().getBoolean("expeditions.require-wilderness", true)
                    && !towny.wilderness(location)) {
                find(world, definition, candidates, index + 1, callback);
                return;
            }
            int minimum = plugin.getConfig().getInt("expeditions.minimum-distance-between-sites", 96);
            for (ActiveExpedition expedition : repository.active()) {
                if (!expedition.worldId().equals(world.getUID())) continue;
                int dx = expedition.siteAnchor().x() - candidate[0];
                int dz = expedition.siteAnchor().z() - candidate[1];
                if (dx * dx + dz * dz < minimum * minimum) {
                    find(world, definition, candidates, index + 1, callback);
                    return;
                }
            }
            callback.accept(new BlockPos(candidate[0], y, candidate[1]));
        }));
    }

    public boolean build(ActiveExpedition expedition, ExpeditionDefinition definition) {
        World world = expedition.world();
        if (world == null) return false;
        SitePlan plan = generator.generate(definition, expedition.siteAnchor());
        try {
            for (BlockPos position : plan.blocks().keySet()) {
                expedition.snapshots().put(position, snapshot(position.location(world).getBlock()));
            }
            expedition.objectives().addAll(plan.objectives());
            repository.put(expedition);
            repository.save();
            for (Map.Entry<BlockPos, Material> entry : plan.blocks().entrySet()) {
                entry.getKey().location(world).getBlock().setType(entry.getValue(), false);
            }
            spawn(expedition, definition, definition.mobCount());
            repository.changed();
            repository.save();
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Ошибка генерации " + expedition.id() + ": " + exception.getMessage());
            restore(expedition, () -> {});
            return false;
        }
    }

    private SiteSnapshot snapshot(Block block) {
        List<ItemStack> items = new ArrayList<>();
        BlockState state = block.getState();
        if (state instanceof InventoryHolder holder) {
            for (ItemStack item : holder.getInventory().getContents()) {
                items.add(item == null ? new ItemStack(Material.AIR) : item.clone());
            }
        }
        return new SiteSnapshot(block.getType(), block.getBlockData().getAsString(), items);
    }

    public void respawn(ActiveExpedition expedition, ExpeditionDefinition definition, int count) {
        if (count <= 0 || expedition.status() != ExpeditionStatus.ACTIVE
                || repository.get(expedition.id()) == null) return;
        int spawned = spawn(expedition, definition, count);
        if (spawned > 0) {
            repository.changed();
            repository.saveIfDirty();
        }
    }

    private int spawn(ActiveExpedition expedition, ExpeditionDefinition definition, int count) {
        World world = expedition.world();
        Location center = expedition.siteLocation();
        if (world == null || center == null) return 0;

        double radius = Math.max(2.0, plugin.getConfig().getDouble("combat.mob-spawn-radius", 7.0));
        Set<BlockPos> occupied = new HashSet<>();
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2 * index / Math.max(1, count);
            Location desired = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            Location safe = safeSpawnLocation(world, desired, center, definition.mob(), occupied);
            if (safe == null) {
                plugin.getLogger().warning("Экспедиция " + expedition.id()
                        + ": не найдена безопасная точка для моба " + definition.mob() + ".");
                continue;
            }
            Entity entity = world.spawnEntity(safe, definition.mob());
            if (entity instanceof LivingEntity living) {
                living.getPersistentDataContainer().set(
                        mobKey, PersistentDataType.STRING, expedition.id().toString());
                living.setFallDistance(0);
                expedition.spawnedMobs().add(living.getUniqueId());
                spawned++;
            } else {
                entity.remove();
            }
        }
        return spawned;
    }

    private Location safeSpawnLocation(World world, Location desired, Location center,
                                       EntityType type, Set<BlockPos> occupied) {
        int radius = Math.max(2, plugin.getConfig().getInt("combat.spawn-search-radius", 12));
        List<int[]> offsets = offsets(radius);
        for (Location origin : List.of(desired, center)) {
            for (int[] offset : offsets) {
                int x = origin.getBlockX() + offset[0];
                int z = origin.getBlockZ() + offset[1];
                Location candidate = type == EntityType.DROWNED
                        ? safeWaterColumn(world, x, origin.getBlockY(), z)
                        : null;
                if (candidate == null) candidate = safeLandColumn(world, x, z, clearance(type));
                if (candidate == null || !insideMobArea(center, candidate)) continue;
                BlockPos key = BlockPos.of(candidate);
                if (occupied.add(key)) return candidate;
            }
        }
        return null;
    }

    private Location safeLandColumn(World world, int x, int z, int clearance) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        if (y <= world.getMinHeight() || y + clearance >= world.getMaxHeight()) return null;
        Block floor = world.getBlockAt(x, y - 1, z);
        if (!floor.getType().isSolid()) return null;
        for (int offset = 0; offset < clearance; offset++) {
            Block block = world.getBlockAt(x, y + offset, z);
            if (!block.isPassable() || block.isLiquid()) return null;
        }
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private Location safeWaterColumn(World world, int x, int baseY, int z) {
        int vertical = Math.max(4, plugin.getConfig().getInt("combat.spawn-search-height", 12));
        for (int distance = 0; distance <= vertical; distance++) {
            for (int direction : distance == 0 ? new int[]{1} : new int[]{1, -1}) {
                int y = baseY + distance * direction;
                if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
                if (world.getBlockAt(x, y, z).getType() == Material.WATER
                        && world.getBlockAt(x, y + 1, z).getType() == Material.WATER) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    private int clearance(EntityType type) {
        return type == EntityType.ENDERMAN ? 3 : 2;
    }

    private List<int[]> offsets(int radius) {
        List<int[]> result = new ArrayList<>();
        result.add(new int[]{0, 0});
        for (int ring = 1; ring <= radius; ring++) {
            for (int x = -ring; x <= ring; x++) {
                result.add(new int[]{x, -ring});
                result.add(new int[]{x, ring});
            }
            for (int z = -ring + 1; z < ring; z++) {
                result.add(new int[]{-ring, z});
                result.add(new int[]{ring, z});
            }
        }
        return result;
    }

    public ActiveExpedition expeditionForMob(Entity entity) {
        UUID id = expeditionMob(entity);
        return id == null ? null : repository.get(id);
    }

    public boolean insideMobArea(ActiveExpedition expedition, Location location) {
        Location center = expedition.siteLocation();
        return center != null && insideMobArea(center, location);
    }

    private boolean insideMobArea(Location center, Location location) {
        if (location == null || location.getWorld() == null
                || center.getWorld() == null
                || !location.getWorld().getUID().equals(center.getWorld().getUID())) return false;
        double radius = Math.max(4.0,
                plugin.getConfig().getDouble("combat.mob-containment-radius", 15.0));
        double maximumHeight = Math.max(6.0,
                plugin.getConfig().getDouble("combat.mob-containment-height", 18.0));
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        return dx * dx + dz * dz <= radius * radius
                && Math.abs(location.getY() - center.getY()) <= maximumHeight;
    }

    public boolean relocateMob(Entity entity) {
        ActiveExpedition expedition = expeditionForMob(entity);
        Location center = expedition == null ? null : expedition.siteLocation();
        if (center == null) return false;
        Location desired = center.clone().add(
                plugin.getConfig().getDouble("combat.mob-spawn-radius", 7.0) * 0.5, 0, 0);
        Location safe = safeSpawnLocation(center.getWorld(), desired, center,
                entity.getType(), new HashSet<>());
        if (safe == null) return false;
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);
        return entity.teleport(safe);
    }

    public void guard(ActiveExpedition expedition, ExpeditionDefinition definition) {
        World world = expedition.world();
        if (world == null || !world.isChunkLoaded(
                expedition.siteAnchor().x() >> 4, expedition.siteAnchor().z() >> 4)) return;
        int living = 0;
        boolean changed = false;
        for (UUID mobId : new ArrayList<>(expedition.spawnedMobs())) {
            Entity entity = Bukkit.getEntity(mobId);
            if (!(entity instanceof LivingEntity) || !entity.isValid() || entity.isDead()) {
                expedition.spawnedMobs().remove(mobId);
                changed = true;
                continue;
            }
            if (!expedition.id().equals(expeditionMob(entity))) {
                expedition.spawnedMobs().remove(mobId);
                changed = true;
                continue;
            }
            living++;
            if (!insideMobArea(expedition, entity.getLocation()) && relocateMob(entity)) changed = true;
        }
        int missing = Math.max(0, definition.mobCount() - expedition.kills() - living);
        if (missing > 0 && spawn(expedition, definition, missing) > 0) changed = true;
        if (changed) repository.changed();
    }

    public void resume(ActiveExpedition expedition, ExpeditionDefinition definition) {
        if (!plugin.getConfig().getBoolean("combat.respawn-missing-on-restart", true)
                || expedition.world() == null) return;
        int living = 0;
        for (Entity entity : expedition.world().getEntities()) {
            UUID id = expeditionMob(entity);
            if (!expedition.id().equals(id)) continue;
            expedition.spawnedMobs().add(entity.getUniqueId());
            living++;
            if (!insideMobArea(expedition, entity.getLocation())) relocateMob(entity);
        }
        int remaining = Math.max(0, definition.mobCount() - expedition.kills() - living);
        if (remaining > 0) spawn(expedition, definition, remaining);
        repository.changed();
    }

    public UUID expeditionMob(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        if (raw == null && legacyMobKey != null) {
            raw = entity.getPersistentDataContainer().get(legacyMobKey, PersistentDataType.STRING);
        }
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void restore(ActiveExpedition expedition, Runnable done) {
        World world = expedition.world();
        if (world == null) {
            done.run();
            return;
        }
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (expedition.id().equals(expeditionMob(entity))) entity.remove();
        }
        List<Map.Entry<BlockPos, SiteSnapshot>> entries =
                new ArrayList<>(expedition.snapshots().entrySet());
        int batch = Math.max(1,
                plugin.getConfig().getInt("generation.restore-blocks-per-tick", 80));
        new org.bukkit.scheduler.BukkitRunnable() {
            int index;

            @Override
            public void run() {
                for (int restored = 0; restored < batch && index < entries.size();
                     restored++, index++) {
                    Map.Entry<BlockPos, SiteSnapshot> entry = entries.get(index);
                    Block block = entry.getKey().location(world).getBlock();
                    SiteSnapshot snapshot = entry.getValue();
                    block.setType(snapshot.material(), false);
                    try {
                        block.setBlockData(Bukkit.createBlockData(ru.neverland.localization.MaterialLabels.canonicalBlockData(snapshot.blockData())), false);
                    } catch (IllegalArgumentException ignored) {
                    }
                    if (block.getState() instanceof InventoryHolder holder
                            && !snapshot.inventory().isEmpty()) {
                        ItemStack[] content = new ItemStack[holder.getInventory().getSize()];
                        for (int slot = 0;
                             slot < Math.min(content.length, snapshot.inventory().size()); slot++) {
                            ItemStack item = snapshot.inventory().get(slot);
                            content[slot] = item == null ? null : item.clone();
                        }
                        holder.getInventory().setContents(content);
                    }
                }
                if (index >= entries.size()) {
                    cancel();
                    done.run();
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }
}
