package ru.neverland.townysieges;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import io.papermc.paper.event.entity.EntityMoveEvent;
import static ru.neverland.townysieges.SiegeRules.*;

/** Additional restrictions only. Existing Towny/PvP/protection cancellations are always respected. */
public final class SiegeListener implements Listener {
    private final SiegeService service;
    private final Set<UUID> movingVehicles=new HashSet<>();
    public SiegeListener(SiegeService service) { this.service=service; }
    private boolean entry(Player p,Location from,Location to,boolean flying) {
        if(to==null)return false;
        var a=service.assess(p,to);if(a.town()==null)return false;
        Point begin=SiegeService.point(from),end=SiegeService.point(to);
        if(a.enters(Fort.GATE,begin,end)) { service.tell(p,"Крепостные ворота перекрывают проход атакующим. Отойдите от ворот.");return true; }
        if(flying && (a.enters(Fort.TOWER,begin,end)||a.enters(Fort.KEEP,begin,end))) { service.tell(p,"Дозорная башня перекрывает воздушный подход. Приземлитесь за пределами зоны.");return true; }
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e) {
        if(e instanceof PlayerTeleportEvent || e.getTo()==null || e.getFrom().toVector().equals(e.getTo().toVector()))return;
        if(entry(e.getPlayer(),e.getFrom(),e.getTo(),e.getPlayer().isGliding()||e.getPlayer().isRiptiding()))e.setCancelled(true);
    }
    private void teleportCheck(PlayerTeleportEvent e) {
        if(e.getTo()==null)return;
        var a=service.assess(e.getPlayer(),e.getTo());if(a.town()==null)return;
        boolean item=Set.of(PlayerTeleportEvent.TeleportCause.ENDER_PEARL,PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT,PlayerTeleportEvent.TeleportCause.END_GATEWAY).contains(e.getCause());
        if(item && (a.uncertain()||a.wall(SiegeService.point(e.getTo())))) {e.setCancelled(true);service.tell(e.getPlayer(),"Активная крепостная стена блокирует телепортацию предметами в осаждённый город.");return;}
        if(entry(e.getPlayer(),e.getFrom(),e.getTo(),e.getPlayer().isGliding()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e) { teleportCheck(e); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(PlayerPortalEvent e) { teleportCheck(e); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e) {
        var block=e.getClickedBlock();if(block==null)return;
        boolean mechanism=block.getBlockData() instanceof Openable || block.getType().name().endsWith("_BUTTON") || block.getType().name().endsWith("_PRESSURE_PLATE") || block.getType()==org.bukkit.Material.LEVER;
        if(!mechanism || e.getAction()!=Action.RIGHT_CLICK_BLOCK && e.getAction()!=Action.PHYSICAL)return;
        var a=service.assess(e.getPlayer(),block.getLocation());
        if(a.near(Fort.GATE,SiegeService.point(block.getLocation()))>0) {e.setCancelled(true);service.tell(e.getPlayer(),"Механизмы крепостных ворот недоступны атакующей стороне.");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void glide(EntityToggleGlideEvent e) {
        if(!e.isGliding()||!(e.getEntity() instanceof Player p))return;
        var a=service.assess(p,p.getLocation());Point pos=SiegeService.point(p.getLocation());
        if(a.near(Fort.TOWER,pos)>0||a.near(Fort.KEEP,pos)>0) {e.setCancelled(true);service.tell(p,"В зоне дозорной башни нельзя раскрыть элитры.");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void sprint(PlayerToggleSprintEvent e) {
        if(e.isSprinting() && service.assess(e.getPlayer(),e.getPlayer().getLocation()).near(Fort.MOAT,SiegeService.point(e.getPlayer().getLocation()))>0)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void swim(EntityToggleSwimEvent e) {
        if(e.isSwimming()&&e.getEntity() instanceof Player p&&service.assess(p,p.getLocation()).near(Fort.MOAT,SiegeService.point(p.getLocation()))>0)e.setCancelled(true);
    }
    static List<Player> passengers(Entity entity) { var out=new ArrayList<Player>(); passengers(entity,out,0);return out; }
    static void passengers(Entity e,List<Player> out,int depth) { if(depth>8)return;for(var p:e.getPassengers()){if(p instanceof Player player)out.add(player);passengers(p,out,depth+1);} }
    private boolean vehicleEntry(Entity vehicle,Location from,Location to) {
        for(Player p:passengers(vehicle)) {
            if(entry(p,from,to,false))return true;
            if(vehicle instanceof Boat) {
                var a=service.assess(p,to);
                if(a.enters(Fort.PORT,SiegeService.point(from),SiegeService.point(to))) {service.tell(p,"Портовый форт перекрывает вход в гавань для лодок атакующих.");return true;}
            }
        }
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void boat(VehicleMoveEvent e) {
        var vehicle=e.getVehicle();if(movingVehicles.contains(vehicle.getUniqueId())||!vehicleEntry(vehicle,e.getFrom(),e.getTo()))return;
        movingVehicles.add(vehicle.getUniqueId());
        try {
            vehicle.setVelocity(new Vector());
            if(!vehicle.teleport(e.getFrom()))service.failed(new IllegalStateException("Не удалось вернуть транспорт за линию обороны"));
        } finally {movingVehicles.remove(vehicle.getUniqueId());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mount(EntityMoveEvent e) {
        if(e.hasChangedPosition()&&!e.getEntity().getPassengers().isEmpty()&&vehicleEntry(e.getEntity(),e.getFrom(),e.getTo()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void board(VehicleEnterEvent e) {
        if(!(e.getEntered() instanceof Player p))return;
        var at=e.getVehicle().getLocation();var a=service.assess(p,at);var pos=SiegeService.point(at);
        if(a.near(Fort.GATE,pos)>0||e.getVehicle() instanceof Boat&&a.near(Fort.PORT,pos)>0)e.setCancelled(true);
    }
    static Player responsible(Entity e,int depth) {
        if(e==null||depth>4)return null;if(e instanceof Player p)return p;
        if(e instanceof Projectile p&&p.getShooter() instanceof Entity owner)return responsible(owner,depth+1);
        if(e instanceof TNTPrimed t)return responsible(t.getSource(),depth+1);
        if(e instanceof Tameable t&&t.getOwner() instanceof Player p)return p;
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player defender))return;
        Player attacker=responsible(e.getDamageSource().getCausingEntity(),0);if(attacker==null)attacker=responsible(e.getDamager(),0);if(attacker==null)return;
        double reduction=service.defense(attacker,defender,defender.getLocation());
        if(reduction<0) {e.setCancelled(true);service.tell(attacker,"Проверка осадной обороны временно недоступна.");}
        else if(reduction>0)e.setDamage(e.getDamage()*(1-reduction));
    }
    @EventHandler public void quit(PlayerQuitEvent e) { service.leave(e.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent e) { service.leave(e.getEntity()); }
    @EventHandler public void join(PlayerJoinEvent e) { service.leave(e.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR) public void changedWorld(PlayerChangedWorldEvent e) { service.leave(e.getPlayer()); }
}
