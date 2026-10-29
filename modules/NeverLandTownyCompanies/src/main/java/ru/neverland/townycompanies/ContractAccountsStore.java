package ru.neverland.townycompanies;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import static ru.neverland.townycompanies.ContractAccounting.*;

/** A separate, bounded, immutable accounting image; never changes company funds or payment phases. */
public final class ContractAccountsStore {
    private final Path file;private final AcknowledgedState<State> images;
    public ContractAccountsStore(Path file){this.file=file;images=new AcknowledgedState<>(file,State.empty(),ContractAccountsStore::encode,"NeverLand-contract-accounts");}
    public State state(){return images.get();}public boolean writable(){return images.writable();}public boolean busy(){return images.busy();}
    public void load()throws IOException{
        if(Files.exists(file)&&Files.size(file)>16*1024*1024)throw new IOException("Слишком большой файл учёта договоров");
        try{var y=SafeYaml.load(file);if(!Files.exists(file)){images.loaded(State.empty());return;}SafeYaml.keys(y,"schema","revision","reports","allocations");if(SafeYaml.longValue(y,"schema")!=1)throw new IOException("Неизвестная версия учёта договоров");var reports=new LinkedHashMap<UUID,Report>();var allocations=new LinkedHashMap<UUID,Allocation>();
            var root=SafeYaml.section(y,"reports");if(root!=null)for(String key:root.getKeys(false)){var s=SafeYaml.section(root,key);SafeYaml.keys(s,"company","town","contract","title","target","goal","reward","budget-materials","budget-wages","budget-taxes","materials","material-note","actor","updated","revision");UUID id=UUID.fromString(key);reports.put(id,new Report(id,uuid(s,"company"),uuid(s,"town"),uuid(s,"contract"),SafeYaml.stringValue(s,"title"),SafeYaml.stringValue(s,"target"),Math.toIntExact(SafeYaml.longValue(s,"goal")),SafeYaml.longValue(s,"reward"),new Budget(optional(s,"budget-materials"),optional(s,"budget-wages"),optional(s,"budget-taxes")),optional(s,"materials"),SafeYaml.stringValue(s,"material-note"),uuid(s,"actor"),SafeYaml.longValue(s,"updated"),SafeYaml.longValue(s,"revision")));}
            root=SafeYaml.section(y,"allocations");if(root!=null)for(String key:root.getKeys(false)){var s=SafeYaml.section(root,key);SafeYaml.keys(s,"report","payment","cost","cents","actor","created","reversed");UUID id=UUID.fromString(key);allocations.put(id,new Allocation(id,uuid(s,"report"),uuid(s,"payment"),Cost.valueOf(SafeYaml.stringValue(s,"cost")),SafeYaml.longValue(s,"cents"),uuid(s,"actor"),SafeYaml.longValue(s,"created"),SafeYaml.booleanValue(s,"reversed")));}
            images.loaded(new State(reports,allocations,SafeYaml.longValue(y,"revision")));
        }catch(RuntimeException error){throw new IOException("Учёт договоров повреждён; восстановите файл перед запуском раздела",error);}
    }
    private static Long optional(ConfigurationSection s,String key){return s.contains(key)?SafeYaml.longValue(s,key):null;}
    private static UUID uuid(ConfigurationSection s,String key){return UUID.fromString(SafeYaml.stringValue(s,key));}
    private static String encode(State state){var y=new YamlConfiguration();y.set("schema",1);y.set("revision",state.revision());y.createSection("reports");y.createSection("allocations");
        for(var r:state.reports().values().stream().sorted(Comparator.comparing(Report::id)).toList()){var s=y.createSection("reports."+r.id());s.set("company",r.company().toString());s.set("town",r.town().toString());s.set("contract",r.contract().toString());s.set("title",r.title());s.set("target",r.target());s.set("goal",r.goal());s.set("reward",r.reward());s.set("budget-materials",r.budget().materials());s.set("budget-wages",r.budget().wages());s.set("budget-taxes",r.budget().taxes());s.set("materials",r.declaredMaterials());s.set("material-note",r.materialNote());s.set("actor",r.actor().toString());s.set("updated",r.updated());s.set("revision",r.revision());}
        for(var a:state.allocations().values().stream().sorted(Comparator.comparing(Allocation::id)).toList()){var s=y.createSection("allocations."+a.id());s.set("report",a.report().toString());s.set("payment",a.payment().toString());s.set("cost",a.cost().name());s.set("cents",a.cents());s.set("actor",a.actor().toString());s.set("created",a.created());s.set("reversed",a.reversed());}String encoded=y.saveToString();if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024)throw new IllegalStateException("Слишком большой снимок затрат");return encoded;
    }
    public CompletionStage<State> commit(State expected,State next){return images.commit(expected,next);}
    public CompletionStage<Void> prepareClose(){return images.prepareClose();}public void awaitClosed(){images.awaitClosed();}
}
