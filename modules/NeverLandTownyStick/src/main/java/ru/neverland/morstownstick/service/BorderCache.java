package ru.neverland.morstownstick.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.morstownstick.integration.TownyFacade;
import ru.neverland.morstownstick.model.BorderEdge;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BorderCache {
    private record Entry(long expiresAt, List<BorderEdge> edges) { }

    private final JavaPlugin plugin;
    private final TownyFacade towny;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public BorderCache(JavaPlugin plugin, TownyFacade towny) {
        this.plugin = plugin;
        this.towny = towny;
    }

    public List<BorderEdge> edges(Town town, long currentTick) {
        long lifetime = Math.max(1L, plugin.getConfig().getLong("particles.town-border-cache-interval", 200L));
        Entry current = cache.get(town.getUUID());
        if (current != null && current.expiresAt >= currentTick) return current.edges;
        List<BorderEdge> edges = BorderGeometry.outsideEdges(towny.ownedCells(town), towny.cellSize());
        cache.put(town.getUUID(), new Entry(currentTick + lifetime, edges));
        return edges;
    }

    public void invalidate(UUID townId) {
        if (townId != null) cache.remove(townId);
    }

    public void clear() {
        cache.clear();
    }
}
