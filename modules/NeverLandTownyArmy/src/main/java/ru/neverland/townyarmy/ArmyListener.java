package ru.neverland.townyarmy;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
public final class ArmyListener implements Listener {
    private final ArmyService service;
    public ArmyListener(ArmyService service) { this.service = service; }
    @EventHandler public void quit(PlayerQuitEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) { service.leave(e.getPlayer().getUniqueId()); } }
    @EventHandler public void death(PlayerDeathEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyArmy")){ru.neverland.core.ModuleGate.stopEvent(e);return;} service.leave(e.getEntity().getUniqueId()); } }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyArmy")){ru.neverland.core.ModuleGate.stopEvent(e);return;} if (!(e instanceof PlayerTeleportEvent)) service.moved(e.getPlayer(), e.getFrom(), e.getTo()); } }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyArmy")){ru.neverland.core.ModuleGate.stopEvent(e);return;} service.teleported(e.getPlayer().getUniqueId()); } }
}
