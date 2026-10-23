package ru.neverland.townybuilds.construction;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;
import ru.neverland.core.SuiteScheduler;
import ru.neverland.core.WorldTasks;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Personal, temporary hints. All callers hold SuiteState.lock(); update runs on the player's region. */
final class ConstructionBlockDisplays {
    record Hint(Location location, BlockData data) {}
    private record Key(UUID world, int x, int y, int z) {
        static Key of(Location location) {
            return new Key(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
    private final Plugin plugin;
    private final Map<UUID, Map<Key, BlockDisplay>> viewers = new HashMap<>();

    ConstructionBlockDisplays(Plugin plugin) { this.plugin = plugin; }

    int update(Player player, List<Hint> hints, int perPlayer, int serverLimit, float scale) {
        if (!SuiteScheduler.owns(player)) throw new IllegalStateException("Player region required");
        Map<Key, Hint> wanted = new LinkedHashMap<>();
        for (Hint hint : hints) {
            Location location = hint.location();
            // Never spawn into another region or cause synchronous chunk loads at a region boundary.
            if (wanted.size() >= perPlayer) break;
            if (location.getWorld().equals(player.getWorld()) && WorldTasks.ownsBox(location, 1)
                    && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4))
                wanted.putIfAbsent(Key.of(location), hint);
        }
        Map<Key, BlockDisplay> current = viewers.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        current.entrySet().removeIf(entry -> {
            if (!wanted.containsKey(entry.getKey()) || !SuiteScheduler.owns(entry.getValue()) || !entry.getValue().isValid()) {
                WorldTasks.remove(plugin, entry.getValue());
                return true;
            }
            return false;
        });
        int available = Math.max(0, serverLimit - size());
        for (var entry : wanted.entrySet()) {
            BlockDisplay display = current.get(entry.getKey());
            Hint hint = entry.getValue();
            if (display == null) {
                if (available == 0) continue;
                float inset = (1.0f - scale) / 2.0f;
                display = hint.location().getWorld().spawn(hint.location(), BlockDisplay.class, created -> {
                    created.setPersistent(false);
                    created.setVisibleByDefault(false);
                    created.setInvulnerable(true);
                    created.setGravity(false);
                    created.setBlock(hint.data());
                    created.setTransformationMatrix(new Matrix4f().translation(inset, inset, inset).scale(scale));
                    created.setBrightness(new Display.Brightness(15, 15));
                    created.setShadowRadius(0);
                    created.setViewRange(2.0f);
                });
                if (!display.isValid()) continue; // A protection plugin can cancel entity creation.
                current.put(entry.getKey(), display);
                player.showEntity(plugin, display);
                available--;
            } else if (!display.getBlock().equals(hint.data())) {
                display.setBlock(hint.data());
            }
        }
        int shown = current.size();
        if (current.isEmpty()) viewers.remove(player.getUniqueId());
        return shown;
    }

    int size() { return viewers.values().stream().mapToInt(Map::size).sum(); }
    Set<UUID> viewers() { return Set.copyOf(viewers.keySet()); }

    void remove(UUID playerId) {
        Map<Key, BlockDisplay> removed = viewers.remove(playerId);
        if (removed != null) removed.values().forEach(display -> WorldTasks.remove(plugin, display));
    }

    void clear() { for (UUID viewer : viewers()) remove(viewer); }
}
