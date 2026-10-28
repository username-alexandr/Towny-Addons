package ru.neverland.townypower;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
import ru.neverland.townypower.model.*;
import ru.neverland.townypower.data.PowerRepository;
/** Conservation, actual durability failures, migration, expiry and no restart accrual. */
public final class EnergyStorageSmoke {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    interface Attempt{void run()throws Exception;}
    static void rejects(Attempt test,String why)throws Exception{try{test.run();}catch(Exception expected){checks++;return;}throw new AssertionError(why);}
    static PowerProfile profile(String id,long output,long demand,int priority){return new PowerProfile(id,id,"REDSTONE",true,priority,List.of(output),List.of(demand));}
    static PowerEngine.Building built(){return new PowerEngine.Building(1,true,true,1);}
    public static void main(String[] args)throws Exception{
        var profiles=Map.of("generator",profile("generator",100,0,50),"water",profile("water",0,70,10),"forge",profile("forge",0,80,50));
        var buildings=Map.of("generator",built(),"water",built(),"forge",built());
        var storage=new Accumulator(Map.of("generator",List.of(10000L)),200,200);var empty=TownPowerState.empty();
        var first=storage.plan(profiles,buildings,empty,0,5);check(first.state().charge()==0&&first.grid().supplied()==70&&first.discharged()==0,"first start cannot invent saved charge");
        var charged=storage.plan(profiles,Map.of("generator",built()),empty,500,5);check(charged.state().charge()==500&&charged.charged()==500,"only completed live production can charge");
        var lease=storage.plan(profiles,buildings,charged.state(),0,5);check(lease.grid().supplied()==150&&lease.grid().backup()==50&&lease.discharged()==250&&lease.state().charge()==250,"backup prepays exact shortfall for the full next period");
        check(lease.grid().buildings().get("forge").status()==PowerEngine.Status.BACKUP,"backup supply is explained in building status");
        var emptyAfter=storage.plan(profiles,buildings,lease.state(),0,5);check(emptyAfter.state().charge()==0&&emptyAfter.discharged()==250,"second period consumes remaining saved energy exactly");
        check(storage.plan(profiles,buildings,emptyAfter.state(),0,5).grid().supplied()==70,"depleted reserve cannot grant another powered period");
        var emergency=storage.plan(profiles,buildings,empty.emergency("forge",0),0,5);check(emergency.grid().buildings().get("forge").powered()&&!emergency.grid().buildings().get("water").powered(),"emergency order reallocates direct power during shortage");
        check(PowerEngine.calculate(profiles,buildings,empty.priority("forge",0).emergency("water",0)).buildings().get("forge").powered(),"ties in explicit priorities are deterministic by project id");
        var adequate=Map.of("generator",new PowerEngine.Building(1,true,true,2),"water",built(),"forge",built());check(storage.plan(profiles,adequate,empty.emergency("forge",0),0,5).grid().backup()==0,"sufficient generation never spends saved charge");
        check(storage.plan(profiles,buildings,empty.charge(9990),500,5).charged()==10,"charge capacity caps actual intake");
        var locked=storage.plan(profiles,Map.of("water",built()),empty.charge(20000),0,5);check(locked.state().charge()==20000&&locked.discharged()==0&&locked.capacity()==0,"missing hardware locks rather than destroys saved stock");
        var off=storage.plan(profiles,buildings,empty.charge(1000).stop("generator",true),0,5);check(off.grid().generation()==0&&off.grid().supplied()==150&&off.discharged()==750,"switching off production leaves the separate maintained battery available");
        var unpaid=new HashMap<>(buildings);unpaid.put("generator",new PowerEngine.Building(1,true,false,1));check(storage.plan(profiles,unpaid,empty.charge(1000),0,5).discharged()==0,"unmaintained storage is not available");
        var rate=new Accumulator(storage.capacities(),200,10);check(rate.plan(profiles,buildings,empty.charge(1000),0,5).grid().backup()==0,"discharge rate cannot be bypassed to supply a whole large consumer");
        long now=123456789000L;var window=PowerWindow.start(now,100,5);check(window.live(now)&&!window.live(now+5_000_000_000L),"prepaid period expires exactly at monotonic boundary");check(window.completedEnergy(now+4_999_999_999L)==0&&window.completedEnergy(now+5_000_000_000L)==500,"unfinished window cannot credit production");
        check(window.acknowledged(now+2_000_000_000L).completedEnergy(now+5_000_000_000L)==300,"disk acknowledgement delay never produces phantom energy");check(window.acknowledged(now+6_000_000_000L).completedEnergy(now+60_000_000_000L)==0,"fully delayed acknowledgement cannot charge at all");
        check(window.completedEnergy(now+100_000_000_000L)==500,"late next tick does not backfill idle lag time");check(window.completedEnergy(now-1)==0&&!window.live(now-1),"time reversal cannot refill or reopen a period");
        var random=new Random(71238);long balance=0,credited=0,debited=0;
        for(int n=0;n<10000;n++){long income=random.nextInt(700);var step=storage.plan(profiles,random.nextBoolean()?buildings:Map.of("generator",built()),empty.charge(balance),income,1+random.nextInt(60));credited+=step.charged();debited+=step.discharged();balance=step.state().charge();if(balance<0||balance>10000||balance!=credited-debited||step.grid().supplied()>step.grid().generation()+step.grid().backup())throw new AssertionError("energy conservation at "+n);}
        check(true,"10000 variable intervals preserve source-backed energy conservation");
        var folder=Files.createTempDirectory("energy-store-");var path=folder.resolve("power-data.yml");UUID city=UUID.randomUUID();Files.writeString(path,"schema: 1\ntowns:\n  "+city+":\n    stopped: [generator]\n    priorities: {forge: 10}\n");var repo=new PowerRepository(path);repo.load();check(repo.towns().get(city).charge()==0&&repo.towns().get(city).stopped().contains("generator"),"schema 1 migrates controls and starts without free energy");
        var state=empty.charge(741).emergency("forge",0);repo.replace(Map.of(city,state));repo.awaitClosed();var restarted=new PowerRepository(path);restarted.load();check(restarted.towns().get(city).equals(state)&&Files.readString(path).contains("schema: 2"),"charge and emergency controls survive acknowledged save and restart");
        check(!Files.readString(path).contains("expires")&&!Files.readString(path).contains("opened"),"process-local production/backup lease is never restored from disk");
        try(var guard=SuiteState.guard()){rejects(()->restarted.replace(Map.of()),"waiting for disk under suite lock must be refused");}
        // Hold the real per-path file writer. A blocked disk cannot stall unrelated suite work or publish charge.
        var writers=AtomicFiles.class.getDeclaredField("writers");writers.setAccessible(true);@SuppressWarnings("unchecked")var locks=(java.util.concurrent.ConcurrentMap<Path,java.util.concurrent.locks.ReentrantLock>)writers.get(null);var lock=locks.computeIfAbsent(path.toAbsolutePath().normalize(),p->new java.util.concurrent.locks.ReentrantLock());lock.lock();CompletionStage<Void> pending;
        try{
            pending=restarted.replaceAsync(Map.of(city,state.charge(500)));try(var guard=SuiteState.guard()){check(restarted.towns().get(city).charge()==741&&!pending.toCompletableFuture().isDone(),"unacknowledged debit is not published and suite ledger stays usable");}
            check(restarted.replaceAsync(Map.of()).toCompletableFuture().isCompletedExceptionally(),"second mutation cannot overwrite a pending energy reservation");
        }finally{lock.unlock();}
        pending.toCompletableFuture().get(3,TimeUnit.SECONDS);check(restarted.towns().get(city).charge()==500,"durable debit publishes after file acknowledgement");
        Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("keep"),"original");rejects(()->restarted.replace(Map.of(city,state.charge(100))),"real replacement failure must reject new state");check(restarted.towns().get(city).charge()==500,"failed persistence preserves prior confirmed charge");
        rejects(()->new Accumulator(Map.of("generator",List.of(10L,5L)),1,1),"decreasing capacities rejected");rejects(()->empty.charge(-1),"negative charge rejected");rejects(()->empty.emergency("forge",101),"emergency range checked");
        System.out.println("EnergyStorageSmoke PASS "+checks+" checks, including 10000 conserved energy periods");
    }
}
