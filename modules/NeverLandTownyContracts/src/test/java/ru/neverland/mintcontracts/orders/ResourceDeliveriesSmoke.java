package ru.neverland.mintcontracts.orders;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.earnings.RewardLedger;

/** Actual SQLite + reward files with faults at the inventory/database/payment boundaries. */
public final class ResourceDeliveriesSmoke {
    private static int checks;
    private static String owner="test";
    private static final Deque<Runnable> playerTasks=new ConcurrentLinkedDeque<>(),ledgerTasks=new ConcurrentLinkedDeque<>();
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static <T> T await(CompletionStage<T> value)throws Exception{return value.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    private static final class Scheduler implements ResourceDeliveries.Owner {
        final Deque<Runnable> tasks;final String name;Scheduler(Deque<Runnable> tasks,String name){this.tasks=tasks;this.name=name;}
        public <T> CompletionStage<T> call(Callable<T> call){var result=new CompletableFuture<T>();tasks.add(()->{owner=name;try{result.complete(call.call());}catch(Throwable error){result.completeExceptionally(error);}finally{owner="test";}});return result;}
    }
    private static final Scheduler PLAYER=new Scheduler(playerTasks,"player"),LEDGER=new Scheduler(ledgerTasks,"ledger");
    private static final class Inventory implements ResourceDeliveries.Inventory {
        int count=64,takes;boolean marker,saved,failSave,allowed=true;
        public <T> CompletionStage<T> call(Callable<T> operation){return PLAYER.call(operation);}
        private void owned(){if(!owner.equals("player"))throw new AssertionError("inventory read off player scheduler");}
        public void check(ServerMaterials.Delivery d){owned();if(!allowed||count<d.amount())throw new IllegalArgumentException("action not allowed / inventory changed");}
        public void takeAndSave(ServerMaterials.Delivery d)throws Exception{owned();count-=d.amount();takes++;marker=true;if(failSave)throw new java.io.IOException("save outcome uncertain");saved=true;}
        public boolean hasReceipt(ServerMaterials.Delivery d){owned();return marker;}
        public void proveReceipt(ServerMaterials.Delivery d){owned();if(!marker)throw new IllegalStateException("no receipt");saved=true;}
    }
    private static <T> T finish(CompletionStage<T> job)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!job.toCompletableFuture().isDone()&&System.nanoTime()<end){Runnable r;while((r=playerTasks.poll())!=null)r.run();while((r=ledgerTasks.poll())!=null)r.run();Thread.sleep(1);}
        return await(job);
    }
    private static void fails(CompletionStage<?> job,String why)throws Exception{try{finish(job);}catch(ExecutionException expected){checks++;return;}throw new AssertionError(why);}
    private static ServerMaterials open(Path file)throws Exception{var result=new ServerMaterials(file);await(result.ready());return result;}
    private static void close(ServerMaterials materials)throws Exception{materials.close();await(materials.closed());}
    private static RewardLedger rewards(Path file,long limit)throws Exception{var rewards=new RewardLedger(file,limit,1000000);rewards.load();return rewards;}
    private static ServerMaterials.Delivery delivery(){return new ServerMaterials.Delivery(UUID.randomUUID(),UUID.randomUUID(),"oak_delivery","OAK_LOG",64,1000,"COMMON",System.currentTimeMillis());}
    private static long stock(ServerMaterials materials)throws Exception{return await(materials.balances()).stream().mapToLong(ServerMaterials.Balance::available).sum();}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("resource-deliveries-");var db=open(root.resolve("normal.db"));var pay=rewards(root.resolve("rewards"),100000);var flow=new ResourceDeliveries(db,pay,LEDGER);var actor=new Inventory();var d=delivery();
        var normal=flow.deliver(d,actor);ok(!normal.toCompletableFuture().isDone()&&actor.takes==0,"database submission never synchronously touches player inventory");finish(normal);
        ok(actor.count==0&&actor.takes==1&&actor.saved&&stock(db)==64,"one saved removal corresponds to one stock credit");ok(pay.get(d.id()).state()==RewardLedger.State.PENDING,"payout released only after stock commit");
        finish(flow.deliver(d,actor));ok(actor.takes==1&&stock(db)==64,"second claim does not remove resources or add stock twice");
        var deposits=new java.util.concurrent.atomic.AtomicInteger();var bank=new RewardLedger.Bank(){public boolean ready(UUID p){return true;}public boolean deposit(RewardLedger.Entry e){deposits.incrementAndGet();return true;}};
        pay.submit(d.id(),bank);pay.submit(d.id(),bank);ok(deposits.get()==1,"resource delivery leaves exactly one payable bank receipt");
        var failedSave=delivery();var unsaved=new Inventory();unsaved.failSave=true;fails(flow.deliver(failedSave,unsaved),"ambiguous player save cannot pay or credit stock");
        ok(unsaved.takes==1&&stock(db)==64&&pay.get(failedSave.id()).state()==RewardLedger.State.HELD,"uncertain inventory outcome preserves intent and holds reward");
        unsaved.failSave=false;finish(flow.recover(await(db.delivery(failedSave.id())),unsaved));
        ok(unsaved.takes==1&&unsaved.saved&&stock(db)==128,"saved in-memory marker is proven before one recovery credit");
        var noSavedMarker=delivery();var rolledBack=new Inventory();rolledBack.failSave=true;fails(flow.deliver(noSavedMarker,rolledBack),"simulated crash before inventory save");rolledBack.marker=false;rolledBack.saved=false;rolledBack.count=64;
        fails(flow.recover(await(db.delivery(noSavedMarker.id())),rolledBack),"missing persisted marker cannot invent removed resources");
        finish(flow.resolve(noSavedMarker.id(),false,"admin","verified old inventory unchanged"));
        ok(stock(db)==128&&rolledBack.count==64&&pay.get(noSavedMarker.id()).state()==RewardLedger.State.VOID,"untouched decision keeps player items and voids held reward");
        var crash=delivery();await(db.prepare(crash));pay.reserve(crash.id(),crash.player(),crash.cents(),"orders:"+crash.template(),true);await(db.confirm(crash.id(),"saved inventory before process crash"));
        close(db);db=open(root.resolve("normal.db"));pay=rewards(root.resolve("rewards"),100000);flow=new ResourceDeliveries(db,pay,LEDGER);finish(flow.reconcile());finish(flow.reconcile());
        ok(stock(db)==192&&pay.get(crash.id()).state()==RewardLedger.State.PENDING,"restart after stock commit safely releases held payment once");pay.submit(d.id(),bank);ok(deposits.get()==1,"old paid reward cannot replay after database and reward restart");
        var denied=delivery();var deniedActor=new Inventory();deniedActor.allowed=false;fails(flow.deliver(denied,deniedActor),"permission/module change before player handoff stops delivery");ok(deniedActor.count==64&&deniedActor.takes==0&&pay.get(denied.id())==null,"revalidation leaves player resources and budget unchanged");
        var limited=rewards(root.resolve("limited"),100);var capped=new ResourceDeliveries(db,limited,LEDGER);var poor=delivery();var inventory=new Inventory();fails(capped.deliver(poor,inventory),"limit rejected before inventory removal");ok(inventory.count==64&&limited.get(poor.id())==null,"payout cap cannot eat resources");
        close(db);
        Path faultFile=root.resolve("fault.db");db=open(faultFile);var faultRewards=rewards(root.resolve("fault-pay"),100000);flow=new ResourceDeliveries(db,faultRewards,LEDGER);
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+faultFile);var s=c.createStatement()){s.execute("CREATE TRIGGER fail_credit BEFORE INSERT ON balances BEGIN SELECT RAISE(ABORT,'injected write failure'); END");}
        var pending=delivery();var saved=new Inventory();fails(flow.deliver(pending,saved),"database failure after player save holds payout");ok(saved.count==0&&saved.saved&&faultRewards.get(pending.id()).state()==RewardLedger.State.HELD,"saved inventory and held reward survive database fault");close(db);
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+faultFile);var s=c.createStatement();var r=s.executeQuery("SELECT state FROM deliveries")){ok(r.next()&&r.getString(1).equals("PREPARED"),"SQL transaction rolls stock and delivery status back together");}
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+faultFile);var s=c.createStatement()){s.execute("DROP TRIGGER fail_credit");}
        db=open(faultFile);flow=new ResourceDeliveries(db,rewards(root.resolve("fault-pay"),100000),LEDGER);finish(flow.recover(await(db.delivery(pending.id())),saved));ok(stock(db)==64&&saved.takes==1,"repair and restart recover receipt without another inventory debit");close(db);
        ok(playerTasks.isEmpty()&&ledgerTasks.isEmpty(),"all owner handoffs settle; no abandoned duplicate callbacks");
        System.out.println("ResourceDeliveriesSmoke PASS: "+checks+" checks; real SQLite/reward files, player ownership, duplicate clicks, save faults, database rollback, recovery, caps and exactly-once rewards");
    }
}
