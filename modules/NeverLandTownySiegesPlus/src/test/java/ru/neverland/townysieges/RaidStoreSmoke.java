package ru.neverland.townysieges;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class RaidStoreSmoke {
    static int checks;
    static void ok(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    static <T>T get(CompletionStage<T> future)throws Exception{return future.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    static void close(RaidStore store)throws Exception{store.close();get(store.stopped());}
    public static void main(String[] args)throws Exception {
        Path folder=Files.createTempDirectory("raid-db-test"),file=folder.resolve("raids.db");var failures=new AtomicInteger();
        var store=new RaidStore(file,e->failures.incrementAndGet());get(store.ready());
        UUID attacker=UUID.randomUUID(),defender=UUID.randomUUID(),actor=UUID.randomUUID(),world=UUID.randomUUID();long now=System.currentTimeMillis();
        var raid=new RaidStore.Raid(UUID.randomUUID(),attacker,defender,actor,now,now+1000,now+10000,now+20000,"OPEN");
        var active=get(store.start(raid));ok(active.size()==1&&!active.get(0).fighting(now)&&active.get(0).fighting(now+1000),"warmup separates declaration from combat");
        try{get(store.start(new RaidStore.Raid(UUID.randomUUID(),UUID.randomUUID(),defender,actor,now+1,now+2,now+100,now+500,"OPEN")));throw new AssertionError("parallel raid accepted");}
        catch(ExecutionException expected){ok(store.healthy()&&failures.get()==0,"ordinary rejected raid does not disable storage");}
        var key=new RaidStore.Key(world,-17,140,32);
        get(store.owner(key,actor));ok(get(store.owners()).get(key.id()).equals(actor),"TNT placement attribution stored");
        UUID blast=UUID.randomUUID();var hit=new RaidStore.Hit(key,defender,"OAK_DOOR",1,120,100);
        var first=get(store.damage(blast,actor.toString(),attacker,defender,now,1800,List.of(hit)));
        ok(first.size()==1&&first.get(0).hp()==20&&first.get(0).receipt().isEmpty(),"first contact hit leaves door standing with 20 HP");
        ok(get(store.damagedKeys()).contains(key.id()),"damage invalidation index available after restart");
        ok(get(store.damage(blast,actor.toString(),attacker,defender,now,1800,List.of(hit))).isEmpty(),"duplicate blast is idempotent");
        close(store);store=new RaidStore(file,e->failures.incrementAndGet());get(store.ready());
        ok(get(store.health(key,"OAK_DOOR",120,now+10,1800)).hp()==20,"partial damage survives restart");
        ok(get(store.owners()).get(key.id()).equals(actor),"redstone TNT owner survives restart");
        var second=get(store.damage(UUID.randomUUID(),actor.toString(),attacker,defender,now+20,1800,List.of(new RaidStore.Hit(key,defender,"OAK_DOOR",999,120,100))));
        ok(second.get(0).hp()==0&&!second.get(0).receipt().isEmpty(),"second hit reserves destruction despite new process epoch");
        ok(get(store.damage(UUID.randomUUID(),actor.toString(),attacker,defender,now+21,1800,List.of(hit))).isEmpty(),"pending destruction cannot be reserved twice");
        close(store);store=new RaidStore(file,e->failures.incrementAndGet());get(store.ready());
        ok(get(store.health(key,"OAK_DOOR",120,now+25,1800)).hp()==1,"interrupted destruction rearmed only for a new real hit, not replayed");
        get(store.reset(key));ok(get(store.health(key,"OAK_DOOR",120,now+30,1800)).hp()==120,"replacing a block clears its damage");
        var stone=new RaidStore.Hit(key,defender,"STONE",1,400,25);
        get(store.damage(UUID.randomUUID(),actor.toString(),attacker,defender,now+31,1800,List.of(stone)));
        ok(get(store.health(key,"STONE",400,now+40,1800)).hp()==375,"distant blast causes reduced HP loss");
        ok(get(store.health(key,"STONE",400,now+31+1800000L,1800)).hp()==400,"quiet interval restores a surviving block's health");
        get(store.owner(key,null));ok(get(store.owners()).isEmpty(),"primed or removed TNT loses block attribution");
        var ended=get(store.cancel(defender,"admin",now+50));ok(ended.get(0).state().equals("CANCELLED")&&!ended.get(0).live(now+51),"admin cancellation stops raid without defeat");
        try{get(store.start(new RaidStore.Raid(UUID.randomUUID(),attacker,defender,actor,now+60,now+60,now+1000,now+30000,"OPEN")));throw new AssertionError("cooldown bypass");}
        catch(ExecutionException expected){ok(store.healthy(),"defender cooldown persists after cancellation");}
        ok(get(store.history(defender)).stream().anyMatch(a->a.action().equals("DECLARE"))&&get(store.history(defender)).stream().anyMatch(a->a.action().equals("TNT_HIT")),"raid and damage operations audited");
        close(store);ok(failures.get()==0,"no unexpected storage failures");
        Path bad=folder.resolve("not-a-db");Files.createDirectory(bad);var broken=new RaidStore(bad,e->failures.incrementAndGet());
        try{get(broken.ready());throw new AssertionError("bad database opened");}catch(ExecutionException expected){ok(!broken.healthy(),"storage failure closes damage path");}close(broken);
        var saturated=new RaidStore(folder.resolve("busy.db"),e->{});get(saturated.ready());
        var field=RaidStore.class.getDeclaredField("worker");field.setAccessible(true);var worker=(ThreadPoolExecutor)field.get(saturated);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        worker.execute(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        ok(entered.await(5,TimeUnit.SECONDS),"saturation fixture holds only the background storage worker");
        try{
            for(int i=0;i<512;i++)saturated.health(key,"STONE",400,now,1800);
            try{get(saturated.reset(key));throw new AssertionError("unbounded storage queue");}catch(ExecutionException expected){ok(!saturated.healthy(),"queue overflow closes damage path before stale block data can be used");}
        }finally{release.countDown();close(saturated);}
        System.out.println("RaidStoreSmoke PASS: "+checks+" assertions; actual SQLite, crash recovery, replay, cooldown, attribution and audit");
    }
}
