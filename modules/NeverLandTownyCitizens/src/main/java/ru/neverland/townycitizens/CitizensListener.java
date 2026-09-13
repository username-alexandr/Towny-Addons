package ru.neverland.townycitizens;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Only adds restrictions. Never uncancels a Towny/protection event. */
public final class CitizensListener implements Listener {
    private final CitizensService service;
    private final Map<UUID, Long> notified = new HashMap<>();
    public CitizensListener(CitizensService service) { this.service = service; }
    private boolean denied(Player player, Location at, String right) {
        if (player.hasPermission("neverlandtownycitizens.bypass")) return false;
        var town = TownyAPI.getInstance().getTown(at); if (town == null) return false;
        boolean allowed; try { allowed = service.allows(town.getUUID(), player.getUniqueId(), right); } catch (RuntimeException ex) { allowed = false; }
        if (!allowed && System.currentTimeMillis() - notified.getOrDefault(player.getUniqueId(), 0L) > 3000) {
            notified.put(player.getUniqueId(), System.currentTimeMillis());
            CitizensCommand.tell(player, "&cСтатус в городе «" + town.getName() + "» не разрешает это действие. /t citizens info " + player.getName() + " " + town.getName());
        }
        return !allowed;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (event instanceof org.bukkit.event.block.BlockMultiPlaceEvent multi) {
            for (var state : multi.getReplacedBlockStates()) if (denied(event.getPlayer(), state.getLocation(), "BUILD")) { event.setCancelled(true); return; }
        } else if (denied(event.getPlayer(), event.getBlockPlaced().getLocation(), "BUILD")) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void destroy(BlockBreakEvent event) { if (denied(event.getPlayer(), event.getBlock().getLocation(), "DESTROY")) event.setCancelled(true); }
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event) { notified.remove(event.getPlayer().getUniqueId()); }
}
