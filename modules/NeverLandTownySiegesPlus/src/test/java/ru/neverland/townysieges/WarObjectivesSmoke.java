package ru.neverland.townysieges;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** SQL-backed goal, authority, restart and late-loot checks. */
public final class WarObjectivesSmoke {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static <T> T done(CompletionStage<T> work)throws Exception{return work.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    private static void denied(CompletionStage<?> work)throws Exception{try{done(work);throw new AssertionError("accepted invalid goal");}catch(ExecutionException expected){checks++;}}
    private static RaidStore.Raid raid(long now){return new RaidStore.Raid(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),now-5000,now-4000,now+600000,now+86400000,"OPEN");}
    private static WarCaptureStore.Capture captured(RaidStore.Raid r,int x){return new WarCaptureStore.Capture(new WarCaptureStore.Key(r.id(),UUID.randomUUID(),x,0),r.attacker(),r.defender(),r.actor(),"HOLD",System.currentTimeMillis(),"CAPTURED");}
    private static void close(RaidStore store)throws Exception{store.close();done(store.stopped());}
    public static void main(String[] args)throws Exception{
        var errors=new ArrayList<Throwable>();Path path=Files.createTempDirectory("war-objectives-").resolve("raids.db");long now=System.currentTimeMillis();
        var store=new RaidStore(path,errors::add);done(store.ready());var captures=new WarCaptureStore(store);
        var chunks=raid(now);done(store.start(chunks,"CHUNKS",2));
        denied(store.victory(chunks.id(),chunks.attacker(),chunks.actor(),now));
        denied(store.victory(chunks.id(),chunks.defender(),chunks.actor(),now));
        done(captures.capture(captured(chunks,1)));check(done(store.objective(chunks.id())).achieved()==1,"one stored capture is visible");
        denied(store.victory(chunks.id(),chunks.attacker(),chunks.actor(),now));
        close(store);store=new RaidStore(path,errors::add);done(store.ready());captures=new WarCaptureStore(store);
        var goal=done(store.objective(chunks.id()));check(goal.kind().equals("CHUNKS")&&goal.required()==2&&goal.achieved()==1,"frozen objective and progress survive restart");
        done(captures.capture(captured(chunks,2)));done(store.victory(chunks.id(),chunks.attacker(),chunks.actor(),now));
        check(done(store.raids()).stream().anyMatch(r->r.id().equals(chunks.id())&&r.state().equals("VICTORY")&&r.next()==chunks.next()),"victory preserves cooldown");
        check(done(captures.load()).isEmpty(),"victory releases all captured chunks in its commit");
        denied(store.victory(chunks.id(),chunks.attacker(),chunks.actor(),now));
        var core=raid(now);done(store.start(core,"CORE",1));
        var loot=new RaidLootStore(store);var permit=new RaidController.Permit(core.actor(),core.attacker(),core.defender(),core.id(),now,false);
        var receipt=done(loot.arm(permit,new RaidStore.Key(UUID.randomUUID(),1,2,3),new byte[0],new LootSettings(true,0,0)));
        check(receipt.state().equals("ARMED"),"live raid admits one core receipt");
        denied(store.victory(core.id(),core.attacker(),core.actor(),now));
        // The real settlement drives this durable state; fixture confirms the read-only victory condition.
        var database=store;done(store.submit(()->{try(var p=database.connection().prepareStatement("UPDATE raid_loot SET state='DONE' WHERE id=?")){p.setString(1,receipt.id().toString());p.executeUpdate();}return null;}));
        check(done(store.objective(core.id())).achieved()==1,"only completed core settlement satisfies objective");
        done(store.victory(core.id(),core.attacker(),core.actor(),now));
        denied(loot.arm(permit,new RaidStore.Key(UUID.randomUUID(),4,5,6),new byte[0],new LootSettings(true,0,0)));
        var legacy=raid(now);done(store.start(legacy));denied(store.victory(legacy.id(),legacy.attacker(),legacy.actor(),now));
        check(errors.isEmpty()&&store.healthy(),"business denials do not poison storage");close(store);
        System.out.println("WarObjectivesSmoke PASS "+checks+" checks");
    }
}
