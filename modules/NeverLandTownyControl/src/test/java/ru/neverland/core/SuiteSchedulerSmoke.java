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
        var enabled=new AtomicBoolean(true);var logger=Logger.getLogger("SuiteSchedulerSmoke");
        Plugin owner=(Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class[]{Plugin.class},(o,m,a)->switch(m.getName()){
            case "getName"->"NeverLandTest";case "isEnabled"->enabled.get();case "getLogger"->logger;case "hashCode"->System.identityHashCode(o);case "equals"->o==a[0];default->null;});
        var global=new Queue();
        GlobalRegionScheduler scheduler=(GlobalRegionScheduler)Proxy.newProxyInstance(GlobalRegionScheduler.class.getClassLoader(),new Class[]{GlobalRegionScheduler.class},(o,m,a)->{
            if(m.getName().equals("runDelayed"))return global.later((Long)a[2],()->((Consumer<ScheduledTask>)a[1]).accept(null),()->{});
            throw new UnsupportedOperationException(m.getName());});
        PluginManager manager=(PluginManager)Proxy.newProxyInstance(PluginManager.class.getClassLoader(),new Class[]{PluginManager.class},(o,m,a)->m.getName().equals("getPlugins")?new Plugin[]{owner}:null);
        Server server=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class[]{Server.class},(o,m,a)->switch(m.getName()){
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
        System.out.println("SuiteSchedulerSmoke PASS: "+checks+" checks; contention, affinity, retries, cancellation, freeze, exceptions, reentrancy and retirement");
    }
}
