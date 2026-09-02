package ru.neverland.mintespionage.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintespionage.model.IntelReport;
import ru.neverland.mintespionage.model.OperationStatus;
import ru.neverland.mintespionage.model.SpyOperation;
import ru.neverland.mintespionage.model.TownSpyData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EspionageRepository {
    private final JavaPlugin plugin;private final File file;private final Map<UUID,TownSpyData> towns=new LinkedHashMap<>();
    private final Map<UUID,SpyOperation> operations=new LinkedHashMap<>();private final Map<UUID,List<IntelReport>> reports=new LinkedHashMap<>();private boolean dirty;
    public EspionageRepository(JavaPlugin plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"data.yml");}
    public synchronized void load(){
        towns.clear();operations.clear();reports.clear();if(!file.exists())return;YamlConfiguration yaml=YamlConfiguration.loadConfiguration(file);
        ConfigurationSection townRoot=yaml.getConfigurationSection("towns");if(townRoot!=null)for(String raw:townRoot.getKeys(false))try{
            UUID id=UUID.fromString(raw);TownSpyData data=new TownSpyData(id);data.networkLevel(townRoot.getInt(raw+".network-level"));data.defenseLevel(townRoot.getInt(raw+".defense-level"));
            ConfigurationSection cooldowns=townRoot.getConfigurationSection(raw+".cooldowns");if(cooldowns!=null)for(String key:cooldowns.getKeys(false))data.cooldown(key,cooldowns.getLong(key));towns.put(id,data);
        }catch(IllegalArgumentException exception){plugin.getLogger().warning("Пропущены данные города "+raw);}
        ConfigurationSection operationRoot=yaml.getConfigurationSection("operations");if(operationRoot!=null)for(String raw:operationRoot.getKeys(false))try{
            String p="operations."+raw;SpyOperation operation=new SpyOperation(UUID.fromString(raw),UUID.fromString(yaml.getString(p+".attacker-id")),yaml.getString(p+".attacker-name","?"),
                    UUID.fromString(yaml.getString(p+".target-id")),yaml.getString(p+".target-name","?"),UUID.fromString(yaml.getString(p+".actor-id")),yaml.getString(p+".actor-name","?"),
                    yaml.getString(p+".type","reconnaissance"),yaml.getLong(p+".started-at"),yaml.getLong(p+".completes-at"),yaml.getDouble(p+".success-chance"),
                    yaml.getDouble(p+".detection-chance"),yaml.getDouble(p+".cost"),OperationStatus.valueOf(yaml.getString(p+".status","CANCELLED")),yaml.getBoolean(p+".detected"));operations.put(operation.id(),operation);
        }catch(Exception exception){plugin.getLogger().warning("Пропущена повреждённая операция "+raw+": "+exception.getMessage());}
        ConfigurationSection reportRoot=yaml.getConfigurationSection("reports");if(reportRoot!=null)for(String rawOwner:reportRoot.getKeys(false))try{
            UUID owner=UUID.fromString(rawOwner);ConfigurationSection values=reportRoot.getConfigurationSection(rawOwner);if(values==null)continue;List<IntelReport> list=new ArrayList<>();
            for(String raw:values.getKeys(false)){String p="reports."+rawOwner+"."+raw;list.add(new IntelReport(UUID.fromString(raw),owner,UUID.fromString(yaml.getString(p+".target-id")),yaml.getString(p+".target-name","?"),yaml.getString(p+".type","reconnaissance"),yaml.getLong(p+".created-at"),yaml.getLong(p+".expires-at"),yaml.getStringList(p+".lines"),yaml.getBoolean(p+".read")));}reports.put(owner,list);
        }catch(Exception exception){plugin.getLogger().warning("Пропущены повреждённые отчёты "+rawOwner+": "+exception.getMessage());}
        dirty=false;
    }
    public synchronized TownSpyData town(UUID id){return towns.computeIfAbsent(id,key->{dirty=true;return new TownSpyData(key);});}
    public synchronized void add(SpyOperation operation){operations.put(operation.id(),operation);dirty=true;}
    public synchronized SpyOperation operation(UUID id){return operations.get(id);}
    public synchronized List<SpyOperation> operations(){return List.copyOf(operations.values());}
    public synchronized List<SpyOperation> active(UUID town){return operations.values().stream().filter(v->v.status()==OperationStatus.ACTIVE&&v.attackerTownId().equals(town)).sorted(Comparator.comparingLong(SpyOperation::completesAt)).toList();}
    public synchronized void addReport(IntelReport report){List<IntelReport> list=reports.computeIfAbsent(report.ownerTownId(),key->new ArrayList<>());list.add(0,report);trim(list,plugin.getConfig().getInt("limits.reports-per-town",50));dirty=true;}
    public synchronized List<IntelReport> reports(UUID town){return List.copyOf(reports.getOrDefault(town,List.of()));}
    public synchronized void markDirty(){dirty=true;}
    public synchronized void cleanup(long now){
        if(plugin.getConfig().getBoolean("reports.delete-expired",true))for(List<IntelReport> list:reports.values())if(list.removeIf(report->report.expired(now)))dirty=true;
        int limit=Math.max(5,plugin.getConfig().getInt("limits.history-per-town",40));for(UUID town:towns.keySet()){
            List<SpyOperation> finished=operations.values().stream().filter(v->v.attackerTownId().equals(town)&&v.status()!=OperationStatus.ACTIVE).sorted(Comparator.comparingLong(SpyOperation::startedAt).reversed()).toList();
            if(finished.size()>limit)for(SpyOperation operation:finished.subList(limit,finished.size())){operations.remove(operation.id());dirty=true;}
        }
    }
    private <T> void trim(List<T> list,int maximum){while(list.size()>Math.max(1,maximum))list.remove(list.size()-1);}
    public synchronized void saveIfDirty(){if(dirty)save();}
    public synchronized void save(){
        YamlConfiguration yaml=new YamlConfiguration();for(TownSpyData data:towns.values()){String p="towns."+data.townId();yaml.set(p+".network-level",data.networkLevel());yaml.set(p+".defense-level",data.defenseLevel());for(Map.Entry<String,Long> e:data.cooldowns().entrySet())yaml.set(p+".cooldowns."+e.getKey(),e.getValue());}
        for(SpyOperation v:operations.values()){String p="operations."+v.id();yaml.set(p+".attacker-id",v.attackerTownId().toString());yaml.set(p+".attacker-name",v.attackerName());yaml.set(p+".target-id",v.targetTownId().toString());yaml.set(p+".target-name",v.targetName());yaml.set(p+".actor-id",v.actorId().toString());yaml.set(p+".actor-name",v.actorName());yaml.set(p+".type",v.type());yaml.set(p+".started-at",v.startedAt());yaml.set(p+".completes-at",v.completesAt());yaml.set(p+".success-chance",v.successChance());yaml.set(p+".detection-chance",v.detectionChance());yaml.set(p+".cost",v.cost());yaml.set(p+".status",v.status().name());yaml.set(p+".detected",v.detected());}
        for(Map.Entry<UUID,List<IntelReport>> entry:reports.entrySet())for(IntelReport v:entry.getValue()){String p="reports."+entry.getKey()+"."+v.id();yaml.set(p+".target-id",v.targetTownId().toString());yaml.set(p+".target-name",v.targetName());yaml.set(p+".type",v.type());yaml.set(p+".created-at",v.createdAt());yaml.set(p+".expires-at",v.expiresAt());yaml.set(p+".lines",v.lines());yaml.set(p+".read",v.read());}
        try{yaml.save(file);dirty=false;}catch(IOException exception){plugin.getLogger().severe("Не удалось сохранить data.yml: "+exception.getMessage());}
    }
}
