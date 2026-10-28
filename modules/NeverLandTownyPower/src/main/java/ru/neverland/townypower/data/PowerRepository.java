package ru.neverland.townypower.data;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townypower.config.PowerSettings;
import ru.neverland.townypower.model.TownPowerState;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
public final class PowerRepository {
    private final Path file;private final ru.neverland.core.DurableSnapshots writes;private boolean writable,busy;private Map<UUID,TownPowerState> towns=Map.of();
    private java.util.concurrent.CompletionStage<Void> pending=java.util.concurrent.CompletableFuture.completedFuture(null),closing;
    public PowerRepository(Path file){this.file=file;writes=new ru.neverland.core.DurableSnapshots(file,"NeverLand-power-write",2);}public Map<UUID,TownPowerState> towns(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return towns;} }
    public boolean busy(){try(var guard=ru.neverland.core.SuiteState.guard()){return busy;}}
    public void load()throws Exception{ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        writable=false;if(!Files.exists(file)){towns=Map.of();ru.neverland.core.AtomicFiles.loaded(file);writable=true;return;}
        var y=new YamlConfiguration();y.load(file.toFile());long schema=PowerSettings.number(y.get("schema"));if((schema!=1&&schema!=2)||!y.isConfigurationSection("towns"))throw new IOException("Неверная схема power-data.yml");
        Map<UUID,TownPowerState> next=new HashMap<>();var root=y.getConfigurationSection("towns");
        for(String id:root.getKeys(false)){var t=root.getConfigurationSection(id);if(t==null||!(t.get("stopped") instanceof List<?> raw)||!t.isConfigurationSection("priorities"))throw new IOException("Неверная запись города");
            Set<String> stopped=new HashSet<>();for(var v:raw)if(!(v instanceof String name)||!stopped.add(name))throw new IOException("Неверный список остановленных зданий");
            Map<String,Integer> priorities=priorities(t,"priorities"),emergency=Map.of();long charge=0;if(schema==2){if(!t.isConfigurationSection("emergency-priorities")||!t.contains("charge"))throw new IOException("Отсутствует заряд или аварийная очерёдность");emergency=priorities(t,"emergency-priorities");charge=PowerSettings.number(t.get("charge"));}next.put(UUID.fromString(id),new TownPowerState(stopped,priorities,emergency,charge));}
        if(next.size()>10000)throw new IOException("Слишком много сохранённых энергосетей");
        towns=Map.copyOf(next);ru.neverland.core.AtomicFiles.loaded(file);writable=true;
    } }
    private static Map<String,Integer> priorities(org.bukkit.configuration.ConfigurationSection t,String key){var result=new HashMap<String,Integer>();for(String name:t.getConfigurationSection(key).getKeys(false))result.put(name,Math.toIntExact(PowerSettings.number(t.get(key+"."+name))));return result;}
    private static String encode(Map<UUID,TownPowerState> image){
        var y=new YamlConfiguration();y.set("schema",2);y.createSection("towns");for(var e:image.entrySet()){String k="towns."+e.getKey()+".";y.set(k+"stopped",e.getValue().stopped().stream().sorted().toList());y.createSection(k+"priorities",e.getValue().priorities());y.createSection(k+"emergency-priorities",e.getValue().emergencyPriorities());y.set(k+"charge",e.getValue().charge());}return y.saveToString();
    }
    public java.util.concurrent.CompletionStage<Void> replaceAsync(Map<UUID,TownPowerState> next){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(!writable||closing!=null||!ru.neverland.core.AtomicFiles.writable(file))return java.util.concurrent.CompletableFuture.failedFuture(new IOException("Хранилище сети недоступно или останавливается"));
        if(busy)return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Предыдущее изменение ещё сохраняется"));
        var image=Map.copyOf(next);if(image.size()>10000)return java.util.concurrent.CompletableFuture.failedFuture(new IOException("Слишком много энергосетей"));if(image.equals(towns))return java.util.concurrent.CompletableFuture.completedFuture(null);
        busy=true;pending=writes.submit(()->encode(image)).handle((id,error)->{try(var committed=ru.neverland.core.SuiteState.guard()){busy=false;if(error!=null)throw new java.util.concurrent.CompletionException(error);towns=image;return null;}});return pending;
    }}
    /** Offline compatibility helper. Server work must use the acknowledged asynchronous form. */
    public void replace(Map<UUID,TownPowerState> next)throws IOException{
        if(ru.neverland.core.SuiteState.held())throw new IllegalStateException("Нельзя ждать диск под общей блокировкой");
        try{replaceAsync(next).toCompletableFuture().join();}catch(java.util.concurrent.CompletionException error){throw new IOException("Не удалось сохранить энергосеть",error.getCause());}
    }
    public java.util.concurrent.CompletionStage<Void> prepareClose(){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(closing!=null)return closing;
        closing=pending.thenCompose(unused->{Map<UUID,TownPowerState> image;try(var capture=ru.neverland.core.SuiteState.guard()){image=towns;}return writes.finish(()->encode(image)).thenApply(id->(Void)null);});return closing;
    }}
    public void awaitClosed(){
        var receipt=prepareClose();if(ru.neverland.core.SuiteState.held()){if(receipt.toCompletableFuture().isDone()){receipt.toCompletableFuture().join();return;}throw new IllegalStateException("Сначала завершите запись энергосети вне общей блокировки");}
        try{receipt.toCompletableFuture().get(10,java.util.concurrent.TimeUnit.SECONDS);if(!writes.drain(java.time.Duration.ofSeconds(10)))throw new IllegalStateException("Энергосеть ещё сохраняется");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Сохранение энергосети прервано",error);}catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException error){throw new IllegalStateException("Сохранение энергосети не подтверждено",error);}
    }
}
