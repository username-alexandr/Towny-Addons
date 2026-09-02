package ru.neverland.townychronicles.listener;

import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.NationAddTownEvent;
import com.palmergames.bukkit.towny.event.NationRemoveTownEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.RenameTownEvent;
import com.palmergames.bukkit.towny.event.TownClaimEvent;
import com.palmergames.bukkit.towny.event.town.TownConqueredEvent;
import com.palmergames.bukkit.towny.event.town.TownMayorChangedEvent;
import com.palmergames.bukkit.towny.event.town.TownReclaimedEvent;
import com.palmergames.bukkit.towny.event.town.TownRuinedEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townychronicles.service.ChronicleTracker;

public final class TownyHistoryListener implements Listener {
    private final JavaPlugin plugin;private final ChronicleTracker tracker;public TownyHistoryListener(JavaPlugin plugin,ChronicleTracker tracker){this.plugin=plugin;this.tracker=tracker;}
    @EventHandler(priority=EventPriority.MONITOR)public void onNewTown(NewTownEvent event){tracker.process(event.getTown(),true);}
    @EventHandler(priority=EventPriority.MONITOR)public void onDelete(DeleteTownEvent event){tracker.dissolve(event.getTownUUID(),event.getTownName(),"Причина: "+event.getCause().name());}
    @EventHandler(priority=EventPriority.MONITOR)public void onRename(RenameTownEvent event){tracker.renamed(event.getTown(),event.getOldName());}
    @EventHandler(priority=EventPriority.MONITOR)public void onMayor(TownMayorChangedEvent event){tracker.mayorChanged(event.getTown(),event.getOldMayor()==null?"":event.getOldMayor().getName(),event.getNewMayor()==null?"":event.getNewMayor().getName(),event.getNewMayor()==null?null:event.getNewMayor().getUUID());}
    @EventHandler(priority=EventPriority.MONITOR)public void onNationAdd(NationAddTownEvent event){tracker.nationChanged(event.getTown(),"",event.getNation().getName(),event.getNation().getUUID());}
    @EventHandler(priority=EventPriority.MONITOR)public void onNationRemove(NationRemoveTownEvent event){tracker.nationChanged(event.getTown(),event.getNation().getName(),"",null);}
    @EventHandler(priority=EventPriority.MONITOR)public void onConquer(TownConqueredEvent event){tracker.conquered(event.getTown());}
    @EventHandler(priority=EventPriority.MONITOR)public void onRuined(TownRuinedEvent event){tracker.ruined(event.getTown(),event.getOldMayorName());}
    @EventHandler(priority=EventPriority.MONITOR)public void onReclaimed(TownReclaimedEvent event){tracker.reclaimed(event.getTown(),event.getResident().getName());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void onClaim(TownClaimEvent event){Bukkit.getScheduler().runTask(plugin,()->tracker.process(event.getTown(),false));}
}
