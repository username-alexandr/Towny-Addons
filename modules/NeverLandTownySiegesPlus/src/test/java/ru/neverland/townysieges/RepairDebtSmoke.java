package ru.neverland.townysieges;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real SQLite and held worker: debt must never become a false before its queued mutation. */
public final class RepairDebtSmoke {
    static int checks;
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static <T>T get(CompletionStage<T> value)throws Exception{return value.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    static void denied(Runnable action){try{action.run();throw new AssertionError("Unavailable debt accepted");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        var path=Files.createTempDirectory("repair-debt").resolve("raids.db");
        var storage=new RaidStore(path,e->{throw new AssertionError(e);});get(storage.ready());
        UUID town=UUID.randomUUID(),actor=UUID.randomUUID(),world=UUID.randomUUID();String project="town_hall";
        long clean=storage.repairDebtRevision();ok(!get(storage.hasRepairDebt(town,project)),"clean registered target has no debt");
        ok(storage.repairDebtRevision()==clean,"reading debt does not invalidate its own revision");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var held=storage.submit(()->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("fixture worker not released");return null;});
        ok(entered.await(2,TimeUnit.SECONDS),"real writer held before mutations");
        var key=new RaidStore.Key(world,5,100,5);
        var hit=storage.damage(UUID.randomUUID(),actor.toString(),UUID.randomUUID(),town,10,1800,List.of(new RaidStore.Hit(key,town,"STONE",1,400,400,project)));
        ok(storage.repairDebtRevision()>clean,"damage acceptance invalidates a region proof before SQL runs");
        var debt=storage.hasRepairDebt(town,project);ok(!debt.toCompletableFuture().isDone(),"pending query cannot report clean while writer is blocked");
        release.countDown();get(held);var hitResult=get(hit).get(0);ok(get(debt),"query ordered after accepted damage sees actual durable debt");
        long beforeFinish=storage.repairDebtRevision();get(storage.finish(hitResult,true));ok(storage.repairDebtRevision()>beforeFinish,"physical damage acknowledgement advances revision");
        var row=get(new BuildingDamageStore(storage).list(town,project)).get(0);var cell=new RepairPlans.Cell(key,"STONE","minecraft:stone","");
        var plan=new RepairPlans.Plan(town,world,project,"Ратуша",1,10000,false,"layout",List.of(cell));
        var repairs=new RaidRepairsStore(storage);long beforeCreate=storage.repairDebtRevision();
        var job=get(repairs.create(actor,plan,400,400,10000,100000,List.of(new RaidRepairsStore.Cell(cell,row.version(),0,400,true)),true));
        ok(storage.repairDebtRevision()>beforeCreate&&get(storage.hasRepairDebt(town,project)),"new repair intent participates in debt revision");
        long beforeReset=storage.repairDebtRevision();get(storage.reset(key));ok(storage.repairDebtRevision()>beforeReset,"manual block replacement invalidates prior geometry check");
        ok(get(new BuildingDamageStore(storage).list(town,project)).isEmpty()&&get(storage.hasRepairDebt(town,project)),"pending payment remains debt even after physical damage row is removed");
        long beforeCancel=storage.repairDebtRevision();get(repairs.change(job.id(),"TREASURY_PENDING","CANCELLED","explicit unpaid fixture"));
        ok(storage.repairDebtRevision()>beforeCancel&&!get(storage.hasRepairDebt(town,project)),"terminal unpaid receipt with no damage clears debt atomically");
        long stable=storage.repairDebtRevision();for(int n=0;n<4;n++)get(storage.hasRepairDebt(town,project));ok(storage.repairDebtRevision()==stable,"repeated queries preserve revision");
        final var same=storage;denied(()->same.hasRepairDebt(town," "));ok(storage.healthy(),"invalid input cannot damage the raid journal");
        storage.close();get(storage.stopped());denied(storage::repairDebtRevision);
        try{get(storage.hasRepairDebt(town,project));throw new AssertionError("closed debt read became false");}catch(ExecutionException expected){checks++;}
        storage=new RaidStore(path,e->{throw new AssertionError(e);});get(storage.ready());ok(!get(storage.hasRepairDebt(town,project)),"restart restores exact terminal debt state");
        get(storage.damage(UUID.randomUUID(),actor.toString(),UUID.randomUUID(),town,20,1800,List.of(new RaidStore.Hit(key,town,"STONE",2,400,100,project))));
        ok(get(storage.hasRepairDebt(town,project)),"partial HP damage blocks migration even while material is still present");
        storage.close();get(storage.stopped());storage=new RaidStore(path,e->{throw new AssertionError(e);});get(storage.ready());
        ok(get(storage.hasRepairDebt(town,project))&&!get(storage.hasRepairDebt(UUID.randomUUID(),project)),"restart preserves partial damage and isolates towns");storage.close();get(storage.stopped());
        System.out.println("RepairDebtSmoke PASS "+checks+" checks; actual queued SQLite, pre-enqueue revision, partial damage, pending receipt, closed provider and restart");
    }
}
