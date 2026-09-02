package ru.neverland.mintevents.service;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import ru.neverland.mintevents.api.EventSnapshot;
import ru.neverland.mintevents.api.MintTownyEventsApi;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.EventDefinition;
import ru.neverland.mintevents.model.EventMode;
import ru.neverland.mintevents.model.HistoryEntry;
import ru.neverland.mintevents.util.ColorUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class EventService implements MintTownyEventsApi {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final EventRegistry registry;
    private final EventRepository repository;
    private final DevelopmentService development;
    private final MessageService messages;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> raidFailureWarnings = new HashMap<>();
    private final NamespacedKey raidMobKey;
    private final NamespacedKey legacyRaidMobKey;
    private BukkitTask tickTask;
    private BukkitTask randomTask;
    private BukkitTask saveTask;
    private long effectsTick;

    public EventService(JavaPlugin plugin, TownyHook towny, EventRegistry registry,
                        EventRepository repository, DevelopmentService development,
                        MessageService messages) {
        this.plugin = plugin;
        this.towny = towny;
        this.registry = registry;
        this.repository = repository;
        this.development = development;
        this.messages = messages;
        this.raidMobKey = new NamespacedKey(plugin, "raid_town");
        this.legacyRaidMobKey = NamespacedKey.fromString("minttownyevents:raid_town");
    }

    public void start() {
        stopTasks();
        long tickPeriod = Math.max(1, plugin.getConfig().getLong("runtime.tick-seconds", 1)) * 20;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, tickPeriod);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, repository::saveIfDirty, 1200, 1200);
        if (plugin.getConfig().getBoolean("scheduler.enabled", true)) {
            long delay = Math.max(1, plugin.getConfig().getLong("scheduler.startup-delay-minutes", 10)) * 1200;
            long period = Math.max(1, plugin.getConfig().getLong("scheduler.check-interval-minutes", 5)) * 1200;
            randomTask = Bukkit.getScheduler().runTaskTimer(plugin, this::randomCheck, delay, period);
        }
    }

    public void shutdown() {
        stopTasks();
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
        repository.save();
    }

    private void stopTasks() {
        if (tickTask != null) tickTask.cancel();
        if (randomTask != null) randomTask.cancel();
        if (saveTask != null) saveTask.cancel();
        tickTask = randomTask = saveTask = null;
    }

    public boolean startEvent(Town town, EventDefinition definition) {
        if (town == null || definition == null || repository.active(town.getUUID()) != null) return false;
        long now = System.currentTimeMillis();
        int residents = Math.max(1, town.getResidents().size());
        int goal = definition.baseGoal() + definition.goalPerResident() * residents;
        double protection = development.protection(town.getUUID(), definition);
        ActiveEvent active = new ActiveEvent(town.getUUID(), definition.id(), now,
                now + definition.durationSeconds() * 1000, 0, goal, protection, 0);
        repository.put(active);
        saveNow();
        announce(town, "event-start-town", definition, active);
        updateBossBar(town, definition, active);
        if (definition.mode() == EventMode.RAID) {
            Location anchor = raidAnchor(town);
            if (anchor != null) maybeRaidWave(town, anchor, active, 1.0 - active.protection(), now, false);
        }
        return true;
    }

    public boolean resolve(Town town, boolean success) {
        if (town == null) return false;
        ActiveEvent active = repository.active(town.getUUID());
        if (active == null) return false;
        resolveInternal(town, active, success);
        return true;
    }

    private void resolveInternal(Town town, ActiveEvent active, boolean success) {
        EventDefinition definition = registry.get(active.eventId());
        long now = System.currentTimeMillis();
        repository.complete(active, success,
                plugin.getConfig().getInt("runtime.history-limit-per-town", 20), now);
        cleanupRaidMobs(active.townId());
        raidFailureWarnings.remove(active.townId());
        BossBar bar = bars.remove(active.townId());
        if (bar != null) bar.removeAll();
        if (definition != null) {
            announce(town, success ? "event-success-town" : "event-failed-town", definition, active);
            runCommands(town, definition, active, success ? definition.successCommands() : definition.failureCommands());
        }
        saveNow();
    }

    public int contribute(Town town, int points) {
        ActiveEvent event = town == null ? null : repository.active(town.getUUID());
        if (event == null) return -1;
        int value = event.addProgress(points);
        repository.changed();
        EventDefinition definition = registry.get(event.eventId());
        if (definition != null) updateBossBar(town, definition, event);
        if (event.completed()) {
            resolveInternal(town, event, true);
        } else saveNow();
        return value;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long refresh = Math.max(1, plugin.getConfig().getLong("runtime.effects-refresh-seconds", 8)) * 1000;
        boolean applyEffects = now - effectsTick >= refresh;
        if (applyEffects) effectsTick = now;
        for (ActiveEvent active : repository.active().values()) {
            Town town = town(active.townId());
            EventDefinition definition = registry.get(active.eventId());
            if (town == null || definition == null) continue;
            active.protection(development.protection(active.townId(), definition));
            if (active.completed()) {
                resolveInternal(town, active, true);
            } else if (now >= active.endsAt()) {
                resolveInternal(town, active, false);
            } else {
                updateBossBar(town, definition, active);
                if (applyEffects) applyGameplay(town, definition, active, now);
            }
        }
    }

    private void applyGameplay(Town town, EventDefinition definition, ActiveEvent active, long now) {
        double severity = 1.0 - active.protection();
        if (definition.mode() == EventMode.RAID) {
            Location anchor = raidAnchor(town);
            if (anchor != null) maybeRaidWave(town, anchor, active, severity, now, false);
            return;
        }
        for (Player player : onlineResidents(town)) {
            Town current = towny.townAt(player.getLocation());
            if (current == null || !current.getUUID().equals(town.getUUID())) continue;
            int duration = (int) Math.max(60, plugin.getConfig().getLong("runtime.effects-refresh-seconds", 8) * 30);
            switch (definition.mode()) {
                case EPIDEMIC -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration,
                            severity >= 0.65 ? 1 : 0, true, false, true));
                    if (severity >= 0.45) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            duration, 0, true, false, true));
                }
                case FIRE -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration,
                            severity >= 0.7 ? 1 : 0, true, false, true));
                    fireParticles(player);
                }
                case FESTIVAL -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, duration, 0, true, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, true, true, true));
                }
                case RAID -> { }
                case DROUGHT -> { }
            }
        }
    }

    private void fireParticles(Player player) {
        int count = Math.max(1, plugin.getConfig().getInt("gameplay.fire-particle-count", 12));
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0),
                count, 5, 1.5, 5, 0.01);
    }

    private int maybeRaidWave(Town town, Location anchor, ActiveEvent event, double severity, long now,
                              boolean ignoreInterval) {
        long interval = Math.max(10, plugin.getConfig().getLong("gameplay.raid-wave-interval-seconds", 60)) * 1000;
        if (!ignoreInterval && now - event.lastRaidWave() < interval) return 0;
        int alive = raidMobCount(town.getUUID());
        int cap = Math.max(1, plugin.getConfig().getInt("gameplay.raid-max-alive-mobs-per-town", 18));
        int base = Math.max(1, plugin.getConfig().getInt("gameplay.raid-base-mobs-per-wave", 5));
        int amount = Math.min(cap - alive, Math.max(1, (int) Math.ceil(base * severity)));
        if (amount <= 0) {
            event.lastRaidWave(now);
            repository.changed();
            return 0;
        }
        Location spawnLocation = findRaidSpawn(town, anchor, false);
        if (spawnLocation == null && plugin.getConfig().getBoolean("gameplay.raid-allow-liquid-fallback", true)) {
            spawnLocation = findRaidSpawn(town, anchor, true);
        }
        List<String> rawTypes = plugin.getConfig().getStringList("gameplay.raid-mobs");
        int spawned = 0;
        int rejected = 0;
        int invalidTypes = 0;
        if (spawnLocation != null) {
            for (int index = 0; index < amount; index++) {
                RaidSpawnResult result = spawnRaidMob(town, spawnLocation, rawTypes);
                if (result == RaidSpawnResult.SPAWNED) spawned++;
                else if (result == RaidSpawnResult.INVALID_TYPE) invalidTypes++;
                else rejected++;
            }
        }
        if (spawned > 0) {
            raidFailureWarnings.remove(town.getUUID());
            event.lastRaidWave(now);
            if (plugin.getConfig().getBoolean("gameplay.raid-announce-wave", true)) {
                String text = messages.formatText(messages.raw("raid-wave-town"),
                        Map.of("town", town.getName(), "count", spawned), false);
                for (Player player : onlineResidents(town)) player.sendMessage(text);
            }
            if (plugin.getConfig().getBoolean("gameplay.raid-log-spawns", true)) {
                plugin.getLogger().info("Набег: город " + town.getName() + ", создано разбойников: "
                        + spawned + "/" + amount + ".");
            }
        } else {
            long retry = Math.max(1, plugin.getConfig().getLong("gameplay.raid-failed-retry-seconds", 10)) * 1000;
            event.lastRaidWave(now - interval + retry);
            logRaidFailure(town, spawnLocation, rejected, invalidTypes, retry, now);
        }
        repository.changed();
        return spawned;
    }

    private RaidSpawnResult spawnRaidMob(Town town, Location location, List<String> rawTypes) {
        if (rawTypes.isEmpty()) return RaidSpawnResult.INVALID_TYPE;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        EntityType type;
        try { type = EntityType.valueOf(rawTypes.get(random.nextInt(rawTypes.size())).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { type = EntityType.ZOMBIE; }
        if (!type.isAlive() || !type.isSpawnable()) return RaidSpawnResult.INVALID_TYPE;
        try {
            Location actual = location.clone().add(random.nextDouble(-0.3, 0.3), 0, random.nextDouble(-0.3, 0.3));
            Entity spawned = actual.getWorld().spawnEntity(actual, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (!(spawned instanceof LivingEntity living)) {
                if (spawned != null) spawned.remove();
                return RaidSpawnResult.REJECTED;
            }
            if (!living.isValid() || living.isDead()) {
                living.remove();
                return RaidSpawnResult.REJECTED;
            }
            living.getPersistentDataContainer().set(raidMobKey, PersistentDataType.STRING, town.getUUID().toString());
            living.setCustomName(ColorUtil.color("&#FF5E6CРазбойник"));
            living.setCustomNameVisible(false);
            if (living instanceof Mob mob) {
                Player target = nearestTownPlayer(town, actual);
                if (target != null) mob.setTarget(target);
            }
            return RaidSpawnResult.SPAWNED;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Набег: EntityType " + type + " не создан: " + exception.getMessage());
            return RaidSpawnResult.REJECTED;
        }
    }

    private Location findRaidSpawn(Town town, Location anchor, boolean allowLiquidFloor) {
        World world = anchor.getWorld();
        if (world == null) return null;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int minRadius = Math.max(1, plugin.getConfig().getInt("gameplay.raid-spawn-min-radius", 4));
        int maxRadius = Math.max(minRadius, plugin.getConfig().getInt("gameplay.raid-spawn-max-radius", 18));
        int attempts = Math.max(8, plugin.getConfig().getInt("gameplay.raid-spawn-attempts", 32));
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            int radius = random.nextInt(minRadius, maxRadius + 1);
            int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            Location location = safeRaidLocation(world, x, z, town, allowLiquidFloor);
            if (location != null) return location;
        }

        List<TownBlock> blocks = new ArrayList<>(town.getTownBlocks());
        blocks.sort(Comparator.comparingDouble(block -> townBlockDistance(block, anchor)));
        int blockAttempts = Math.max(1, plugin.getConfig().getInt("gameplay.raid-townblock-attempts", 6));
        int maxBlocks = Math.max(1, plugin.getConfig().getInt("gameplay.raid-max-townblocks-scanned", 64));
        int scanned = 0;
        for (TownBlock townBlock : blocks) {
            if (scanned++ >= maxBlocks) break;
            World blockWorld = townBlock.getWorldCoord().getBukkitWorld();
            if (blockWorld == null || !blockWorld.equals(world)) continue;
            BoundingBox box = townBlock.getWorldCoord().getBoundingBox();
            int minX = (int) Math.floor(box.getMinX()) + 1;
            int maxX = (int) Math.ceil(box.getMaxX()) - 2;
            int minZ = (int) Math.floor(box.getMinZ()) + 1;
            int maxZ = (int) Math.ceil(box.getMaxZ()) - 2;
            for (RaidSpawnPlanner.Column probe : RaidSpawnPlanner.blockProbes(minX, maxX, minZ, maxZ)) {
                Location location = safeRaidLocation(world, probe.x(), probe.z(), town, allowLiquidFloor);
                if (location != null) return location;
            }
            for (int attempt = 0; attempt < blockAttempts; attempt++) {
                int x = maxX <= minX ? minX : random.nextInt(minX, maxX + 1);
                int z = maxZ <= minZ ? minZ : random.nextInt(minZ, maxZ + 1);
                Location location = safeRaidLocation(world, x, z, town, allowLiquidFloor);
                if (location != null) return location;
            }
        }
        return null;
    }

    private Location safeRaidLocation(World world, int x, int z, Town town, boolean allowLiquidFloor) {
        if (!belongsToTown(town, world, x, z)) return null;
        Location center = new Location(world, x + 0.5, world.getMinHeight() + 1, z + 0.5);
        if (!world.getWorldBorder().isInside(center)) return null;
        int scanDown = Math.max(4, plugin.getConfig().getInt("gameplay.raid-vertical-scan-down", 16));
        int scanUp = Math.max(2, plugin.getConfig().getInt("gameplay.raid-vertical-scan-up", 6));
        Set<Integer> heights = new HashSet<>();
        heights.add(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
        heights.add(world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
        heights.add(world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR));
        for (int highest : heights) {
            for (int y : RaidSpawnPlanner.verticalCandidates(highest, world.getMinHeight(),
                    world.getMaxHeight(), scanDown, scanUp)) {
                Block ground = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                boolean floor = (!ground.isLiquid() && ground.getType().isSolid())
                        || (allowLiquidFloor && ground.isLiquid());
                if (floor && feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    private boolean belongsToTown(Town town, World world, int x, int z) {
        for (TownBlock block : town.getTownBlocks()) {
            World blockWorld = block.getWorldCoord().getBukkitWorld();
            if (blockWorld != null && blockWorld.equals(world)
                    && block.getWorldCoord().containsCoordinate(x, z)) return true;
        }
        return false;
    }

    private void logRaidFailure(Town town, Location spawnLocation, int rejected, int invalidTypes,
                                long retry, long now) {
        long interval = Math.max(10, plugin.getConfig().getLong("gameplay.raid-warning-interval-seconds", 60)) * 1000;
        long previous = raidFailureWarnings.getOrDefault(town.getUUID(), 0L);
        if (now - previous < interval) return;
        raidFailureWarnings.put(town.getUUID(), now);
        String reason;
        if (spawnLocation == null) {
            reason = "не найдена поверхность в принадлежащих городу участках";
        } else if (invalidTypes > 0 && rejected == 0) {
            reason = "в config.yml указаны неподдерживаемые типы мобов";
        } else {
            reason = "точка найдена, но создание мобов отклонено сервером, сложностью мира или защитным плагином";
        }
        plugin.getLogger().warning("Набег: город " + town.getName() + " — " + reason
                + "; следующая попытка через " + (retry / 1000) + " сек. Повтор предупреждения не чаще "
                + (interval / 1000) + " сек.");
    }

    private enum RaidSpawnResult { SPAWNED, REJECTED, INVALID_TYPE }

    private double townBlockDistance(TownBlock block, Location anchor) {
        BoundingBox box = block.getWorldCoord().getBoundingBox();
        double dx = (box.getMinX() + box.getMaxX()) * 0.5 - anchor.getX();
        double dz = (box.getMinZ() + box.getMaxZ()) * 0.5 - anchor.getZ();
        return dx * dx + dz * dz;
    }

    private Location raidAnchor(Town town) {
        for (Player player : onlineResidents(town)) {
            Town current = towny.townAt(player.getLocation());
            if (current != null && current.getUUID().equals(town.getUUID())) return player.getLocation();
        }
        Location spawn = town.getSpawnOrNull();
        if (spawn != null && spawn.getWorld() != null) return spawn;
        TownBlock home = town.getHomeBlockOrNull();
        if (home == null && !town.getTownBlocks().isEmpty()) home = town.getTownBlocks().iterator().next();
        if (home == null) return null;
        BoundingBox box = home.getWorldCoord().getBoundingBox();
        World world = home.getWorldCoord().getBukkitWorld();
        return world == null ? null : new Location(world, (box.getMinX() + box.getMaxX()) * 0.5,
                world.getHighestBlockYAt((int) box.getCenterX(), (int) box.getCenterZ()) + 1,
                (box.getMinZ() + box.getMaxZ()) * 0.5);
    }

    private Player nearestTownPlayer(Town town, Location location) {
        Player nearest = null;
        double distance = Double.MAX_VALUE;
        for (Player player : onlineResidents(town)) {
            if (!player.getWorld().equals(location.getWorld())) continue;
            double current = player.getLocation().distanceSquared(location);
            if (current > 4096) continue;
            if (current < distance) { distance = current; nearest = player; }
        }
        return nearest;
    }

    public int forceRaidWave(Town town) {
        ActiveEvent event = town == null ? null : repository.active(town.getUUID());
        EventDefinition definition = event == null ? null : registry.get(event.eventId());
        if (event == null || definition == null || definition.mode() != EventMode.RAID) return -1;
        Location anchor = raidAnchor(town);
        return anchor == null ? 0 : maybeRaidWave(town, anchor, event,
                1.0 - event.protection(), System.currentTimeMillis(), true);
    }

    private int raidMobCount(UUID townId) {
        int count = 0;
        String expected = townId.toString();
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (expected.equals(raidOwner(entity))) count++;
            }
        }
        return count;
    }

    private void cleanupRaidMobs(UUID townId) {
        String expected = townId.toString();
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : new ArrayList<>(world.getLivingEntities())) {
                if (expected.equals(raidOwner(entity))) {
                    entity.remove();
                }
            }
        }
    }

    private void randomCheck() {
        if (repository.active().size() >= Math.max(1, plugin.getConfig().getInt("scheduler.max-active-events", 3))) return;
        if (ThreadLocalRandom.current().nextDouble() > plugin.getConfig().getDouble("scheduler.chance-per-check", 0.35)) return;
        long cooldown = Math.max(0, plugin.getConfig().getLong("scheduler.town-cooldown-hours", 12)) * 3_600_000;
        boolean requireOnline = plugin.getConfig().getBoolean("scheduler.require-online-resident", true);
        long now = System.currentTimeMillis();
        List<Town> eligible = new ArrayList<>();
        for (Town town : towny.towns()) {
            if (repository.active(town.getUUID()) != null) continue;
            if (now - repository.lastEventAt(town.getUUID()) < cooldown) continue;
            if (requireOnline && onlineResidents(town).isEmpty()) continue;
            eligible.add(town);
        }
        List<EventDefinition> definitions = new ArrayList<>(registry.all());
        if (eligible.isEmpty() || definitions.isEmpty()) return;
        Collections.shuffle(eligible);
        Collections.shuffle(definitions);
        startEvent(eligible.get(0), definitions.get(0));
    }

    private void updateBossBar(Town town, EventDefinition definition, ActiveEvent event) {
        if (!plugin.getConfig().getBoolean("runtime.bossbar", true)) return;
        BossBar bar = bars.computeIfAbsent(town.getUUID(), ignored -> Bukkit.createBossBar("",
                definition.bossBarColor(), BarStyle.SEGMENTED_10, new BarFlag[0]));
        bar.setColor(definition.bossBarColor());
        bar.setProgress(event.progressRatio());
        bar.setTitle(ColorUtil.color(definition.name() + " &8— &f" + event.progress() + "/" + event.goal()));
        bar.removeAll();
        for (Player player : onlineResidents(town)) bar.addPlayer(player);
    }

    private void announce(Town town, String key, EventDefinition definition, ActiveEvent active) {
        if (!plugin.getConfig().getBoolean("runtime.announce-to-town", true)) return;
        Map<String, Object> placeholders = placeholders(town, definition, active);
        String text = messages.formatText(messages.raw(key), placeholders, false);
        for (Player player : onlineResidents(town)) player.sendMessage(text);
    }

    private void runCommands(Town town, EventDefinition definition, ActiveEvent event, List<String> commands) {
        Map<String, Object> placeholders = placeholders(town, definition, event);
        for (String raw : commands) {
            String command = raw;
            for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                command = command.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
        }
    }

    private Map<String, Object> placeholders(Town town, EventDefinition definition, ActiveEvent event) {
        return Map.of("town", town.getName(), "event", ColorUtil.strip(definition.name()),
                "description", definition.description(), "progress", event.progress(), "goal", event.goal(),
                "protection", Math.round(event.protection() * 100));
    }

    public Collection<Player> onlineResidents(Town town) {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Town playerTown = towny.town(player);
            if (playerTown != null && playerTown.getUUID().equals(town.getUUID())) result.add(player);
        }
        return result;
    }

    public Town town(UUID id) {
        for (Town town : towny.towns()) if (town.getUUID().equals(id)) return town;
        return null;
    }

    public ActiveEvent active(UUID townId) { return repository.active(townId); }
    public EventDefinition definition(ActiveEvent event) { return event == null ? null : registry.get(event.eventId()); }
    public List<HistoryEntry> history(UUID townId) { return repository.history(townId); }
    public NamespacedKey raidMobKey() { return raidMobKey; }
    public boolean isRaidMob(Entity entity) { return raidOwner(entity) != null; }
    private String raidOwner(Entity entity) { if (entity == null) return null; String value = entity.getPersistentDataContainer().get(raidMobKey, PersistentDataType.STRING); return value != null || legacyRaidMobKey == null ? value : entity.getPersistentDataContainer().get(legacyRaidMobKey, PersistentDataType.STRING); }
    public EventRegistry registry() { return registry; }
    public DevelopmentService development() { return development; }

    public void reloadRuntime() {
        development.clearCache();
        start();
    }

    private void saveNow() {
        if (plugin.getConfig().getBoolean("runtime.save-immediately", true)) repository.save();
    }

    @Override
    public Optional<EventSnapshot> activeEvent(UUID townId) {
        ActiveEvent event = repository.active(townId);
        EventDefinition definition = definition(event);
        if (event == null || definition == null) return Optional.empty();
        return Optional.of(new EventSnapshot(townId, event.eventId(), ColorUtil.strip(definition.name()),
                event.endsAt(), event.progress(), event.goal(), event.protection()));
    }

    @Override
    public boolean addProgress(UUID townId, int points, String source) {
        Town town = town(townId);
        return town != null && points > 0 && contribute(town, points) >= 0;
    }

    @Override
    public double protection(UUID townId) {
        ActiveEvent event = repository.active(townId);
        return event == null ? 0 : event.protection();
    }
}
