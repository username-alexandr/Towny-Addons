package ru.neverland.townychronicles;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townychronicles.service.ChronicleRepository;
import ru.neverland.townychronicles.model.*;

/** Real fsync, queue pressure and recovery; no server or financial action is mocked as successful. */
public final class SnapshotPersistenceSmoke {
    private static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static final class Fixture extends JavaPlugin {
        @Override public Logger getLogger(){return Logger.getLogger("snapshot-test");}
        @Override public YamlConfiguration getConfig(){return new YamlConfiguration();}
    }
    static Fixture plugin(Path directory)throws Exception{
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)field.get(null);var plugin=(Fixture)unsafe.allocateInstance(Fixture.class);
        field=JavaPlugin.class.getDeclaredField("dataFolder");field.setAccessible(true);field.set(plugin,directory.toFile());return plugin;
    }
    static <T>T await(CompletionStage<T> receipt)throws Exception{return receipt.toCompletableFuture().get(4,TimeUnit.SECONDS);}
    public static void main(String[]args)throws Exception{
        Path dir=Files.createTempDirectory("chronicle-snapshot-");Path file=dir.resolve("ordered.yml");AtomicFiles.loaded(file);
        var writer=new DurableSnapshots(file,"snapshot-test",2);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var first=writer.submit(()->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new java.io.IOException("test timeout");}catch(InterruptedException e){throw new java.io.IOException(e);}return "first";});
        check(entered.await(2,TimeUnit.SECONDS),"writer reached disk worker");
        var second=writer.submit(()->"second");var third=writer.submit(()->"third");var rejected=writer.submit(()->"must not write");
        check(rejected.toCompletableFuture().isCompletedExceptionally(),"queue pressure rejects without false acknowledgement");
        check(!first.toCompletableFuture().isDone()&&writer.committed()==0,"pending bytes are not reported as saved");
        try(var guard=SuiteState.guard()){
            release.countDown();await(first);await(second);await(third);
            check(Files.readString(file).equals("third"),"worker saves in order while suite ledger is held elsewhere");
            boolean denied=false;try{writer.drain(Duration.ofSeconds(1));}catch(IllegalStateException expected){denied=true;}
            check(denied,"shutdown cannot wait while holding ledger");
        }
        check(writer.drain(Duration.ofSeconds(2)),"accepted snapshots drain on shutdown");
        check(writer.submit(()->"closed").toCompletableFuture().isCompletedExceptionally(),"closed writer never accepts a new save");
        Path closingFile=dir.resolve("closing.yml");var closing=new DurableSnapshots(closingFile,"snapshot-close",1);
        var block=new CountDownLatch(1);var active=new CountDownLatch(1);
        var old=closing.submit(()->{active.countDown();try{block.await(3,TimeUnit.SECONDS);}catch(InterruptedException error){throw new java.io.IOException(error);}return "old";});active.await(2,TimeUnit.SECONDS);
        closing.submit(()->"queued");
        try(var guard=SuiteState.guard()){
            var finalReceipt=closing.finish(()->"final-shutdown-image");block.countDown();await(finalReceipt);
            check(Files.readString(closingFile).equals("final-shutdown-image"),"full queue still commits final image without acquiring caller's ledger");
        }
        check(closing.drain(Duration.ofSeconds(1)),"final write terminates worker");

        var repo=new ChronicleRepository(plugin(dir));repo.load();UUID town=UUID.randomUUID();var state=repo.state(town);state.name("FirstTown");state.mayor("Mayor",UUID.randomUUID());state.wonders().put("pyramid",2);repo.markDirty();
        var entry=new ChronicleEntry(UUID.randomUUID(),town,"FirstTown",ChronicleCategory.MAYOR,System.currentTimeMillis(),"New mayor",List.of("Detail"),"Mayor","test");repo.add(entry);
        CompletionStage<Long> captured;
        try(var guard=SuiteState.guard()){
            captured=repo.saveAsync();state.name("RenamedAfterCapture");state.wonders().put("pyramid",4);repo.markDirty();await(captured);
        }
        var yaml=SafeYaml.load(dir.resolve("chronicles.yml"));String base="towns."+town;
        check(yaml.getString(base+".state.name").equals("FirstTown"),"snapshot does not read later mutable town state");
        check(yaml.getInt(base+".state.wonders.pyramid")==2,"snapshot deep-copies mutable maps");
        check(yaml.getString(base+".entries."+entry.id()+".title").equals("New mayor"),"entry persisted with stable ID");
        await(repo.saveAsync());repo.close();
        var restored=new ChronicleRepository(plugin(dir));restored.load();
        check(restored.state(town).name().equals("RenamedAfterCapture"),"new revision survives reload");
        check(restored.state(town).wonders().get("pyramid")==4,"latest map revision survives reload");
        check(restored.entries(town).size()==1&&restored.entries(town).get(0).id().equals(entry.id()),"retries do not duplicate chronicle entries");
        restored.remove(entry.id());await(restored.saveAsync());restored.close();
        var deleted=new ChronicleRepository(plugin(dir));deleted.load();check(deleted.entries(town).isEmpty(),"acknowledged removal survives reload");deleted.close();

        var ordered=new ChronicleRepository(plugin(dir.resolve("restored-order")));ordered.load();ordered.state(town);
        var history=new ArrayList<ChronicleEntry>();for(int i=0;i<1005;i++)history.add(new ChronicleEntry(UUID.nameUUIDFromBytes(("history-"+i).getBytes()),town,"Town",ChronicleCategory.CUSTOM,i,"Thanks "+i,List.of(),"Mayor","mayor-commendation"));
        Collections.shuffle(history,new Random(42));for(var row:history)ordered.add(row);ordered.add(history.get(0));
        check(ordered.entries(town).size()==1000&&ordered.latest(town).timestamp()==1004&&ordered.entries(town).get(999).timestamp()==5,"late replay retains newest history in timestamp order without duplicate IDs");
        ordered.close();var orderedReload=new ChronicleRepository(plugin(dir.resolve("restored-order")));orderedReload.load();check(orderedReload.latest(town).timestamp()==1004&&orderedReload.entries(town).get(999).timestamp()==5,"restored history order survives actual file roundtrip");orderedReload.close();

        Path obstacle=dir.resolve("directory-not-file");Files.createDirectory(obstacle);var failing=new DurableSnapshots(obstacle,"snapshot-failure",1);
        boolean failed=false;try{await(failing.submit(()->"data"));}catch(ExecutionException expected){failed=true;}
        check(failed&&!AtomicFiles.writable(obstacle),"write failure closes storage and never issues a successful receipt");
        check(failing.drain(Duration.ofSeconds(1)),"failed worker can shut down cleanly");
        System.out.println("SnapshotPersistenceSmoke PASS "+checks+" checks");
    }
}
