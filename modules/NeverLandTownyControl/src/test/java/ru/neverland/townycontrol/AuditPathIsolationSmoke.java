package ru.neverland.townycontrol;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.plugin.Plugin;
import ru.neverland.core.*;

/** A slow journal must not hold a class-wide monitor and stall unrelated regional work. */
public final class AuditPathIsolationSmoke {
    static Plugin plugin(Path root,String name){return(Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class<?>[]{Plugin.class},(p,m,a)->switch(m.getName()){case "getName"->name;case "getDataFolder"->root.resolve(name).toFile();case "getLogger"->java.util.logging.Logger.getLogger(name);default->null;});}
    static boolean record(Plugin plugin,String id){return AuditTrail.record(plugin,"step",id,"AUDIT_TEST","DONE",AuditRecord.Party.system(),AuditRecord.Party.system(),AuditRecord.Party.system(),"",0,"","independent journal");}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("audit-paths-");Plugin first=plugin(root,"slow"),second=plugin(root,"other");if(!record(first,"prime-a")||!record(second,"prime-b"))throw new AssertionError("prime journals");
        var field=AuditTrail.class.getDeclaredField("journals");field.setAccessible(true);var journal=((Map<?,?>)field.get(null)).get(first.getDataFolder().toPath().resolve("audit"));
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var independent=new CompletableFuture<Boolean>();var failure=new CompletableFuture<Throwable>();
        var holder=new Thread(()->{synchronized(journal){held.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}});holder.setDaemon(true);holder.start();if(!held.await(2,TimeUnit.SECONDS))throw new AssertionError("journal lock not held");
        var writer=new Thread(()->{try{if(!record(first,"blocked"))throw new AssertionError("first write failed");}catch(Throwable error){failure.complete(error);}});writer.setDaemon(true);writer.start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(writer.getState()!=Thread.State.BLOCKED&&System.nanoTime()<deadline)java.util.concurrent.locks.LockSupport.parkNanos(1000000);
        if(writer.getState()!=Thread.State.BLOCKED){release.countDown();throw new AssertionError("writer must actually wait for slow journal");}
        var other=new Thread(()->{try(var guard=SuiteState.guard()){independent.complete(record(second,"unrelated"));}catch(Throwable error){independent.completeExceptionally(error);}});other.setDaemon(true);other.start();
        boolean passed=false;try{passed=independent.get(1,TimeUnit.SECONDS);}catch(TimeoutException expected){}finally{release.countDown();holder.join(3000);writer.join(3000);other.join(3000);}
        if(!passed||failure.isDone())throw new AssertionError("slow journal blocked unrelated suite ledger work");
        System.out.println("AuditPathIsolationSmoke PASS: actual blocked journal cannot hold unrelated ledger work");
    }
}
