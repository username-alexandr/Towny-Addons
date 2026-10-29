package ru.neverland.townyelections;
import java.nio.file.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
import org.bukkit.configuration.file.YamlConfiguration;
/** Optional acknowledged sidecar. Original ballots and their schema remain independent. */
public final class ElectionProgramsRepository {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();private final Path file;private final AcknowledgedState<ElectionPrograms.State> state;private volatile boolean readable;
    public ElectionProgramsRepository(Path file){this.file=file;state=new AcknowledgedState<>(file,ElectionPrograms.State.empty(),ElectionProgramsRepository::encode,"NeverLand-election-programs-write");}
    public void load()throws Exception{readable=false;ElectionPrograms.State value=ElectionPrograms.State.empty();if(!Files.notExists(file)){if(Files.size(file)>64*1024*1024)throw new java.io.IOException("Слишком большой журнал программ");var yaml=SafeYaml.load(file);SafeYaml.keys(yaml,"schema","programs");if(SafeYaml.integer(yaml,"schema")!=1)throw new java.io.IOException("Неизвестный формат программ");value=JSON.fromJson(SafeYaml.text(yaml,"programs"),ElectionPrograms.State.class);if(value==null)throw new java.io.IOException("Пустой журнал программ");}state.loaded(value);readable=true;}
    private static String encode(ElectionPrograms.State value)throws java.io.IOException{var y=new YamlConfiguration();y.set("schema",1);y.set("programs",JSON.toJson(value));String text=y.saveToString();if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>64*1024*1024)throw new java.io.IOException("Журнал программ заполнен");return text;}
    public boolean readable(){return readable;}public boolean writable(){return readable&&state.writable();}public boolean busy(){return state.busy();}public ElectionPrograms.State state(){return state.get();}public CompletionStage<ElectionPrograms.State> commit(ElectionPrograms.State before,ElectionPrograms.State next){if(!writable())return CompletableFuture.failedFuture(new IllegalStateException("Программы временно недоступны; бюллетени сохранены"));return state.commit(before,next);}public CompletionStage<Void> prepareClose(){return state.prepareClose();}public void awaitClosed(){state.awaitClosed();}
}
