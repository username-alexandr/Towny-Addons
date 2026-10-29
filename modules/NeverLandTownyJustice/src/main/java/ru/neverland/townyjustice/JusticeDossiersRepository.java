package ru.neverland.townyjustice;
import java.nio.file.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
import org.bukkit.configuration.file.YamlConfiguration;
/** Optional sidecar, never publishes before durable acknowledgement or resets corrupt data. */
public final class JusticeDossiersRepository {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();private final Path file;private final AcknowledgedState<JusticeDossiers.State> store;private volatile boolean readable;
    public JusticeDossiersRepository(Path file){this.file=file;store=new AcknowledgedState<>(file,JusticeDossiers.State.empty(),JusticeDossiersRepository::encode,"NeverLand-justice-dossiers-write");}
    public void load()throws Exception{readable=false;var value=JusticeDossiers.State.empty();if(!Files.notExists(file)){if(Files.size(file)>32*1024*1024)throw new java.io.IOException("Журнал разбирательств слишком большой");var yaml=SafeYaml.load(file);SafeYaml.keys(yaml,"schema","dossiers");if(SafeYaml.integer(yaml,"schema")!=1)throw new java.io.IOException("Неверный формат разбирательств");value=JSON.fromJson(SafeYaml.text(yaml,"dossiers"),JusticeDossiers.State.class);if(value==null)throw new java.io.IOException("Пустой журнал разбирательств");}store.loaded(value);readable=true;}
    private static String encode(JusticeDossiers.State state)throws java.io.IOException{var yaml=new YamlConfiguration();yaml.set("schema",1);yaml.set("dossiers",JSON.toJson(state));String text=yaml.saveToString();if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32*1024*1024)throw new java.io.IOException("Журнал разбирательств заполнен");return text;}
    public boolean readable(){return readable;}public boolean writable(){return readable&&store.writable();}public boolean busy(){return store.busy();}public JusticeDossiers.State state(){return store.get();}public CompletionStage<JusticeDossiers.State> commit(JusticeDossiers.State expected,JusticeDossiers.State next){return writable()?store.commit(expected,next):CompletableFuture.failedFuture(new IllegalStateException("Разбирательства временно недоступны; прежние дела сохранены"));}public CompletionStage<Void> prepareClose(){return store.prepareClose();}public void awaitClosed(){store.awaitClosed();}
}
