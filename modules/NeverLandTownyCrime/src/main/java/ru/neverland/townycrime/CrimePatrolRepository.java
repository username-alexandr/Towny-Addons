package ru.neverland.townycrime;
import java.nio.file.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
import org.bukkit.configuration.file.YamlConfiguration;
/** Optional journal; original crime state and resource reservation protocol remain separate. */
public final class CrimePatrolRepository {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();private final Path file;private final AcknowledgedState<CrimePatrols.State> store;private volatile boolean readable;
    public CrimePatrolRepository(Path file){this.file=file;store=new AcknowledgedState<>(file,CrimePatrols.State.empty(),CrimePatrolRepository::encode,"NeverLand-crime-patrol-write");}
    public void load()throws Exception{readable=false;var state=CrimePatrols.State.empty();if(!Files.notExists(file)){if(Files.size(file)>32*1024*1024)throw new java.io.IOException("Журнал патрулей слишком большой");var yaml=SafeYaml.load(file);SafeYaml.keys(yaml,"schema","patrols");if(SafeYaml.integer(yaml,"schema")!=1)throw new java.io.IOException("Неизвестная схема патрулей");state=JSON.fromJson(SafeYaml.text(yaml,"patrols"),CrimePatrols.State.class);if(state==null)throw new java.io.IOException("Пустой журнал патрулей");}store.loaded(state);readable=true;}
    private static String encode(CrimePatrols.State state)throws java.io.IOException{var yaml=new YamlConfiguration();yaml.set("schema",1);yaml.set("patrols",JSON.toJson(state));String value=yaml.saveToString();if(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32*1024*1024)throw new java.io.IOException("Журнал патрулей заполнен");return value;}
    public boolean readable(){return readable;}public boolean writable(){return readable&&store.writable();}public boolean busy(){return store.busy();}public CrimePatrols.State state(){return store.get();}public CompletionStage<CrimePatrols.State> commit(CrimePatrols.State before,CrimePatrols.State next){return !writable()?CompletableFuture.failedFuture(new IllegalStateException("Журнал патрулей недоступен")):store.commit(before,next);}public CompletionStage<Void> prepareClose(){return store.prepareClose();}public void awaitClosed(){store.awaitClosed();}
}
