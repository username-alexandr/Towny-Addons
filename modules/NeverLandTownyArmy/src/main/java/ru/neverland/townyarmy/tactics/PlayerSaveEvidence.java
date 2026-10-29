package ru.neverland.townyarmy.tactics;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.PlayerDataMarkers;

/** Exact vanilla save destination and durable operation marker. File verification never occupies a Folia region. */
public class PlayerSaveEvidence implements AutoCloseable {
    public record Prepared(Path file,UUID owner,NamespacedKey key,String marker) {}
    private final NamespacedKey key;
    private final ThreadPoolExecutor worker;
    private final CompletableFuture<Void> closed=new CompletableFuture<>();
    public PlayerSaveEvidence(JavaPlugin plugin){
        key=new NamespacedKey(plugin,"army_kit_receipt");
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),task->{var thread=new Thread(task,plugin.getName()+"-inventory-proof");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){closed.complete(null);}};
    }
    /** Resolve the actual PlayerDataStorage before touching inventory; unsupported mappings fail safely. */
    public Prepared prepare(Player player,UUID receipt)throws IOException{
        try{
            Object playerList=Bukkit.getServer().getClass().getMethod("getHandle").invoke(Bukkit.getServer());
            Object storage=playerList.getClass().getField("playerIo").get(playerList);
            Field directoryField=storage.getClass().getDeclaredField("playerDir");directoryField.setAccessible(true);
            Object directory=directoryField.get(storage);if(!(directory instanceof File folder))throw new IOException("Unsupported player data directory");
            return new Prepared(folder.toPath().toAbsolutePath().normalize().resolve(player.getUniqueId()+".dat"),player.getUniqueId(),key,receipt.toString());
        }catch(ReflectiveOperationException|RuntimeException error){throw new IOException("Нельзя подтвердить путь сохранения игрока: предметы не перемещены",error);}
    }
    /** Must run on the owning player thread, immediately after the full inventory plan was applied. */
    public CompletionStage<Boolean> saveAndVerify(Player player,Prepared proof){
        if(!player.getUniqueId().equals(proof.owner()))throw new IllegalArgumentException("Wrong inventory proof owner");
        player.getPersistentDataContainer().set(proof.key(),PersistentDataType.STRING,proof.marker());
        if(ru.neverland.core.SuiteState.held())throw new IllegalStateException("Сохранение игрока должно выполняться вне общего журнала");
        player.saveData(); // Bukkit may silently fail; its return is deliberately NOT an acknowledgement.
        return CompletableFuture.supplyAsync(()->{try{return PlayerDataMarkers.verify(proof.file(),proof.owner(),proof.key().toString(),proof.marker());}catch(IOException error){throw new CompletionException(error);}},worker);
    }
    public CompletionStage<Boolean> verify(Prepared proof){return CompletableFuture.supplyAsync(()->{try{return PlayerDataMarkers.verify(proof.file(),proof.owner(),proof.key().toString(),proof.marker());}catch(IOException error){throw new CompletionException(error);}},worker);}
    public CompletionStage<Void> prepareClose(){worker.shutdown();return closed;}
    public void awaitClosed(){if(ru.neverland.core.SuiteState.held())throw new IllegalStateException("Ожидайте сохранение игрока вне общей блокировки");try{if(!worker.awaitTermination(10,TimeUnit.SECONDS))throw new IllegalStateException("Подтверждение сохранения игрока ещё выполняется");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}
    @Override public void close(){worker.shutdown();}
}
