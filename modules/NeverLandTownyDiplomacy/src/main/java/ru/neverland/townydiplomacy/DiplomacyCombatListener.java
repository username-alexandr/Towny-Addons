package ru.neverland.townydiplomacy;

import java.util.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.potion.PotionEffectTypeCategory;
import com.palmergames.bukkit.towny.event.damage.TownyPlayerDamagePlayerEvent;

/** Additional veto only: never uncancels Towny, world protection, PvP flags or another plugin. */
public final class DiplomacyCombatListener implements Listener {
    private final NeverLandTownyDiplomacy plugin;private final DiplomacyService service;
    public DiplomacyCombatListener(NeverLandTownyDiplomacy plugin,DiplomacyService service) { this.plugin=plugin;this.service=service; }
    public UUID responsible(Entity entity) { return responsible(entity,0); }
    private UUID responsible(Entity entity,int depth) {
        if(entity==null||depth>4)return null;if(entity instanceof Player p)return p.getUniqueId();
        if(entity instanceof Projectile p && p.getShooter() instanceof Entity owner)return responsible(owner,depth+1);
        if(entity instanceof Tameable pet && pet.getOwner()!=null)return pet.getOwner().getUniqueId();
        if(entity instanceof TNTPrimed t)return responsible(t.getSource(),depth+1);
        if(entity instanceof AreaEffectCloud c && c.getSource() instanceof Entity owner)return responsible(owner,depth+1);
        return null;
    }
    public boolean blocked(UUID attacker,UUID victim) {
        if(attacker==null||victim==null||attacker.equals(victim))return false;
        var a=service.ownTown(attacker);var b=service.ownTown(victim);
        return a!=null&&b!=null&&service.hostileBlocked(a.getUUID(),b.getUUID());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void towny(TownyPlayerDamagePlayerEvent e) {
        if(blocked(e.getAttackingPlayer().getUniqueId(),e.getVictimPlayer().getUniqueId())) {
            e.setCancelled(true);e.setMessage("Дипломатический договор запрещает нападение. Проверьте /diplomacy");
        }
    }
    private UUID attacker(EntityDamageByEntityEvent e) {
        UUID actor=responsible(e.getDamageSource().getCausingEntity());return actor==null?responsible(e.getDamager()):actor;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e) {
        if(blocked(attacker(e),responsible(e.getEntity())))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void combust(EntityCombustByEntityEvent e) {
        if(blocked(responsible(e.getCombuster()),responsible(e.getEntity())))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void splash(PotionSplashEvent e) {
        if(e.getPotion().getEffects().stream().noneMatch(p->p.getType().getCategory()==PotionEffectTypeCategory.HARMFUL))return;
        UUID attacker=responsible(e.getPotion());
        for(var victim:e.getAffectedEntities())if(blocked(attacker,responsible(victim)))e.setIntensity(victim,0);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e) {
        // Cloud custom and base effects are combined by the server; suppress hostile clouds conservatively.
        var cloud=e.getEntity();boolean harmful=cloud.getCustomEffects().stream().anyMatch(p->p.getType().getCategory()==PotionEffectTypeCategory.HARMFUL)
                || cloud.getBasePotionType()!=null&&cloud.getBasePotionType().getPotionEffects().stream().anyMatch(p->p.getType().getCategory()==PotionEffectTypeCategory.HARMFUL);
        if(harmful)e.getAffectedEntities().removeIf(v->blocked(responsible(cloud),responsible(v)));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void incident(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player victim)||e.getFinalDamage()<=0)return;
        UUID actor=attacker(e);if(actor==null)return;var a=service.ownTown(actor);var b=service.ownTown(victim.getUniqueId());if(a==null||b==null)return;
        try {
            var l=victim.getLocation();String where=l.getWorld().getName()+" "+l.getBlockX()+" "+l.getBlockY()+" "+l.getBlockZ();
            var defenders=service.recordAttack(a.getUUID(),b.getUUID(),where);
            for(var id:defenders)plugin.notifyTown(id,"&cНападение на город &f"+b.getName()+"&c со стороны &f"+a.getName()+"&7. Место: &f"+where+"&7. Обязательство защиты: /diplomacy incidents");
        } catch(Exception ex) { plugin.failure("Не удалось сохранить оборонный инцидент",ex); }
    }
}
