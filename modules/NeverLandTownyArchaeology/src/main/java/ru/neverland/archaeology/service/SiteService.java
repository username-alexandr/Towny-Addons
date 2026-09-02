package ru.neverland.archaeology.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BrushableBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.archaeology.integration.TownyHook;
import ru.neverland.archaeology.model.DigSite;
import ru.neverland.archaeology.model.SiteDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class SiteService implements Listener {
    public record Creation(DigSite site, int blocks) { }
    private final JavaPlugin plugin; private final TownyHook towny; private final ArchaeologyRegistry registry; private final ArchaeologyRepository repository; private final ArtifactService artifacts;
    private final NamespacedKey generatedKey; private final NamespacedKey siteKey; private final NamespacedKey legacyGeneratedKey; private final NamespacedKey legacySiteKey; private BukkitTask scanTask;
    public SiteService(JavaPlugin plugin, TownyHook towny, ArchaeologyRegistry registry, ArchaeologyRepository repository, ArtifactService artifacts) { this.plugin = plugin; this.towny = towny; this.registry = registry; this.repository = repository; this.artifacts = artifacts; generatedKey = new NamespacedKey(plugin, "site_generated"); siteKey = new NamespacedKey(plugin, "site_id"); legacyGeneratedKey = NamespacedKey.fromString("townyarchaeology:site_generated"); legacySiteKey = NamespacedKey.fromString("townyarchaeology:site_id"); }
    public void start() { stop(); long ticks = Math.max(200L, plugin.getConfig().getLong("generation.scan-interval-seconds", 30) * 20L); scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanLoadedSites, ticks, ticks); }
    public void stop() { if (scanTask != null) scanTask.cancel(); scanTask = null; }
    @EventHandler public void onPopulate(ChunkPopulateEvent event) {
        if (!plugin.getConfig().getBoolean("generation.enabled", true)) return; Chunk chunk = event.getChunk(); if (!allowedWorld(chunk.getWorld()) || chunk.getPersistentDataContainer().has(generatedKey, PersistentDataType.BYTE) || (legacyGeneratedKey != null && chunk.getPersistentDataContainer().has(legacyGeneratedKey, PersistentDataType.BYTE))) return; chunk.getPersistentDataContainer().set(generatedKey, PersistentDataType.BYTE, (byte) 1);
        if (ThreadLocalRandom.current().nextDouble() > plugin.getConfig().getDouble("generation.chance-per-new-chunk", 0.025)) return; int x = (chunk.getX() << 4) + 8; int z = (chunk.getZ() << 4) + 8; int y = chunk.getWorld().getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES); String biome = chunk.getWorld().getBlockAt(x, y, z).getBiome().getKey().getKey().toLowerCase(Locale.ROOT);
        List<SiteDefinition> candidates = registry.sites().stream().filter(site -> site.biomes().isEmpty() || site.biomes().contains(biome)).toList(); if (candidates.isEmpty()) return; SiteDefinition selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())); create(chunk.getWorld().getBlockAt(x, y, z).getLocation(), selected, false);
    }
    public Creation create(Location origin, SiteDefinition definition, boolean forced) {
        if (origin.getWorld() == null || definition == null) return new Creation(null, 0); if (!forced && (!farFromTowns(origin) || !farFromSites(origin))) return new Creation(null, 0);
        if (!cleanupCapacity()) return new Creation(null, 0); int attempts = Math.max(definition.maximumBlocks() * 4, plugin.getConfig().getInt("generation.attempts-per-site", 36)); Set<Block> selected = new HashSet<>(); ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts && selected.size() < definition.maximumBlocks(); attempt++) { int dx = random.nextInt(-7, 8); int dz = random.nextInt(-7, 8); Block block = surfaceTarget(origin.getWorld(), origin.getBlockX() + dx, origin.getBlockZ() + dz, definition); if (block == null || (!forced && towny.town(block.getLocation()) != null)) continue; selected.add(block); }
        int wanted = random.nextInt(definition.minimumBlocks(), definition.maximumBlocks() + 1); if (selected.size() < Math.min(definition.minimumBlocks(), wanted)) return new Creation(null, 0); List<Block> blocks = new ArrayList<>(selected); if (blocks.size() > wanted) blocks = blocks.subList(0, wanted);
        UUID id = UUID.randomUUID(); int centerX = blocks.stream().mapToInt(Block::getX).sum() / blocks.size(); int centerY = blocks.stream().mapToInt(Block::getY).sum() / blocks.size(); int centerZ = blocks.stream().mapToInt(Block::getZ).sum() / blocks.size(); DigSite site = new DigSite(id, definition.id(), origin.getWorld().getUID(), origin.getWorld().getName(), centerX, centerY, centerZ, System.currentTimeMillis());
        for (Block block : blocks) { String artifactId = registry.randomArtifact(definition); if (artifactId == null) continue; block.setType(definition.suspiciousBlock(), false); if (!(block.getState() instanceof BrushableBlock state)) continue; state.setItem(artifacts.create(artifactId)); state.getPersistentDataContainer().set(siteKey, PersistentDataType.STRING, id.toString()); state.update(true, false); site.blocks().add(DigSite.blockKey(block.getX(), block.getY(), block.getZ())); }
        if (site.blocks().isEmpty()) return new Creation(null, 0); repository.addSite(site); return new Creation(site, site.blocks().size());
    }
    private Block surfaceTarget(World world, int x, int z, SiteDefinition definition) { int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES); int bottom = Math.max(world.getMinHeight(), top - 36); for (int y = top; y >= bottom; y--) { Block block = world.getBlockAt(x, y, z); if (definition.replaceable().contains(block.getType())) return block; } return null; }
    private boolean allowedWorld(World world) { List<String> whitelist = plugin.getConfig().getStringList("generation.worlds"); if (!whitelist.isEmpty() && whitelist.stream().noneMatch(name -> name.equalsIgnoreCase(world.getName()))) return false; return plugin.getConfig().getStringList("generation.world-blacklist").stream().noneMatch(name -> name.equalsIgnoreCase(world.getName())); }
    private boolean farFromTowns(Location location) { double minimum = Math.max(0, plugin.getConfig().getDouble("generation.minimum-distance-from-town", 96)); double squared = minimum * minimum; for (Town town : towny.towns()) { Location spawn = town.getSpawnOrNull(); if (spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(location.getWorld()) && spawn.distanceSquared(location) < squared) return false; } return towny.town(location) == null; }
    private boolean farFromSites(Location location) { double minimum = Math.max(0, plugin.getConfig().getDouble("generation.minimum-site-distance", 160)); double squared = minimum * minimum; for (DigSite site : repository.sites().values()) if (!site.completed() && site.worldId().equals(location.getWorld().getUID())) { double dx = site.x() - location.getX(); double dz = site.z() - location.getZ(); if (dx * dx + dz * dz < squared) return false; } return true; }
    private boolean cleanupCapacity() { int max = Math.max(1, plugin.getConfig().getInt("generation.maximum-saved-sites", 2000)); if (repository.sites().size() < max) return true; repository.sites().values().stream().filter(DigSite::completed).sorted(Comparator.comparingLong(DigSite::createdAt)).limit(repository.sites().size() - max + 1L).map(DigSite::id).toList().forEach(repository::removeSite); return repository.sites().size() < max; }
    @EventHandler public void onBreak(BlockBreakEvent event) { if (!(event.getBlock().getState() instanceof BrushableBlock state)) return; String raw = state.getPersistentDataContainer().get(siteKey, PersistentDataType.STRING); if (raw == null && legacySiteKey != null) raw = state.getPersistentDataContainer().get(legacySiteKey, PersistentDataType.STRING); if (raw == null) return; try { DigSite site = repository.sites().get(UUID.fromString(raw)); if (site != null) { site.blocks().remove(DigSite.blockKey(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ())); if (site.blocks().isEmpty()) site.completed(true); repository.dirty(); } } catch (IllegalArgumentException ignored) { } }
    private void scanLoadedSites() { for (DigSite site : repository.sites().values()) { if (site.completed()) continue; World world = Bukkit.getWorld(site.worldId()); if (world == null) continue; site.blocks().removeIf(raw -> missing(world, raw)); if (site.blocks().isEmpty()) site.completed(true); } repository.dirty(); }
    private boolean missing(World world, String raw) { try { String[] parts = raw.split(","); int x = Integer.parseInt(parts[0]); int y = Integer.parseInt(parts[1]); int z = Integer.parseInt(parts[2]); if (!world.isChunkLoaded(x >> 4, z >> 4)) return false; Material type = world.getBlockAt(x, y, z).getType(); return type != Material.SUSPICIOUS_SAND && type != Material.SUSPICIOUS_GRAVEL; } catch (RuntimeException exception) { return true; } }
    public DigSite nearest(Location location, double maximumDistance) { if (location.getWorld() == null) return null; double maxSquared = maximumDistance * maximumDistance; DigSite best = null; double bestDistance = maxSquared; for (DigSite site : repository.sites().values()) { if (site.completed() || !site.worldId().equals(location.getWorld().getUID())) continue; double dx = site.x() - location.getX(); double dy = site.y() - location.getY(); double dz = site.z() - location.getZ(); double distance = dx * dx + dy * dy + dz * dz; if (distance < bestDistance) { bestDistance = distance; best = site; } } return best; }
}
