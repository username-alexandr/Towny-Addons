package ru.neverland.core;

import io.papermc.paper.threadedregions.scheduler.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.plugin.*;

/** Real cross-thread lock contention with deterministic owner-scheduler queues. */
public final class SuiteSchedulerSmoke {
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    record Item(long delay,Runnable action,Runnable retired) {}
    static final class Queue implements SuiteScheduler.Schedule {
        final Deque<Item> items=new ArrayDeque<>();
        public ScheduledTask later(long delay,Runnable run,Runnable retired){
            items.addLast(new Item(delay,run,retired));var cancelled=new AtomicBoolean();
            return (ScheduledTask)Proxy.newProxyInstance(ScheduledTask.class.getClassLoader(),new Class[]{ScheduledTask.class},(o,m,a)->switch(m.getName()){
                case "cancel"->{cancelled.set(true);yield m.getReturnType().getEnumConstants()[0];}
                case "isCancelled"->cancelled.get();case "isRepeatingTask"->false;default->null;});
        }
        void next(){items.removeFirst().action.run();}
    }
    public static void main(String[] args)throws Exception {
        var enabled=new AtomicBoolean(true);var stopping=new AtomicBoolean();var logger=Logger.getLogger("SuiteSchedulerSmoke");
        Plugin owner=(Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class[]{Plugin.class},(o,m,a)->switch(m.getName()){
            case "getName"->"NeverLandTest";case "isEnabled"->enabled.get();case "getLogger"->logger;case "hashCode"->System.identityHashCode(o);case "equals"->o==a[0];default->null;});
        var global=new Queue();
        GlobalRegionScheduler scheduler=(GlobalRegionScheduler)Proxy.newProxyInstance(GlobalRegionScheduler.class.getClassLoader(),new Class[]{GlobalRegionScheduler.class},(o,m,a)->{
            if(m.getName().equals("runDelayed"))return global.later((Long)a[2],()->((Consumer<ScheduledTask>)a[1]).accept(null),()->{});
            throw new UnsupportedOperationException(m.getName());});
        PluginManager manager=(PluginManager)Proxy.newProxyInstance(PluginManager.class.getClassLoader(),new Class[]{PluginManager.class},(o,m,a)->m.getName().equals("getPlugins")?new Plugin[]{owner}:null);
        Server server=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class[]{Server.class},(o,m,a)->switch(m.getName()){
            case "isStopping"->stopping.get();
            case "getWorlds","getOnlinePlayers","isOwnedByCurrentRegion"->throw new AssertionError("Shutdown must not access world/player state: "+m.getName());
            case "getName","getVersion","getBukkitVersion","getMinecraftVersion"->"test";case "getLogger"->logger;case "getPluginManager"->manager;case "getGlobalRegionScheduler"->scheduler;default->null;});
        var serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);serverField.set(null,server);
        var calls=new AtomicInteger();var origin=new Queue();
        var task=new SuiteScheduler.Task(owner,calls::incrementAndGet,false,0,origin,null).start(1);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        Thread holder=new Thread(()->SuiteState.run(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}),"other-region-holder");
        holder.start();check(entered.await(2,TimeUnit.SECONDS),"holder entered ledger");
        var executor=Executors.newSingleThreadExecutor();
        Item stale=origin.items.getFirst();
        try {
            executor.submit(origin::next).get(2,TimeUnit.SECONDS);
            check(calls.get()==0&&origin.items.size()==1,"busy callback returns without running or losing continuation");
            check(origin.items.getFirst().delay()==1&&global.items.isEmpty(),"retry stays on original owner scheduler");
            for(int i=0;i<20;i++)origin.next();
            check(origin.items.size()==1,"contention retains only one retry");
            check(SuiteState.diagnostics().stream().anyMatch(x->x.contains("other-region-holder")),"diagnostic names actual lock owner without acquiring lock");
        } finally {release.countDown();holder.join(2000);executor.shutdownNow();}
        check(!holder.isAlive(),"holder finishes");origin.next();stale.action.run();
        check(calls.get()==1&&origin.items.isEmpty(),"one-shot runs exactly once; stale callback is ignored");
        try(var outer=SuiteState.guard()){
            check(SuiteState.held(),"outer guard held");SuiteState.run(()->check(SuiteState.held(),"nested guard reentrant"));
            check(SuiteState.tryRun("reentrant",()->check(SuiteState.held(),"nested tryRun held")),"tryRun reentrant");
        }
        try{SuiteState.tryRun("throw",()->{throw new IllegalArgumentException("test");});throw new AssertionError("exception lost");}catch(IllegalArgumentException expected){}
        check(!SuiteState.held(),"throwing callback releases guard");

        var timerQueue=new Queue();var ticks=new AtomicInteger();
        var timer=new SuiteScheduler.Task(owner,ticks::incrementAndGet,false,12,timerQueue,null).start(1);
        timerQueue.next();check(ticks.get()==1&&timerQueue.items.getFirst().delay()==12,"period is rescheduled after completion");
        SuiteState.freeze(owner.getName());timerQueue.next();check(ticks.get()==1&&timerQueue.items.size()==1,"freeze suppresses periodic work without accumulating callbacks");
        SuiteState.thaw(owner.getName());timerQueue.next();check(ticks.get()==2,"periodic work resumes");
        timer.cancel();timerQueue.next();check(ticks.get()==2&&timer.isCancelled(),"cancel suppresses already queued callback");
        var cancelledQueue=new Queue();var cancelled=new SuiteScheduler.Task(owner,calls::incrementAndGet,false,0,cancelledQueue,null).start(1);
        SuiteScheduler.get().cancelTasks(owner);cancelledQueue.next();check(cancelled.isCancelled()&&calls.get()==1,"module shutdown cancels pending one-shot");

        var entity=new Queue();var retiredCalls=new AtomicInteger();
        new SuiteScheduler.Task(owner,()->{throw new AssertionError("retired entity callback ran");},false,0,entity,retiredCalls::incrementAndGet).start(1);
        Item removed=entity.items.removeFirst();removed.retired.run();removed.retired.run();removed.action.run();
        check(global.items.size()==1,"retirement is transferred once to global data cleanup");global.next();
        check(retiredCalls.get()==1&&global.items.isEmpty(),"retired cleanup executes once");
        var disabledQueue=new Queue();new SuiteScheduler.Task(owner,calls::incrementAndGet,false,8,disabledQueue,null).start(1);
        enabled.set(false);disabledQueue.next();check(disabledQueue.items.isEmpty()&&calls.get()==1,"disabled owner stops repeating task");
        enabled.set(true);
        check(WorldTasks.cleanupOwner(owner)==owner,"running server retains cleanup owner for administrative disable");
        var stopQueue=new Queue();var stopCalls=new AtomicInteger();
        var pendingStop=new SuiteScheduler.Task(owner,stopCalls::incrementAndGet,false,10,stopQueue,null).start(1);
        var retiringQueue=new Queue();var retiring=new SuiteScheduler.Task(owner,stopCalls::incrementAndGet,false,0,retiringQueue,stopCalls::incrementAndGet).start(1);
        stopping.set(true);
        stopQueue.next();check(pendingStop.isCancelled()&&stopCalls.get()==0&&stopQueue.items.isEmpty(),"queued timer is cancelled on server stop without another schedule");
        retiringQueue.items.removeFirst().retired.run();check(retiring.isCancelled()&&global.items.isEmpty(),"retirement cannot schedule cleanup during shutdown");
        var blocked=new SuiteScheduler.Task(owner,stopCalls::incrementAndGet,false,0,(d,a,r)->{throw new AssertionError("native scheduler invoked after stop");},null).start(1);
        check(blocked.isCancelled(),"new tasks return cancelled handles without invoking native schedulers");
        var world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class[]{World.class},(o,m,a)->{throw new AssertionError("world access at shutdown");});
        var shutdownEntity=(org.bukkit.entity.Entity)Proxy.newProxyInstance(org.bukkit.entity.Entity.class.getClassLoader(),new Class[]{org.bukkit.entity.Entity.class},(o,m,a)->{throw new AssertionError("entity access at shutdown");});
        check(SuiteScheduler.region(owner,new Location(world,0,64,0),stopCalls::incrementAndGet).isCancelled(),"public region scheduling is stopped");
        check(SuiteScheduler.entity(owner,shutdownEntity,stopCalls::incrementAndGet).isCancelled(),"public entity scheduling is stopped");
        check(SuiteScheduler.get().runTask(owner,stopCalls::incrementAndGet).isCancelled(),"public global scheduling is stopped");
        check(SuiteScheduler.get().runTaskAsynchronously(owner,stopCalls::incrementAndGet).isCancelled(),"public async scheduling is stopped");
        WorldTasks.eachEntityCleanup(owner,e->{throw new AssertionError("cleanup executed");});
        WorldTasks.eachChunk(owner,c->{throw new AssertionError("chunk access");});
        WorldTasks.remove(owner,shutdownEntity);WorldTasks.loaded(owner,new Location(world,0,64,0),stopCalls::incrementAndGet);
        SuiteScheduler.cleanupPlayer(owner,null,p->{throw new AssertionError("player cleanup executed");});
        SuiteScheduler.cleanupPlayers(owner,p->{throw new AssertionError("player cleanup executed");});
        check(WorldTasks.cleanupOwner(owner)==null&&stopCalls.get()==0,"shutdown cleanup never accesses world/entity/player state");
        stopping.set(false);
        var raced=new SuiteScheduler.Task(owner,stopCalls::incrementAndGet,false,0,(d,a,r)->{stopping.set(true);throw new NullPointerException("region data already retired");},null).start(1);
        check(raced.isCancelled(),"shutdown race during native scheduling is cancelled without surfacing NPE");
        stopping.set(false);
        var expected=new NullPointerException("real scheduler defect outside shutdown");
        try{new SuiteScheduler.Task(owner,stopCalls::incrementAndGet,false,0,(d,a,r)->{throw expected;},null).start(1);throw new AssertionError("exception swallowed");}
        catch(NullPointerException actual){check(actual==expected,"normal runtime scheduling errors remain visible");}
        System.out.println("SuiteSchedulerSmoke PASS: "+checks+" checks; contention, affinity, retries, cancellation, freeze, exceptions, reentrancy and retirement");
    }
}
