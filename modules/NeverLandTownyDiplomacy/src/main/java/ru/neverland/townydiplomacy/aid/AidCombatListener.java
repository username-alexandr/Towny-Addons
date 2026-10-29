package ru.neverland.townydiplomacy.aid;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;
import ru.neverland.townydiplomacy.*;

/** Observation only. Never cancels or uncancels damage and never reads a remote player's location. */
public final class AidCombatListener implements Listener {
    private final AidService aid;private final DiplomacyService diplomacy;private final DiplomacyCombatListener attribution;
    public AidCombatListener(NeverLandTownyDiplomacy plugin,DiplomacyService diplomacy,AidService aid){this.aid=aid;this.diplomacy=diplomacy;attribution=new DiplomacyCombatListener(plugin,diplomacy);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){
        if(e.isCancelled()||!(e.getEntity() instanceof Player victim)||!SuiteScheduler.owns(victim)||!Double.isFinite(e.getFinalDamage())||e.getFinalDamage()<1)return;
        // UUID attribution is safe for arrows, spears, pets and TNT; no remote position/health access.
        UUID actor=attribution.responsible(e.getDamageSource().getCausingEntity());if(actor==null)actor=attribution.responsible(e.getDamager());if(actor==null||actor.equals(victim.getUniqueId()))return;
        var location=victim.getLocation();long damage=(long)Math.floor(Math.min(20,Math.min(victim.getHealth(),e.getFinalDamage()))*1000);if(damage<1000)return;
        try(var guard=SuiteState.guard()){
            if(!aid.ready())return;var a=diplomacy.ownTown(actor);var b=diplomacy.ownTown(victim.getUniqueId());var territory=TownyAPI.getInstance().getTown(location);
            if(a==null||b==null||territory==null||a.equals(b)||diplomacy.hostileBlocked(a.getUUID(),b.getUUID()))return;
            aid.observe(new AidFact(UUID.randomUUID(),System.currentTimeMillis(),actor,a.getUUID(),victim.getUniqueId(),b.getUUID(),territory.getUUID(),damage,location.getWorld().getUID(),location.getBlockX(),location.getBlockY(),location.getBlockZ()));
        }
    }
}
