package ru.neverland.archaeology.restoration;
import java.nio.file.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
/** Optional independent journal; corrupt bytes never replace the legacy archaeology database. */
public final class RestorationRepository {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private final Path file;private final AcknowledgedState<RestorationBook.State> store;private volatile boolean readable;
    public RestorationRepository(Path file){this.file=file;store=new AcknowledgedState<>(file,RestorationBook.State.empty(),RestorationRepository::encode,"NeverLand-restoration-write");}
    public void load()throws Exception{readable=false;var state=RestorationBook.State.empty();if(!Files.notExists(file)){if(Files.size(file)>64*1024*1024)throw new java.io.IOException("Журнал реставрации слишком большой");var yaml=SafeYaml.load(file);SafeYaml.keys(yaml,"schema","book");if(SafeYaml.integer(yaml,"schema")!=1)throw new java.io.IOException("Неизвестная схема реставрации");state=JSON.fromJson(SafeYaml.text(yaml,"book"),RestorationBook.State.class);if(state==null)throw new java.io.IOException("Пустой журнал реставрации");}store.loaded(state);readable=true;}
    private static String encode(RestorationBook.State state)throws java.io.IOException{var yaml=new YamlConfiguration();yaml.set("schema",1);yaml.set("book",JSON.toJson(state));String text=yaml.saveToString();if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>64*1024*1024)throw new java.io.IOException("Журнал реставрации заполнен");return text;}
    public RestorationBook.State state(){return store.get();}public boolean readable(){return readable;}public boolean writable(){return readable&&store.writable();}public boolean busy(){return store.busy();}
    public CompletionStage<RestorationBook.State> commit(RestorationBook.State expected,RestorationBook.State next){if(!writable())return CompletableFuture.failedFuture(new IllegalStateException("Реставрация временно недоступна; прежние артефакты сохранены"));return store.commit(expected,next);}
    public CompletionStage<Void> prepareClose(){return store.prepareClose();}public void awaitClosed(){store.awaitClosed();}
}
