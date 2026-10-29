package ru.neverland.townysieges;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
public final class RaidRepairsStoreSmoke {
    static int checks;static void ok(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}static <T>T get(CompletionStage<T> f)throws Exception{return f.toCompletableFuture().get(10,TimeUnit.SECONDS);}static void close(RaidStore s)throws Exception{s.close();get(s.stopped());}
    static void denied(CompletionStage<?> f)throws Exception{try{get(f);throw new AssertionError("accepted stale repair");}catch(ExecutionException expected){if(!(expected.getCause() instanceof IllegalArgumentException))throw expected;checks++;}}
    public static void main(String[] args)throws Exception{
        var path=Files.createTempDirectory("raid-repair").resolve("test.db");var s=new RaidStore(path,e->{throw new AssertionError(e);});get(s.ready());UUID town=UUID.randomUUID(),att=UUID.randomUUID(),actor=UUID.randomUUID(),world=UUID.randomUUID();var key=new RaidStore.Key(world,15,100,16);var second=new RaidStore.Key(world,16,100,16);long now=System.currentTimeMillis();
        var hits=get(s.damage(UUID.randomUUID(),actor.toString(),att,town,now,1800,List.of(new RaidStore.Hit(key,town,"STONE",1,400,400,"town_hall"))));get(s.finish(hits.get(0),true));var damage=new BuildingDamageStore(s);var row=get(damage.list(town,"town_hall")).get(0);ok(row.remaining()==0,"destroyed registered block remains in durable building damage ledger");ok(get(s.health(key,"STONE",400,now+3600000,1800)).hp()==0,"building health never heals for free after timeout");
        var cell=new RepairPlans.Cell(key,"STONE","minecraft:stone","");var plan=new RepairPlans.Plan(town,world,"town_hall","Ратуша",2,100000,false,"layout",List.of(cell,new RepairPlans.Cell(second,"STONE","minecraft:stone","")));var cells=List.of(new RaidRepairsStore.Cell(cell,row.version(),row.remaining(),row.maximum(),true));var store=new RaidRepairsStore(s);
        var job=get(store.create(actor,plan,800,400,50000,100000,cells));ok(job.cents()==50000&&job.state().equals("PAYING"),"50 percent damage persists exact half-price bank intent");denied(store.create(actor,plan,800,400,50000,100000,cells));UUID id=job.id();close(s);
        s=new RaidStore(path,e->{throw new AssertionError(e);});get(s.ready());store=new RaidRepairsStore(s);job=get(store.get(id));ok(job.state().equals("REVIEW_BANK"),"restart during bank call requires review, never automatic re-charge");ok(job.cells().equals(cells),"exact repair targets, metadata and versions survive restart");
        job=get(store.resolve(id,"paid","admin","confirmed town bank receipt"));get(store.begin(id,cells));ok(get(store.steps(id)).get(key.id()).equals("APPLYING"),"world intent durable before placing a block");close(s);
        s=new RaidStore(path,e->{throw new AssertionError(e);});get(s.ready());store=new RaidRepairsStore(s);ok(get(store.get(id)).state().equals("PAID"),"confirmed payment persists for world recovery");ok(get(store.steps(id)).get(key.id()).equals("APPLYING"),"interrupted world write remains distinguishable");
        get(store.applied(id,cells));get(store.applied(id,cells));ok(get(new BuildingDamageStore(s).list(town,"town_hall")).isEmpty(),"repair acknowledgement clears damage exactly once");ok(get(store.steps(id)).get(key.id()).equals("DONE"),"completed cell not repeated");job=get(store.change(id,"PAID","DONE","restored"));ok(get(store.list(true)).isEmpty(),"completed repairs leave retry queue");denied(store.create(actor,plan,800,400,50000,100000,cells));
        get(s.damage(UUID.randomUUID(),actor.toString(),att,town,now,1800,List.of(new RaidStore.Hit(second,town,"STONE",1,400,100,"town_hall"))));get(s.damage(UUID.randomUUID(),actor.toString(),att,town,now+3600000,1800,List.of(new RaidStore.Hit(second,town,"STONE",2,400,100,"town_hall"))));ok(get(s.health(second,"STONE",400,now+3600001,1800)).hp()==200,"new hit retains old partial building damage past regeneration timeout");get(s.reset(second));ok(get(new BuildingDamageStore(s).list(town,"town_hall")).isEmpty(),"manual block replacement clears its old raid damage");
        get(s.damage(UUID.randomUUID(),actor.toString(),att,town,now,1800,List.of(new RaidStore.Hit(second,town,"STONE",3,400,200,"town_hall"))));
        var newDamage=get(new BuildingDamageStore(s).list(town,"town_hall")).get(0);var newCell=new RepairPlans.Cell(second,"STONE","minecraft:stone","");
        var newCells=List.of(new RaidRepairsStore.Cell(newCell,newDamage.version(),newDamage.remaining(),newDamage.maximum(),false));
        var pending=get(store.create(actor,plan,800,200,25000,100000,newCells,true));UUID pendingId=pending.id();
        ok(pending.treasury()&&pending.state().equals("TREASURY_PENDING"),"async route and exact amount durable before bank call");denied(store.begin(pendingId,newCells));close(s);
        s=new RaidStore(path,e->{throw new AssertionError(e);});get(s.ready());store=new RaidRepairsStore(s);pending=get(store.get(pendingId));
        ok(pending.treasury()&&pending.state().equals("TREASURY_PENDING")&&pending.cents()==25000&&pending.actor().equals(actor),"restart retains same Treasury ID, actor, amount and protocol");
        denied(store.resolve(pendingId,"paid","admin","bank checked but no receipt"));
        get(store.change(pendingId,"TREASURY_PENDING","TREASURY_REVIEW","unknown bank outcome"));close(s);
        s=new RaidStore(path,e->{throw new AssertionError(e);});get(s.ready());store=new RaidRepairsStore(s);
        ok(get(store.get(pendingId)).state().equals("TREASURY_REVIEW"),"Treasury review persists without converting to legacy admin override");denied(store.resolve(pendingId,"retry","admin","do not charge another ID"));
        get(store.funding(pendingId,RepairTreasury.Outcome.PAID));
        ok(get(store.funding(pendingId,RepairTreasury.Outcome.PAID)).state().equals("PAID"),"duplicate payment ACK is idempotent while repair applies");
        ok(get(store.funding(pendingId,RepairTreasury.Outcome.REVIEW)).state().equals("PAID"),"stale review callback cannot rewind paid repair");get(store.begin(pendingId,newCells));get(store.applied(pendingId,newCells));get(store.change(pendingId,"PAID","DONE","restored"));
        ok(get(store.funding(pendingId,RepairTreasury.Outcome.PAID)).state().equals("DONE"),"late payment ACK cannot reopen completed world work");
        denied(store.funding(id,RepairTreasury.Outcome.PAID));
        ok(get(store.get(pendingId)).treasury()&&get(store.list(true)).isEmpty(),"paid async repair completes and retains protocol evidence");close(s);
        System.out.println("RaidRepairsStoreSmoke PASS: "+checks+" assertions; real SQLite, half-price payment, crash states, no replay, durable HP");
    }
}
