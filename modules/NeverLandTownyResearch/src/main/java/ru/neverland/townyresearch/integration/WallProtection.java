package ru.neverland.townyresearch.integration;
import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.neverland.townyresearch.service.ResearchService;
public final class WallProtection implements Listener {
    private final ResearchService service;public WallProtection(ResearchService service){this.service=service;}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)public void damage(EntityDamageByEntityEvent event){
        if(!(event.getEntity() instanceof Player player))return;Entity source=event.getDamager();if(source instanceof Projectile projectile){if(!(projectile.getShooter() instanceof Entity shooter))return;source=shooter;}if(!(source instanceof Enemy))return;
        var town=service.town(player);if(town==null)return;var local=TownyAPI.getInstance().getTown(player.getLocation());if(local==null||!town.getUUID().equals(local.getUUID()))return;var view=service.research(town.getUUID()).orElse(null);
        if(view==null||view.buildings().getOrDefault("fortress_wall",0)<1||!ru.neverland.integration.BuildingOperations.active(town.getUUID(),"fortress_wall"))return;
        event.setDamage(ru.neverland.integration.ResearchEffects.hostileDamage(event.getDamage(),service.bonus(town.getUUID(),"walls"),true,true,true));
    }
}
