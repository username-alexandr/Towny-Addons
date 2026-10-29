package ru.neverland.townycouncil;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import static ru.neverland.townycouncil.Delegations.*;

/** New acknowledged file; existing council.yml/access.yml formats remain untouched. */
public final class DelegationRepository {
    private final Path file;private final AcknowledgedState<State> images;private boolean readable;
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    public DelegationRepository(Path file){this.file=file;images=new AcknowledgedState<>(file,State.empty(),DelegationRepository::encode,"NeverLand-council-delegations");}
    public State state(){return images.get();}public boolean readable(){return readable;}public boolean writable(){return readable&&images.writable();}public boolean busy(){return images.busy();}
    public void load()throws Exception{readable=false;var grants=new HashMap<String,Grant>();var history=List.<Entry>of();long revision=0;
        if(Files.exists(file)){if(Files.size(file)>8*1024*1024)throw new IOException("Слишком большой реестр временных прав");var y=SafeYaml.load(file);SafeYaml.keys(y,"schema","revision","grants","history");if(SafeYaml.integer(y,"schema")!=1||!y.isConfigurationSection("grants")||!y.isString("history"))throw new IOException("Неизвестная схема временных прав");revision=SafeYaml.integer(y,"revision");var root=y.getConfigurationSection("grants");for(String key:root.getKeys(false)){var r=root.getConfigurationSection(key);if(r==null)throw new IOException("Повреждено поручение");SafeYaml.keys(r,"id","town","resident","mayor","action","granted-at","expires-at","actor","reason","revoked","revision");var grant=new Grant(UUID.fromString(SafeYaml.stringValue(r,"id")),UUID.fromString(SafeYaml.stringValue(r,"town")),UUID.fromString(SafeYaml.stringValue(r,"resident")),UUID.fromString(SafeYaml.stringValue(r,"mayor")),SafeYaml.stringValue(r,"action"),SafeYaml.integer(r,"granted-at"),SafeYaml.integer(r,"expires-at"),SafeYaml.stringValue(r,"actor"),SafeYaml.stringValue(r,"reason"),SafeYaml.booleanValue(r,"revoked"),SafeYaml.integer(r,"revision"));if(!key.equals(grant.key()))throw new IOException("Ключ поручения не совпадает");grants.put(key,grant);}history=List.copyOf(Arrays.asList(JSON.fromJson(y.getString("history"),Entry[].class)));}
        images.loaded(new State(grants,history,revision));readable=true;
    }
    public CompletionStage<State> commit(State expected,State next){if(!writable())return java.util.concurrent.CompletableFuture.failedFuture(new IOException("Запись временных прав приостановлена"));return images.commit(expected,next);}
    public CompletionStage<Void> prepareClose(){return images.prepareClose();}public void awaitClosed(){images.awaitClosed();}
    private static String encode(State state)throws IOException{var y=new YamlConfiguration();y.set("schema",1);y.set("revision",state.revision());y.createSection("grants");new TreeMap<>(state.grants()).forEach((key,g)->{String p="grants."+key+".";y.set(p+"id",g.id().toString());y.set(p+"town",g.town().toString());y.set(p+"resident",g.resident().toString());y.set(p+"mayor",g.mayor().toString());y.set(p+"action",g.action());y.set(p+"granted-at",g.grantedAt());y.set(p+"expires-at",g.expiresAt());y.set(p+"actor",g.actor());y.set(p+"reason",g.reason());y.set(p+"revoked",g.revoked());y.set(p+"revision",g.revision());});y.set("history",JSON.toJson(state.history()));String image=y.saveToString();if(image.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8*1024*1024)throw new IOException("Реестр временных прав требует архивирования");return image;}
}
