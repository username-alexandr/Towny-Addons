package ru.neverland.mintexpeditions.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;

/** Local adaptation of Trade CaravanEffects: old schema/IDs, asynchronous ACK before every effect. */
public final class ExpeditionEffects {
    private final Path file;private final AcknowledgedState<Map<UUID,EffectJournal.Entry>> data;
    public ExpeditionEffects(Path file){this.file=file;data=new AcknowledgedState<>(file,Map.of(),ExpeditionEffects::encode,"nlt-expedition-effects");}
    public void load()throws IOException{
        if(SuiteState.held())throw new IllegalStateException("Чтение платежей должно быть вне общей блокировки");
        var result=new LinkedHashMap<UUID,EffectJournal.Entry>();
        if(Files.exists(file)){
            if(Files.size(file)>32L*1024*1024)throw new IOException("Журнал платежей больше 32 МиБ");
            var y=new YamlConfiguration();try{y.load(file.toFile());}catch(org.bukkit.configuration.InvalidConfigurationException error){throw new IOException("Повреждён YAML наград экспедиций",error);}if(y.getInt("schema")!=1||!y.isConfigurationSection("effects"))throw new IOException("Неизвестная схема платежей");
            var root=y.getConfigurationSection("effects");if(root.getKeys(false).size()>100_000)throw new IOException("Слишком много платежей");
            try{for(String raw:root.getKeys(false)){UUID id=UUID.fromString(raw);result.put(id,new EffectJournal.Entry(id,root.getString(raw+".description"),EffectJournal.State.valueOf(root.getString(raw+".state"))));}}catch(RuntimeException ex){throw new IOException("Повреждены награды экспедиций",ex);}
        }data.loaded(Map.copyOf(result));
    }
    public boolean writable(){return data.writable();}public boolean busy(){return data.busy();}
    public Collection<EffectJournal.Entry> entries(){return List.copyOf(data.get().values());}
    public EffectJournal.State state(UUID id){var e=data.get().get(id);return e==null?EffectJournal.State.READY:e.state();}
    private CompletionStage<Map<UUID,EffectJournal.Entry>> put(EffectJournal.Entry entry){try(var guard=SuiteState.guard()){
        var before=data.get();if(!before.containsKey(entry.id())&&before.size()>=100_000)return CompletableFuture.failedFuture(new IllegalStateException("Журнал платежей достиг предела; нужна проверка администратора"));
        var next=new LinkedHashMap<>(before);next.put(entry.id(),entry);return data.commit(before,Map.copyOf(next));
    }}
    public CompletionStage<Boolean> executeAsync(UUID id,String description,Supplier<CompletionStage<Boolean>> external){try(var guard=SuiteState.guard()){
        var old=data.get().get(id);if(old!=null&&!old.description().equals(description))return CompletableFuture.failedFuture(new IllegalArgumentException("ID платежа занят другими условиями"));
        if(state(id)==EffectJournal.State.DONE)return CompletableFuture.completedFuture(true);
        if(state(id)==EffectJournal.State.PENDING)return CompletableFuture.failedFuture(new IllegalStateException("Платёж "+id+" требует сверки"));
        return put(new EffectJournal.Entry(id,description,EffectJournal.State.PENDING)).thenCompose(ignored->{
            try{return external.get();}catch(Exception ex){return CompletableFuture.failedFuture(ex);}
        }).thenCompose(applied->put(new EffectJournal.Entry(id,description,applied?EffectJournal.State.DONE:EffectJournal.State.READY)).thenApply(ignored->applied));
    }}
    public CompletionStage<Void> resolveAsync(UUID id,boolean applied){try(var guard=SuiteState.guard()){
        var old=data.get().get(id);if(old==null||old.state()!=EffectJournal.State.PENDING)return CompletableFuture.failedFuture(new IllegalArgumentException("Нет платежа для сверки"));
        return put(new EffectJournal.Entry(id,old.description(),applied?EffectJournal.State.DONE:EffectJournal.State.READY)).thenApply(x->null);
    }}
    public CompletionStage<Void> review(Map<UUID,String> descriptions){try(var guard=SuiteState.guard()){
        var before=data.get();var next=new LinkedHashMap<>(before);descriptions.forEach((id,description)->next.putIfAbsent(id,new EffectJournal.Entry(id,description,EffectJournal.State.PENDING)));
        if(next.size()>100_000)return CompletableFuture.failedFuture(new IllegalStateException("Слишком много платежей"));return data.commit(before,Map.copyOf(next)).thenApply(x->null);
    }}
    public CompletionStage<Void> retainAsync(Set<UUID> needed){try(var guard=SuiteState.guard()){
        var before=data.get();var next=new LinkedHashMap<>(before);next.entrySet().removeIf(e->!needed.contains(e.getKey())&&e.getValue().state()!=EffectJournal.State.PENDING);return data.commit(before,Map.copyOf(next)).thenApply(x->null);
    }}
    // Offline migration/test compatibility. Runtime always uses the asynchronous methods.
    public boolean execute(UUID id,String description,EffectJournal.Action action)throws Exception{requireDetached();return await(executeAsync(id,description,()->{try{return CompletableFuture.completedFuture(action.apply());}catch(Exception e){return CompletableFuture.failedFuture(e);}}));}
    public void resolve(UUID id,boolean applied)throws IOException{requireDetached();awaitIo(resolveAsync(id,applied));}
    public void review(UUID id,String description)throws IOException{requireDetached();awaitIo(review(Map.of(id,description)));}
    public void retain(Set<UUID> needed)throws IOException{requireDetached();awaitIo(retainAsync(needed));}
    private static void requireDetached(){if(SuiteState.held())throw new IllegalStateException("Синхронные платежи допустимы только вне Suite");}
    private static <T>T await(CompletionStage<T> stage)throws Exception{if(SuiteState.held())throw new IllegalStateException("Ожидайте запись вне Suite");return stage.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    private static void awaitIo(CompletionStage<?> stage)throws IOException{try{await(stage);}catch(Exception e){throw new IOException(e);}}
    public CompletionStage<Void> prepareClose(){return data.prepareClose();}
    private static String encode(Map<UUID,EffectJournal.Entry> entries)throws IOException{var y=new YamlConfiguration();y.set("schema",1);y.createSection("effects");entries.values().stream().sorted(Comparator.comparing(e->e.id().toString())).forEach(e->{String p="effects."+e.id();y.set(p+".description",e.description());y.set(p+".state",e.state().name());});String value=y.saveToString();if(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32L*1024*1024)throw new IOException("Журнал наград достиг 32 МиБ");return value;}
}
