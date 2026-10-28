package ru.neverland.townysieges;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
public final class WarChunksSmoke {
    static int checks;
    static void ok(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    static <T>T get(CompletionStage<T> future)throws Exception{return future.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    static void rejected(CompletionStage<?> future)throws Exception{try{get(future);throw new AssertionError("capture accepted outside battle");}catch(ExecutionException expected){ok(expected.getCause() instanceof IllegalArgumentException,"ordinary capture denial");}}
    public static void main(String[] args)throws Exception{
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var s=ChunkHold.step(null,Set.of(a),false,1000);
        ok(s.millis()==0,"arrival starts with zero observed time");s=ChunkHold.step(s,Set.of(a),false,2000);
        ok(s.millis()==1000&&!ChunkHold.complete(s,5),"consecutive presence accumulates");
        ok(ChunkHold.step(s,Set.of(),false,2100)==null,"departure resets occupation");
        ok(ChunkHold.step(s,Set.of(a),true,2100)==null,"one defender resets occupation");
        ok(ChunkHold.step(s,Set.of(b),false,2100).millis()==0,"disjoint replacement cannot inherit progress");
        var overlap=ChunkHold.step(s,Set.of(a,b),false,3000);ok(overlap.millis()==2000,"overlapping reinforcements retain presence");
        ok(ChunkHold.step(overlap,Set.of(b),false,4000).millis()==3000,"continuous army can hand over control");
        ok(ChunkHold.step(s,Set.of(a),false,5001).millis()==0,"scheduler stall cannot capture a chunk offline");
        ok(ChunkHold.step(s,Set.of(a),false,1500).millis()==0,"backward clock resets progress");
        var capped=ChunkHold.step(s,Set.of(a),false,4400);ok(capped.millis()==2500,"lag interval has a bounded contribution");
        for(long now=3000;now<=6000;now+=1000)s=ChunkHold.step(s,Set.of(a),false,now);
        ok(ChunkHold.complete(s,5),"exact observed duration completes occupation");
        var config=new YamlConfiguration();ok(WarSettings.load(config).holdSeconds()==120&&WarSettings.load(config).enabled(),"default is two minutes");
        for(int n:new int[]{4,3601}){config.set("war-chunks.empty-hold-seconds",n);try{WarSettings.load(config);throw new AssertionError("bad hold time");}catch(IllegalArgumentException expected){checks++;}}
        Path folder=Files.createTempDirectory("war-capture-native-sql");var failures=new ArrayList<Throwable>();var db=new RaidStore(folder.resolve("raids.db"),failures::add);get(db.ready());var store=new WarCaptureStore(db);
        UUID att=UUID.randomUUID(),def=UUID.randomUUID(),actor=UUID.randomUUID(),world=UUID.randomUUID();long now=System.currentTimeMillis();
        var warm=new RaidStore.Raid(UUID.randomUUID(),att,def,actor,now,now+60000,now+120000,now+180000,"OPEN");get(db.start(warm));
        var warmCapture=new WarCaptureStore.Capture(new WarCaptureStore.Key(warm.id(),world,-1,2),att,def,actor,"HOLD",now,"CAPTURED");rejected(store.capture(warmCapture));
        UUID att2=UUID.randomUUID(),def2=UUID.randomUUID();var war=new RaidStore.Raid(UUID.randomUUID(),att2,def2,actor,now-1000,now-500,now+600000,now+86400000,"OPEN");get(db.start(war));
        var key=new WarCaptureStore.Key(war.id(),world,-1,2);var c=new WarCaptureStore.Capture(key,att2,def2,actor,"BUILDING",now,"CAPTURED");
        ok(get(store.capture(c)),"capture committed during actual stored battle");ok(!get(store.capture(c)),"duplicate capture is idempotent");
        ok(get(store.load()).size()==1,"one active chunk loaded");db.close();get(db.stopped());
        db=new RaidStore(folder.resolve("raids.db"),failures::add);get(db.ready());store=new WarCaptureStore(db);
        ok(get(store.load()).get(0).key().equals(key),"captured permission survives storage restart");
        ok(!key.equals(new WarCaptureStore.Key(UUID.randomUUID(),world,-1,2)),"new war cannot inherit an old capture");
        ok(!key.equals(new WarCaptureStore.Key(war.id(),UUID.randomUUID(),-1,2)),"coordinates are scoped to world");
        get(db.cancel(def2,"admin",System.currentTimeMillis()));
        rejected(store.capture(new WarCaptureStore.Capture(new WarCaptureStore.Key(war.id(),world,0,2),att2,def2,actor,"HOLD",System.currentTimeMillis(),"CAPTURED")));
        ok(get(store.release(c,System.currentTimeMillis())),"war ending releases capture durably");ok(!get(store.release(c,System.currentTimeMillis())),"release cannot be recorded twice");
        ok(get(store.load()).isEmpty(),"released chunk no longer grants authority");
        var finalDb=db;int count=get(db.submit(()->{try(var p=finalDb.connection().createStatement();var r=p.executeQuery("SELECT COUNT(*) FROM war_chunk_log")){r.next();return r.getInt(1);}}));
        ok(count==2,"capture and release each have one durable receipt");
        var pending=get(store.pendingLog());ok(pending.size()==2,"committed events wait for admin journal acknowledgement");
        get(store.journalled(pending.stream().map(WarCaptureStore.Log::id).toList()));ok(get(store.pendingLog()).isEmpty(),"only acknowledged events leave the journal queue");
        get(store.event(key,att2,def2,null,"TNT_DENIED","unknown source"));long pendingId=get(store.pendingLog()).get(0).id();
        db.close();get(db.stopped());db=new RaidStore(folder.resolve("raids.db"),failures::add);get(db.ready());store=new WarCaptureStore(db);
        var recovered=get(store.pendingLog());ok(recovered.size()==1&&recovered.get(0).id()==pendingId,"unacknowledged audit keeps stable identity through restart");
        ok(recovered.get(0).actor()==null,"unknown priming source never blames the war commander");
        ok(db.healthy()&&failures.isEmpty(),"validation denial does not disable storage");db.close();get(db.stopped());
        System.out.println("WarChunksSmoke PASS: "+checks+" assertions; occupation, contest, lag, SQLite migration/restart, replay and war isolation");
    }
}
