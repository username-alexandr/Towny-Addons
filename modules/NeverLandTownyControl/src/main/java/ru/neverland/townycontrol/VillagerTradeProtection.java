package ru.neverland.townycontrol;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.utils.CombatUtil;
import io.papermc.paper.event.player.PlayerTradeEvent;
import java.util.*;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;

/** Guard both entry and the final exchange. Never lifts another plugin's cancellation. */
final class VillagerTradeProtection implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID,Long> notices=new HashMap<>();
    VillagerTradeProtection(JavaPlugin plugin){this.plugin=plugin;}

    private String denied(Player player,AbstractVillager merchant){
        if(!plugin.getConfig().getBoolean("villager-trading.enabled",true))return null;
        try {
            if(!SuiteScheduler.owns(merchant))return "Подойдите ближе к торговцу и попробуйте снова.";
            var api=TownyAPI.getInstance();var location=merchant.getLocation();
            if(!api.isTownyWorld(location.getWorld()))return null;
            var plot=api.getTownBlock(location);var owner=plot==null?null:plot.getTownOrNull();
            if(owner==null)return null; // Wilderness trading is unchanged.
            var resident=api.getResident(player.getUniqueId());var town=resident==null?null:resident.getTownOrNull();
            if(town==null)return "В этом городе торговать с жителями могут только его жители и союзники.";
            if(owner.getUUID().equals(town.getUUID()))return null;
            if(!DiplomacyAccess.available())return "Сейчас не удалось проверить союз городов. Попробуйте позже.";
            if(DiplomacyAccess.tradeBlocked(owner.getUUID(),town.getUUID()))return "Торговля между вашими городами временно запрещена.";
            // Require a mutual Towny alliance; adding the host only to one's own ally list grants no access.
            boolean allied=CombatUtil.isAlly(owner,town)&&CombatUtil.isAlly(town,owner);
            var relations=DiplomacyAccess.relations(owner.getUUID(),town.getUUID());
            if(relations.contains("UNAVAILABLE"))return "Сейчас не удалось проверить союз городов. Попробуйте позже.";
            if(allied||relations.contains("ALLIANCE"))return null;
            return "Ваш город не состоит в союзе с этим городом. Торговля с его жителями недоступна.";
        } catch(RuntimeException|LinkageError unavailable){
            return "Сейчас не удалось проверить доступ к торговле. Попробуйте позже.";
        }
    }
    private boolean block(Player player,AbstractVillager merchant){
        String message=denied(player,merchant);if(message==null)return false;
        long now=System.nanoTime();Long last=notices.get(player.getUniqueId());
        if(last==null||now-last>=2_000_000_000L){notices.put(player.getUniqueId(),now);player.sendMessage("§c"+message);}
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEntityEvent event){synchronized(SuiteState.lock()){
        if(event.getRightClicked() instanceof AbstractVillager merchant&&block(event.getPlayer(),merchant))event.setCancelled(true);
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void open(InventoryOpenEvent event){synchronized(SuiteState.lock()){
        if(event.getPlayer() instanceof Player player&&event.getInventory() instanceof MerchantInventory inventory
            &&inventory.getMerchant() instanceof AbstractVillager merchant&&block(player,merchant))event.setCancelled(true);
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void trade(PlayerTradeEvent event){synchronized(SuiteState.lock()){
        if(block(event.getPlayer(),event.getVillager()))event.setCancelled(true);
    }}
    @EventHandler public void quit(PlayerQuitEvent event){synchronized(SuiteState.lock()){notices.remove(event.getPlayer().getUniqueId());}}
}
