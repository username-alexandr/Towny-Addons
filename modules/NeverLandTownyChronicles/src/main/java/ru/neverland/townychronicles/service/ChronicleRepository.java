package ru.neverland.townychronicles.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.model.ChronicleEntry;
import ru.neverland.townychronicles.model.TownChronicleState;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChronicleRepository {
    private boolean ready;
    private java.util.concurrent.CompletionStage<Void> closing;
    private void loaded(){ru.neverland.core.AtomicFiles.loaded(file.toPath());ready=true;}
    private void gate(){if(!ready||closing!=null||!ru.neverland.core.AtomicFiles.writable(file.toPath()))throw new IllegalStateException("Хранилище заблокировано или останавливается; новые изменения не принимаются");}

    private final JavaPlugin plugin;private final File file;private final Map<UUID,TownChronicleState>states=new LinkedHashMap<>();private final Map<UUID,List<ChronicleEntry>>entries=new LinkedHashMap<>();private long revision,submittedRevision=-1;private final java.util.concurrent.atomic.AtomicLong savedRevision=new java.util.concurrent.atomic.AtomicLong();private java.util.concurrent.CompletionStage<Long> pending;private final ru.neverland.core.DurableSnapshots writes;
    public ChronicleRepository(JavaPlugin plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"chronicles.yml");writes=new ru.neverland.core.DurableSnapshots(file.toPath(),"NeverLand-chronicles-write",4);}
    public void load(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {ready=false;states.clear();entries.clear();YamlConfiguration yaml=ru.neverland.core.SafeYaml.load(file.toPath());ru.neverland.core.SafeYaml.keys(yaml,"towns");ConfigurationSection root=ru.neverland.core.SafeYaml.section(yaml,"towns");if(root==null){loaded();return;}for(String raw:root.getKeys(false))try{UUID id=UUID.fromString(raw);String base="towns."+raw;TownChronicleState state=new TownChronicleState(id);state.name(ru.neverland.core.SafeYaml.stringValue(yaml,base+".state.name",""));state.mayor(ru.neverland.core.SafeYaml.stringValue(yaml,base+".state.mayor-name",""),uuid(ru.neverland.core.SafeYaml.stringValue(yaml,base+".state.mayor-id")));state.nation(ru.neverland.core.SafeYaml.stringValue(yaml,base+".state.nation-name",""),uuid(ru.neverland.core.SafeYaml.stringValue(yaml,base+".state.nation-id")));state.residents(ru.neverland.core.SafeYaml.intValue(yaml,base+".state.residents"));state.blocks(ru.neverland.core.SafeYaml.intValue(yaml,base+".state.blocks"));state.balance(ru.neverland.core.SafeYaml.doubleValue(yaml,base+".state.balance"));state.foundedAt(ru.neverland.core.SafeYaml.longValue(yaml,base+".state.founded-at"));state.lastSeen(ru.neverland.core.SafeYaml.longValue(yaml,base+".state.last-seen"));state.active(ru.neverland.core.SafeYaml.booleanValue(yaml,base+".state.active",true));state.achievements().addAll(ru.neverland.core.SafeYaml.strings(yaml,base+".state.achievements"));ConfigurationSection wonders=ru.neverland.core.SafeYaml.section(yaml,base+".state.wonders");if(wonders!=null)for(String project:wonders.getKeys(false))state.wonders().put(project,ru.neverland.core.SafeYaml.intValue(wonders,project));states.put(id,state);ConfigurationSection values=ru.neverland.core.SafeYaml.section(yaml,base+".entries");List<ChronicleEntry>list=new ArrayList<>();if(values!=null)for(String entryId:values.getKeys(false))try{String p=base+".entries."+entryId;list.add(new ChronicleEntry(UUID.fromString(entryId),id,ru.neverland.core.SafeYaml.stringValue(yaml,p+".town-name",state.name()),ChronicleCategory.valueOf(ru.neverland.core.SafeYaml.stringValue(yaml,p+".category","CUSTOM")),ru.neverland.core.SafeYaml.longValue(yaml,p+".timestamp"),ru.neverland.core.SafeYaml.stringValue(yaml,p+".title","Событие"),ru.neverland.core.SafeYaml.strings(yaml,p+".details"),ru.neverland.core.SafeYaml.stringValue(yaml,p+".actor",""),ru.neverland.core.SafeYaml.stringValue(yaml,p+".source","")));}catch(Exception exception){throw new IllegalArgumentException("Повреждены сохранённые данные: ChronicleRepository");}list.sort(Comparator.comparingLong(ChronicleEntry::timestamp).reversed());entries.put(id,list);}catch(Exception exception){throw new IllegalArgumentException("Повреждены сохранённые данные: ChronicleRepository");}revision=0;savedRevision.set(0);loaded();} }
    public TownChronicleState state(UUID townId){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {gate();return states.computeIfAbsent(townId,id->{revision++;return new TownChronicleState(id);});} }public List<TownChronicleState>states(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return List.copyOf(states.values());} }
    public void add(ChronicleEntry entry){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {gate();List<ChronicleEntry>list=entries.computeIfAbsent(entry.townId(),id->new ArrayList<>());list.add(0,entry);int maximum=Math.max(10,plugin.getConfig().getInt("storage.maximum-entries-per-town",1000));while(list.size()>maximum)list.remove(list.size()-1);revision++;} }
    public List<ChronicleEntry>entries(UUID townId){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return List.copyOf(entries.getOrDefault(townId,List.of()));} }public List<ChronicleEntry>entries(UUID townId,ChronicleCategory category){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return entries(townId).stream().filter(v->category==null||v.category()==category).toList();} }
    public ChronicleEntry latest(UUID townId){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {gate();List<ChronicleEntry>list=entries.get(townId);return list==null||list.isEmpty()?null:list.get(0);} }public boolean remove(UUID id){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {gate();for(List<ChronicleEntry>list:entries.values())if(list.removeIf(v->v.id().equals(id))){revision++;return true;}return false;} }
    public ChronicleEntry find(String id){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {gate();String lower=id.toLowerCase();for(List<ChronicleEntry>list:entries.values())for(ChronicleEntry entry:list)if(entry.id().toString().equalsIgnoreCase(id)||entry.id().toString().startsWith(lower))return entry;return null;} }
    public void markDirty(){try(var guard=ru.neverland.core.SuiteState.guard()){gate();revision++;}}
    public void saveIfDirty(){requestSave();}
    /** Compatibility entry point: schedules a durable write; use saveAsync for its acknowledgement. */
    public void save(){requestSave();}
    private record Image(Map<String,Object> fields,List<ChronicleEntry> events){}
    private List<Image> snapshot(){
        var images=new ArrayList<Image>();
        for(TownChronicleState state:states.values()){
            var fields=new LinkedHashMap<String,Object>();String base="towns."+state.townId();
            fields.put(base+".state.name",state.name());fields.put(base+".state.mayor-name",state.mayorName());fields.put(base+".state.mayor-id",string(state.mayorId()));
            fields.put(base+".state.nation-name",state.nationName());fields.put(base+".state.nation-id",string(state.nationId()));
            fields.put(base+".state.residents",state.residents());fields.put(base+".state.blocks",state.blocks());fields.put(base+".state.balance",state.balance());
            fields.put(base+".state.founded-at",state.foundedAt());fields.put(base+".state.last-seen",state.lastSeen());fields.put(base+".state.active",state.active());
            fields.put(base+".state.achievements",state.achievements().stream().sorted().toList());
            state.wonders().forEach((project,level)->fields.put(base+".state.wonders."+project,level));
            images.add(new Image(java.util.Collections.unmodifiableMap(fields),List.copyOf(entries.getOrDefault(state.townId(),List.of()))));
        }
        return List.copyOf(images);
    }
    private static String encode(List<Image> images){
        var yaml=new YamlConfiguration();
        for(var image:images){image.fields().forEach(yaml::set);for(var entry:image.events()){
            String p="towns."+entry.townId()+".entries."+entry.id();yaml.set(p+".town-name",entry.townName());yaml.set(p+".category",entry.category().name());
            yaml.set(p+".timestamp",entry.timestamp());yaml.set(p+".title",entry.title());yaml.set(p+".details",entry.details());yaml.set(p+".actor",entry.actor());yaml.set(p+".source",entry.source());
        }}
        return yaml.saveToString();
    }
    public java.util.concurrent.CompletionStage<Long> saveAsync(){try(var guard=ru.neverland.core.SuiteState.guard()){
        gate();long version=revision;
        if(pending!=null&&submittedRevision==version&&!pending.toCompletableFuture().isCompletedExceptionally())return pending;
        var image=snapshot();submittedRevision=version;
        pending=writes.submit(()->encode(image)).thenApply(receipt->{savedRevision.accumulateAndGet(version,Math::max);return version;});
        return pending;
    }}
    public void requestSave(){try(var guard=ru.neverland.core.SuiteState.guard()){
        gate();if(revision<=savedRevision.get())return;
        saveAsync().whenComplete((v,error)->{if(error!=null)plugin.getLogger().log(java.util.logging.Level.SEVERE,"Летопись не сохранена; подтверждение не выдано, данные остаются в памяти",error);});
    }}
    /** Control awaits this receipt before asking Paper to close the plugin classloader. */
    public java.util.concurrent.CompletionStage<Void> prepareClose(){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(closing!=null)return closing;gate();var image=snapshot();closing=writes.finish(()->encode(image)).thenApply(id->null);return closing;
    }}
    /** Server shutdown calls this without an outer ledger guard. Control arrives with prepareClose already done. */
    public void close(){
        var done=prepareClose();
        try{
            if(ru.neverland.core.SuiteState.held()){
                // Legacy callers that directly disable a plugin must not unload classes before its final receipt.
                // The supported Control path prepares first and never waits here.
                done.toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);
            }else{
                if(!writes.drain(java.time.Duration.ofSeconds(5)))throw new IllegalStateException("Летопись ещё записывается; дождитесь завершения перед повторным запуском");
                done.toCompletableFuture().join();
            }
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Запись летописи прервана",error);}
        catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException error){throw new IllegalStateException("Не подтверждено финальное сохранение летописи",error);}
    }
    private UUID uuid(String value){try{return value==null?null:UUID.fromString(value);}catch(IllegalArgumentException exception){throw new IllegalArgumentException("Повреждён UUID летописи",exception);}}private String string(UUID value){return value==null?null:value.toString();}
}
