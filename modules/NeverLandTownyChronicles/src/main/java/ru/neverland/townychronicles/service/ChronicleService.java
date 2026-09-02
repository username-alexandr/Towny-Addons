package ru.neverland.townychronicles.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townychronicles.api.ChronicleRecordEvent;
import ru.neverland.townychronicles.api.TownyChroniclesApi;
import ru.neverland.townychronicles.integration.TownyHook;
import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.model.ChronicleEntry;
import ru.neverland.townychronicles.util.ColorUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChronicleService implements TownyChroniclesApi {
    private final JavaPlugin plugin;private final TownyHook towny;private final ChronicleRepository repository;
    public ChronicleService(JavaPlugin plugin,TownyHook towny,ChronicleRepository repository){this.plugin=plugin;this.towny=towny;this.repository=repository;}
    @Override public ChronicleEntry record(UUID townId,ChronicleCategory category,String title,List<String>details,String actor,String source){return record(townId,category,System.currentTimeMillis(),title,details,actor,source);}
    @Override public ChronicleEntry record(UUID townId,ChronicleCategory category,long timestamp,String title,List<String>details,String actor,String source){Town town=towny.town(townId);String name=town==null?repository.state(townId).name():town.getName();return record(townId,name,category,timestamp,title,details,actor,source,true);}
    public ChronicleEntry record(UUID townId,String townName,ChronicleCategory category,long timestamp,String title,List<String>details,String actor,String source,boolean announce){ChronicleEntry entry=new ChronicleEntry(UUID.randomUUID(),townId,townName,category,timestamp,title,details,actor,source);repository.add(entry);Bukkit.getPluginManager().callEvent(new ChronicleRecordEvent(entry));if(announce)announce(entry);return entry;}
    public void announce(ChronicleEntry entry){if(!plugin.getConfig().getBoolean("announcements.enabled",true)||!plugin.getConfig().getStringList("announcements.categories").contains(entry.category().name()))return;String message=plugin.getConfig().getString("announcements.format","%town%: %title%").replace("%town%",entry.townName()).replace("%title%",entry.title());String colored=ColorUtil.color(message);Bukkit.getOnlinePlayers().forEach(player->player.sendMessage(colored));Bukkit.getConsoleSender().sendMessage(colored);}
    @Override public List<ChronicleEntry>entries(UUID townId){return repository.entries(townId);}@Override public ChronicleEntry latest(UUID townId){return repository.latest(townId);}public ChronicleRepository repository(){return repository;}
}
