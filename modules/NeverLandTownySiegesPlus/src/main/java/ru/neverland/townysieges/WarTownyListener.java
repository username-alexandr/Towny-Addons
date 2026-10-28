package ru.neverland.townysieges;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.block.data.Directional;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.actions.*;
import com.palmergames.bukkit.towny.event.damage.*;
import ru.neverland.core.*;

/** Overrides Towny's own permission decisions, never a cancelled Bukkit damage/build event. */
public final class WarTownyListener implements Listener {
    private record Frame(EntityDamageByEntityEvent event,UUID territory){}
    private final ThreadLocal<Deque<Frame>> combat=ThreadLocal.withInitial(ArrayDeque::new);
    private final NeverLandTownySiegesPlus plugin;
    private final WarControl control;
    public WarTownyListener(NeverLandTownySiegesPlus plugin,WarControl control){this.plugin=plugin;this.control=control;}
    private boolean protectedResident(UUID id){var r=TownyAPI.getInstance().getResident(id);return r==null||r.isJailed()||r.hasRespawnProtection();}
    private boolean pair(UUID attacker,UUID victim,Location at){
        if(attacker==null||attacker.equals(victim)||protectedResident(attacker)||protectedResident(victim))return false;
        var town=TownyAPI.getInstance().getTown(at);return town!=null&&!town.isAdminDisabledPVP()&&control.pvp(attacker,victim,at);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void damageStart(EntityDamageByEntityEvent e){
        UUID[] territory={null};
        if(!e.isCancelled()&&e.getEntity() instanceof Player victim)SuiteState.tryRun("war PvP",()->{
            UUID actor=plugin.tnt().owner(e.getDamager());
            if(actor==null)actor=plugin.tnt().owner(e.getDamageSource().getCausingEntity());
            if(pair(actor,victim.getUniqueId(),victim.getLocation()))territory[0]=TownyAPI.getInstance().getTown(victim.getLocation()).getUUID();
        });
        combat.get().push(new Frame(e,territory[0]));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void damageEnd(EntityDamageByEntityEvent e){var stack=combat.get();if(!stack.isEmpty()&&stack.peek().event()==e)stack.pop();else stack.removeIf(f->f.event()==e);if(stack.isEmpty())combat.remove();}
    @EventHandler(priority=EventPriority.LOWEST)
    public void pvpTest(TownBlockPVPTestEvent e){
        var stack=combat.get();var f=stack.peek();
        if(f!=null&&f.territory()!=null&&!f.event().isCancelled()&&e.getTown()!=null&&f.territory().equals(e.getTown().getUUID())&&!e.getTown().isAdminDisabledPVP())e.setPvp(true);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void playerDecision(TownyPlayerDamagePlayerEvent e){SuiteState.tryRun("war Towny PvP decision",()->{
        if(pair(e.getAttackingPlayer().getUniqueId(),e.getVictimPlayer().getUniqueId(),e.getLocation()))e.setCancelled(false);
    });}
    // Towny's generic explosion check has no attacker field and runs before damageStart.
    // The final explosive guard below requires an attributed enemy. Bukkit cancellations stay set.
    @EventHandler(priority=EventPriority.LOWEST)
    public void explosionDecision(TownyExplosionDamagesEntityEvent e){
        if(e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_EXPLOSION||!(e.getEntity() instanceof Player victim))return;
        SuiteState.tryRun("war explosion permission",()->{var w=e.getTown()==null?null:control.war(e.getTown().getUUID());var own=SiegeService.town(victim.getUniqueId());
            if(w!=null&&own!=null&&!e.getTown().isAdminDisabledPVP()&&!protectedResident(victim.getUniqueId())&&(own.getUUID().equals(w.attacker())||own.getUUID().equals(w.defender())))e.setCancelled(false);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explosiveDamage(EntityDamageByEntityEvent e){
        if(e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_EXPLOSION||!(e.getEntity() instanceof Player victim)||!com.palmergames.bukkit.towny.utils.EntityTypeUtil.isPVPExplosive(e.getDamager().getType()))return;
        boolean checked=SuiteState.tryRun("war explosive participants",()->{var t=TownyAPI.getInstance().getTown(victim.getLocation());
            if(t!=null&&control.war(t.getUUID())!=null&&!pair(plugin.tnt().owner(e.getDamager()),victim.getUniqueId(),victim.getLocation()))e.setCancelled(true);
        });if(!checked&&knownWar(victim.getLocation()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void build(TownyBuildEvent e){if(e.getMaterial()==Material.TNT)SuiteState.tryRun("war TNT placement",()->{if(control.attackerTnt(e.getPlayer(),e.getLocation()))e.setCancelled(false);});}
    @EventHandler(priority=EventPriority.LOWEST)
    public void destroy(TownyDestroyEvent e){if(e.getMaterial()==Material.TNT)SuiteState.tryRun("war TNT ignition",()->{if(control.attackerTnt(e.getPlayer(),e.getLocation()))e.setCancelled(false);});}
    @EventHandler(priority=EventPriority.LOWEST)
    public void itemuse(TownyItemuseEvent e){if(e.getMaterial()==Material.FLINT_AND_STEEL||e.getMaterial()==Material.FIRE_CHARGE)SuiteState.tryRun("war TNT item use",()->{
        var at=e.getLocation();if(Bukkit.isOwnedByCurrentRegion(at)&&at.getBlock().getType()==Material.TNT&&control.attackerTnt(e.getPlayer(),at))e.setCancelled(false);
    });}
    private boolean knownWar(Location at){if(!control.configured())return false;var t=TownyAPI.getInstance().getTown(at);return t!=null&&plugin.raids().knownDeclaration(t.getUUID());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void prime(TNTPrimeEvent e){boolean checked=SuiteState.tryRun("war TNT prime",()->{
        if(!control.primeAllowed(e.getBlock().getLocation())){e.setCancelled(true);control.denied(e.getBlock().getLocation(),plugin.tnt().owner(e.getPrimingEntity()));}
    });if(!checked&&knownWar(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dispense(BlockDispenseEvent e){
        if(e.getBlock().getType()!=Material.DISPENSER||e.getItem().getType()!=Material.TNT||!(e.getBlock().getBlockData() instanceof Directional direction))return;
        Location at=e.getBlock().getRelative(direction.getFacing()).getLocation();
        boolean checked=SuiteState.tryRun("war dispenser TNT",()->{if(!control.primeAllowed(at)){e.setCancelled(true);control.denied(at,null);}});
        if(!checked&&knownWar(at))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spawn(EntitySpawnEvent e){if(!(e.getEntity() instanceof TNTPrimed))return;boolean checked=SuiteState.tryRun("war TNT spawn",()->{
        if(!control.primeAllowed(e.getLocation())){e.setCancelled(true);control.denied(e.getLocation(),plugin.tnt().owner(e.getEntity()));}
    });if(!checked&&knownWar(e.getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent e){
        if(e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null||e.getClickedBlock().getType()!=Material.TNT||e.getItem()==null||!Set.of(Material.FLINT_AND_STEEL,Material.FIRE_CHARGE).contains(e.getItem().getType()))return;
        SuiteState.tryRun("war TNT interaction",()->{if(!control.primeAllowed(e.getClickedBlock().getLocation())){
            e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);control.denied(e.getClickedBlock().getLocation(),e.getPlayer().getUniqueId());
            plugin.raids().reply(e.getPlayer(),"Здесь TNT пока нельзя поджечь. Разрушьте городские здания в этом чанке или удержите пустой участок. Проверить: /sieges chunk");
        }});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent e){SuiteState.tryRun("war position",()->control.sample(e.getPlayer(),e.getTo()));}
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e){control.forget(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent e){control.forget(e.getEntity().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent e){control.forget(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void mode(PlayerGameModeChangeEvent e){control.forget(e.getPlayer().getUniqueId());}
}
