package ru.neverland.townychronicles.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.SuiteScheduler;
import ru.neverland.townychronicles.api.ChronicleRecordEvent;
import ru.neverland.townychronicles.api.TownyChroniclesApi;
import ru.neverland.townychronicles.integration.TownyHook;
import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.model.ChronicleEntry;
import ru.neverland.townychronicles.util.ColorUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class ChronicleService implements TownyChroniclesApi {
    private final JavaPlugin plugin;private final TownyHook towny;private final ChronicleRepository repository;
    public ChronicleService(JavaPlugin plugin,TownyHook towny,ChronicleRepository repository){this.plugin=plugin;this.towny=towny;this.repository=repository;}
    @Override public ChronicleEntry record(UUID townId,ChronicleCategory category,String title,List<String>details,String actor,String source){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.ApiServices.primaryThread();return record(townId,category,System.currentTimeMillis(),title,details,actor,source);} }
    @Override public ChronicleEntry record(UUID townId,ChronicleCategory category,long timestamp,String title,List<String>details,String actor,String source){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.ApiServices.primaryThread();Town town=towny.town(townId);String name=town==null?repository.state(townId).name():town.getName();return record(townId,name,category,timestamp,title,details,actor,source,true);} }
    public ChronicleEntry record(UUID townId,String townName,ChronicleCategory category,long timestamp,String title,List<String>details,String actor,String source,boolean announce){
        try (var guard = ru.neverland.core.SuiteState.guard()) {
            ru.neverland.core.ApiServices.primaryThread();
            ChronicleEntry entry=new ChronicleEntry(UUID.randomUUID(),townId,townName,category,timestamp,title,details,actor,source);
            repository.add(entry);
            // Towny can fire DeleteTownEvent from its asynchronous daily upkeep task.
            // Keep the durable record on that thread; only the synchronous Bukkit event
            // and player notifications need to move onto the global tick scheduler.
            if (Bukkit.isPrimaryThread()) publish(entry,announce);
            else if (!Bukkit.isStopping() && plugin.isEnabled()) {
                try {
                    SuiteScheduler.get().runTask(plugin,()->publish(entry,announce));
                } catch (RuntimeException error) {
                    // A shutdown race must never roll back a record already accepted
                    // by the repository or prevent dissolve() from saving its state.
                    plugin.getLogger().log(Level.WARNING,"Запись летописи сохранена, но уведомление не отправлено: "+entry.id(),error);
                }
            }
            return entry;
        }
    }
    private void publish(ChronicleEntry entry,boolean notifyPlayers){
        Bukkit.getPluginManager().callEvent(new ChronicleRecordEvent(entry));
        if(notifyPlayers)announce(entry);
    }
    public void announce(ChronicleEntry entry){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(!plugin.getConfig().getBoolean("announcements.enabled",true)||!plugin.getConfig().getStringList("announcements.categories").contains(entry.category().name()))return;String message=plugin.getConfig().getString("announcements.format","%town%: %title%").replace("%town%",entry.townName()).replace("%title%",entry.title());String colored=ColorUtil.color(message);SuiteScheduler.players(plugin,player->player.sendMessage(colored));Bukkit.getConsoleSender().sendMessage(colored);} }
    @Override public List<ChronicleEntry>entries(UUID townId){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.ApiServices.primaryThread();return repository.entries(townId);} }@Override public ChronicleEntry latest(UUID townId){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.ApiServices.primaryThread();return repository.latest(townId);} }public ChronicleRepository repository(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return repository;} }
}
