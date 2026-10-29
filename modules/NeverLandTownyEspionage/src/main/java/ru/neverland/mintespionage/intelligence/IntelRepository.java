package ru.neverland.mintespionage.intelligence;

import java.nio.file.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;

/** Durable outcome and source capture before the legacy mission is marked finished. */
public final class IntelRepository {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private final Path file;private final AcknowledgedState<IntelEvidence.State> store;private volatile boolean readable;
    public IntelRepository(Path file){this.file=file;store=new AcknowledgedState<>(file,IntelEvidence.State.empty(),IntelRepository::encode,"NeverLand-spy-intel-write");}
    public void load()throws Exception{readable=false;var value=IntelEvidence.State.empty();if(!Files.notExists(file)){if(Files.size(file)>32*1024*1024)throw new java.io.IOException("Журнал разведданных слишком большой");var yaml=SafeYaml.load(file);SafeYaml.keys(yaml,"schema","receipts");if(SafeYaml.integer(yaml,"schema")!=1)throw new java.io.IOException("Неверная схема разведданных");value=JSON.fromJson(SafeYaml.text(yaml,"receipts"),IntelEvidence.State.class);if(value==null)throw new java.io.IOException("Пустые разведданные");}store.loaded(value);readable=true;}
    private static String encode(IntelEvidence.State value)throws java.io.IOException{var yaml=new YamlConfiguration();yaml.set("schema",1);yaml.set("receipts",JSON.toJson(value));String result=yaml.saveToString();if(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32*1024*1024)throw new java.io.IOException("Журнал разведданных заполнен");return result;}
    public IntelEvidence.State state(){return store.get();}public boolean readable(){return readable;}public boolean writable(){return readable&&store.writable();}public boolean busy(){return store.busy();}
    public CompletionStage<IntelEvidence.State> commit(IntelEvidence.Receipt receipt){if(!writable())return CompletableFuture.failedFuture(new IllegalStateException("Результаты разведки временно недоступны; начатые задания сохранены"));var before=store.get();return store.commit(before,before.put(receipt));}
    public CompletionStage<IntelEvidence.State> applied(java.util.UUID mission){if(!writable())return CompletableFuture.failedFuture(new IllegalStateException("Журнал разведки недоступен"));var before=store.get();return store.commit(before,before.applied(mission));}
    public CompletionStage<Void> prepareClose(){return store.prepareClose();}public void awaitClosed(){store.awaitClosed();}
}
