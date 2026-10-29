package ru.neverland.townycouncil.spending;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import static ru.neverland.townycouncil.spending.SpendingPolicies.*;

/** Separate additive file; limits become observable only after durable ACK. */
public final class SpendingRepository {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private final Path file;private final AcknowledgedState<State> state;private boolean readable;
    public SpendingRepository(Path file){this.file=file;state=new AcknowledgedState<>(file,State.empty(),SpendingRepository::encode,"nlt-council-spending");}
    public State state(){return state.get();}public boolean readable(){return readable;}public boolean writable(){return readable&&state.writable();}public boolean busy(){return state.busy();}
    public void load()throws Exception{
        if(SuiteState.held())throw new IllegalStateException("Ограничения расходов загружаются вне общей блокировки");readable=false;
        if(!Files.exists(file)){state.loaded(State.empty());readable=true;return;}
        if(Files.size(file)>16L*1024*1024)throw new IOException("Реестр ограничений расходов больше 16 МиБ");var y=SafeYaml.load(file);SafeYaml.keys(y,"schema","revision","policies","history");
        if(SafeYaml.integer(y,"schema")!=1||!y.isConfigurationSection("policies")||!y.isString("history"))throw new IOException("Неверный формат ограничений расходов");
        var policies=new HashMap<String,Policy>();var section=y.getConfigurationSection("policies");
        for(String key:section.getKeys(false)){var row=section.getConfigurationSection(key);if(row==null)throw new IOException("Повреждено правило расходов");SafeYaml.keys(row,"town","resident","operation","day","mayor","actor","changed-at","revision");var policy=new Policy(UUID.fromString(SafeYaml.stringValue(row,"town")),UUID.fromString(SafeYaml.stringValue(row,"resident")),new Limits(SafeYaml.integer(row,"operation"),SafeYaml.integer(row,"day")),UUID.fromString(SafeYaml.stringValue(row,"mayor")),SafeYaml.stringValue(row,"actor"),SafeYaml.integer(row,"changed-at"),SafeYaml.integer(row,"revision"));if(!key.equals(policy.key()))throw new IOException("Ключ правила расходов изменён");policies.put(key,policy);}
        var history=JSON.fromJson(y.getString("history"),Entry[].class);if(history==null)throw new IOException("Отсутствует история ограничений расходов");state.loaded(new State(policies,List.copyOf(Arrays.asList(history)),SafeYaml.integer(y,"revision")));readable=true;
    }
    public CompletionStage<State> commit(State before,State next){if(!writable())return CompletableFuture.failedFuture(new IOException("Правила расходов сейчас недоступны"));return state.commit(before,next);}
    public CompletionStage<Void> close(){return state.prepareClose();}
    private static String encode(State state)throws IOException{var y=new YamlConfiguration();y.set("schema",1);y.set("revision",state.revision());y.createSection("policies");new TreeMap<>(state.policies()).forEach((key,p)->{String path="policies."+key+".";y.set(path+"town",p.town().toString());y.set(path+"resident",p.resident().toString());y.set(path+"operation",p.limits().operation());y.set(path+"day",p.limits().day());y.set(path+"mayor",p.mayor().toString());y.set(path+"actor",p.actorName());y.set(path+"changed-at",p.changedAt());y.set(path+"revision",p.revision());});y.set("history",JSON.toJson(state.history()));String text=y.saveToString();if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16L*1024*1024)throw new IOException("Архив ограничений расходов требует проверки");return text;}
}
