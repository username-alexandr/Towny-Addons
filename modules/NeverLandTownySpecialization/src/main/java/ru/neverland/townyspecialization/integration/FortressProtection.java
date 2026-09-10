package ru.neverland.townyspecialization.integration;
import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.neverland.townyspecialization.service.SpecializationService;
public final class FortressProtection implements Listener {
    private final SpecializationService service;public FortressProtection(SpecializationService service){this.service=service;}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)public void damage(EntityDamageByEntityEvent event){
        if(!(event.getEntity() instanceof Player player))return;Entity source=event.getDamager();if(source instanceof Projectile projectile){if(!(projectile.getShooter() instanceof Entity shooter))return;source=shooter;}if(!(source instanceof Enemy))return;
        var town=service.town(player);if(town==null)return;var local=TownyAPI.getInstance().getTown(player.getLocation());if(local==null||!town.getUUID().equals(local.getUUID()))return;
        double bonus=service.bonus(town.getUUID(),"mob_defense");if(bonus<=0)return;
        event.setDamage(ru.neverland.integration.SpecializationRules.hostileDamage(event.getDamage(),bonus,true,true));
    }
}
