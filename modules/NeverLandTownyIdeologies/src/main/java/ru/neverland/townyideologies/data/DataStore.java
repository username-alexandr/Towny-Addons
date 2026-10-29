package ru.neverland.townyideologies.data;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townyideologies.model.*;
import ru.neverland.townyideologies.reforms.ReformBook;
import static ru.neverland.townyideologies.reforms.ReformBook.*;

/** Legacy towns.yml migrates on the first acknowledged mutation, with active terms and payment state in one file. */
public final class DataStore {
    private final Path file;private final AcknowledgedState<State> data;private boolean ready;
    public DataStore(JavaPlugin plugin){this(plugin.getDataFolder().toPath().resolve("towns.yml"));}
    public DataStore(Path file){this.file=file;data=new AcknowledgedState<>(file,ReformBook.empty(),DataStore::encode,"NeverLand-ideology-reform-write");}
    public void load(){ready=false;try{if(Files.exists(file)&&Files.size(file)>32L*1024*1024)throw new IOException("Слишком большой журнал идеологий");State loaded=decode(SafeYaml.load(file));data.loaded(loaded);ready=true;}catch(IOException error){throw new java.io.UncheckedIOException(error);}}
    public State state(){if(!ready)throw new IllegalStateException("Хранилище идеологий не загружено");return data.get();}
    public Optional<TownIdeology> get(UUID id){return Optional.ofNullable(state().towns().get(id));}
    public boolean writable(){return ready&&data.writable();}
    public boolean busy(){return data.busy();}
    public CompletionStage<State> commit(State before,State next){return data.commit(before,next);}
    public CompletionStage<Void> prepareClose(){return data.prepareClose();}
    public void awaitClosed(){data.awaitClosed();}
    public static String encode(State state)throws IOException{
        var y=new YamlConfiguration();y.set("schema",2);var towns=y.createSection("towns");for(var e:new TreeMap<>(state.towns()).entrySet())writeTown(towns.createSection(e.getKey().toString()),e.getValue());
        var root=y.createSection("reforms");for(var e:new TreeMap<>(state.reforms()).entrySet()){
            var r=e.getValue();var s=root.createSection(e.getKey().toString());s.set("actor",r.actor().toString());s.set("town",r.town().toString());if(r.previous()!=null)writeTown(s.createSection("previous"),r.previous());writeTown(s.createSection("target"),r.target());s.set("cents",r.cents());s.set("stage-millis",r.stages());s.set("worked-millis",r.worked());s.set("phase",r.phase().name());s.set("paused",r.paused());s.set("created",r.created());s.set("finished",r.finished());var attempts=s.createSection("attempts");for(var a:r.attempts()){var row=attempts.createSection(a.id().toString());row.set("actor",a.actor().toString());row.set("result",a.result());row.set("at",a.at());}
        }
        String result=y.saveToString();if(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32L*1024*1024)throw new IOException("Журнал идеологий заполнен");return result;
    }
    public static State decode(ConfigurationSection y){
        long schema=SafeYaml.longValue(y,"schema",1);if(schema!=1&&schema!=2)throw new IllegalArgumentException("Неизвестная схема идеологий");if(schema==1)SafeYaml.keys(y,"towns");else SafeYaml.keys(y,"schema","towns","reforms");
        var towns=new HashMap<UUID,TownIdeology>();var root=SafeYaml.section(y,"towns");if(root!=null)for(String id:root.getKeys(false)){UUID town=UUID.fromString(id);towns.put(town,readTown(town,section(root,id)));}
        var reforms=new HashMap<UUID,Reform>();if(schema==2){root=section(y,"reforms");for(String id:root.getKeys(false)){var s=section(root,id);UUID town=UUID.fromString(SafeYaml.text(s,"town"));var previous=SafeYaml.section(s,"previous");var stages=new ArrayList<Long>();for(Object n:SafeYaml.list(s,"stage-millis")){if(!(n instanceof Integer||n instanceof Long))throw new IllegalArgumentException("Неверный срок этапа");stages.add(((Number)n).longValue());}var attempts=new ArrayList<Attempt>();var a=section(s,"attempts");for(String key:a.getKeys(false)){var row=section(a,key);attempts.add(new Attempt(UUID.fromString(key),UUID.fromString(SafeYaml.text(row,"actor")),SafeYaml.text(row,"result"),SafeYaml.integer(row,"at")));}UUID key=UUID.fromString(id);reforms.put(key,new Reform(key,UUID.fromString(SafeYaml.text(s,"actor")),previous==null?null:readTown(town,previous),readTown(town,section(s,"target")),SafeYaml.integer(s,"cents"),stages,SafeYaml.integer(s,"worked-millis"),Phase.valueOf(SafeYaml.text(s,"phase")),SafeYaml.booleanValue(s,"paused",false),SafeYaml.integer(s,"created"),SafeYaml.integer(s,"finished"),attempts));}}
        return new State(towns,reforms);
    }
    private static ConfigurationSection section(ConfigurationSection s,String key){var child=SafeYaml.section(s,key);if(child==null)throw new IllegalArgumentException("Нет раздела "+key);return child;}
    private static TownIdeology readTown(UUID id,ConfigurationSection s){
        PolicyTerms terms=null;var p=SafeYaml.section(s,"terms");if(p!=null){var numbers=new HashMap<String,Double>();var n=section(p,"numbers");for(String key:n.getKeys(false))numbers.put(key,SafeYaml.doubleValue(n,key));terms=new PolicyTerms(SafeYaml.text(p,"name"),SafeYaml.text(p,"scope"),SafeYaml.text(p,"government"),numbers,SafeYaml.strings(p,"effects"));}
        return new TownIdeology(id,SafeYaml.stringValue(s,"town-name","Неизвестный город"),SafeYaml.text(s,"ideology"),SafeYaml.intValue(s,"level",1),SafeYaml.longValue(s,"selected-at",0),terms);
    }
    private static void writeTown(ConfigurationSection s,TownIdeology t){
        s.set("town-name",t.townName());s.set("ideology",t.ideologyId());s.set("level",t.level());s.set("selected-at",t.selectedAt());if(t.terms()==null)return;var terms=t.terms();var p=s.createSection("terms");p.set("name",terms.name());p.set("scope",terms.scope());p.set("government",terms.government());p.createSection("numbers",new TreeMap<>(terms.numbers()));p.set("effects",terms.effects());
    }
}
