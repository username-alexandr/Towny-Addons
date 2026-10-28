package ru.neverland.core;

import java.util.concurrent.*;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

/** Two-phase disable: drain registered stores while the plugin classloader is still open. */
public final class ModuleShutdown {
    // Only JDK containers/interfaces cross the independently shaded addon classloaders.
    @SuppressWarnings("unchecked") private static final ConcurrentMap<String,Object[]> hooks=(ConcurrentMap<String,Object[]>)System.getProperties().computeIfAbsent("ru.neverland.towny.shutdown.v1",key->new ConcurrentHashMap<String,Object[]>());
    private ModuleShutdown(){}
    public static void register(Plugin plugin,Supplier<CompletionStage<Void>> prepare){
        var entry=new Object[]{plugin,prepare,new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>()};
        var previous=hooks.putIfAbsent(plugin.getName(),entry);if(previous!=null&&previous[0]!=plugin)throw new IllegalStateException("Previous addon shutdown has not finished: "+plugin.getName());
    }
    @SuppressWarnings("unchecked") public static CompletionStage<Void> prepare(Plugin plugin){
        var entry=hooks.get(plugin.getName());if(entry==null||entry[0]!=plugin)return CompletableFuture.completedFuture(null);
        var state=(java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>)entry[2];var existing=state.get();if(existing!=null)return existing;
        var receipt=new CompletableFuture<Void>();if(!state.compareAndSet(null,receipt))return state.get();
        try{((Supplier<CompletionStage<Void>>)entry[1]).get().whenComplete((v,error)->{if(error==null)receipt.complete(null);else receipt.completeExceptionally(error);});}
        catch(Throwable error){receipt.completeExceptionally(error);}return receipt;
    }
    public static void remove(Plugin plugin){hooks.computeIfPresent(plugin.getName(),(name,entry)->entry[0]==plugin?null:entry);}
}
