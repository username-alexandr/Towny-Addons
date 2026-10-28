package ru.neverland.townycontrol;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.Proxy;
import org.bukkit.plugin.Plugin;
import ru.neverland.core.*;

public final class ShutdownHooksSmoke {
    static Plugin plugin(String name){return(Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class<?>[]{Plugin.class},(p,m,a)->m.getName().equals("getName")?name:m.getName().equals("toString")?name:null);}
    public static void main(String[] args)throws Exception{
        var first=plugin("ShutdownTest");var durable=new CompletableFuture<Void>();var calls=new AtomicInteger();ModuleShutdown.register(first,()->{calls.incrementAndGet();return durable;});
        var receipts=new ArrayList<CompletionStage<Void>>();try(var guard=SuiteState.guard()){for(int n=0;n<25;n++)receipts.add(ModuleShutdown.prepare(first));}
        if(calls.get()!=1||receipts.stream().anyMatch(r->r.toCompletableFuture().isDone()))throw new AssertionError("prepare must be nonblocking and invoke one snapshot only");
        durable.complete(null);for(var receipt:receipts)receipt.toCompletableFuture().get(1,TimeUnit.SECONDS);ModuleShutdown.remove(first);
        var second=plugin("ShutdownTest");ModuleShutdown.register(second,()->CompletableFuture.failedFuture(new java.io.IOException("disk unavailable")));
        boolean failed=false;try{ModuleShutdown.prepare(second).toCompletableFuture().get();}catch(ExecutionException expected){failed=true;}
        if(!failed)throw new AssertionError("failed save must never authorize unload");
        ModuleShutdown.remove(first);if(!ModuleShutdown.prepare(second).toCompletableFuture().isCompletedExceptionally())throw new AssertionError("old instance cannot remove new instance's hook");
        ModuleShutdown.remove(second);if(!ModuleShutdown.prepare(second).toCompletableFuture().isDone())throw new AssertionError("legacy store completes without a hook");
        System.out.println("ShutdownHooksSmoke PASS: nonblocking, one receipt, failed-save refusal and instance isolation");
    }
}
