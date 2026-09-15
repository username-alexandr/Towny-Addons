package ru.neverland.townyarmy;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
public final class ArmyListener implements Listener {
    private final ArmyService service;
    public ArmyListener(ArmyService service) { this.service = service; }
    @EventHandler public void quit(PlayerQuitEvent e) { service.leave(e.getPlayer().getUniqueId()); }
    @EventHandler public void death(PlayerDeathEvent e) { service.leave(e.getEntity().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent e) { if (!(e instanceof PlayerTeleportEvent)) service.moved(e.getPlayer(), e.getFrom(), e.getTo()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) { service.teleported(e.getPlayer().getUniqueId()); }
}
