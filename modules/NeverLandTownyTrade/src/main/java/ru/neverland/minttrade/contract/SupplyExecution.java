package ru.neverland.minttrade.contract;
import java.util.*;import java.util.concurrent.*;import java.util.function.Supplier;
import org.bukkit.Bukkit;import org.bukkit.plugin.Plugin;import ru.neverland.core.*;
/** Short guarded state actions and raw bank actions have separate scheduler/completion boundaries. */
final class SupplyExecution{
 private final Plugin plugin;private volatile boolean stopping;private final Set<Request<?>> pending=Collections.newSetFromMap(new IdentityHashMap<>());private final CompletableFuture<Void> closed=new CompletableFuture<>();
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"nlt-supply-results");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){closed.complete(null);}};
 private static final class Request<T>{final CompletableFuture<T> done=new CompletableFuture<>();final boolean raw;boolean running;Request(boolean raw){this.raw=raw;}}
 SupplyExecution(Plugin plugin){this.plugin=plugin;}
 <T>CompletionStage<T> guarded(Supplier<T> action){return submit(action,false);}
 CompletionStage<Boolean> bank(Supplier<Boolean> action){return submit(action,true);}
 private <T>CompletionStage<T> submit(Supplier<T> action,boolean raw){var request=new Request<T>(raw);synchronized(pending){if(stopping)return CompletableFuture.failedFuture(new IllegalStateException("Поставки останавливаются"));pending.add(request);}schedule(request,action,false);return request.done;}
 private <T>void schedule(Request<T> request,Supplier<T> action,boolean later){try{java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> callback=task->{if(request.done.isDone())return;if(request.raw)run(request,action);else if(!SuiteState.tryRun("Supply acknowledged action",()->run(request,action)))schedule(request,action,true);};if(later)Bukkit.getGlobalRegionScheduler().runDelayed(plugin,callback,1);else Bukkit.getGlobalRegionScheduler().run(plugin,callback);}catch(Throwable error){finish(request,null,error);}}
 @SuppressWarnings("unchecked")private <T>void run(Request<T> request,Supplier<T> action){synchronized(pending){if(request.done.isDone())return;request.running=true;}T result=null;Throwable error=null;try{if(stopping||!plugin.isEnabled()||SuiteState.frozen(plugin.getName())){if(request.raw)result=(T)Boolean.FALSE;else throw new IllegalStateException("Поставки приостановлены");}else result=action.get();}catch(Throwable failure){error=failure;}if(request.raw)finish(request,result,error);else complete(request,result,error);}
 private <T>void finish(Request<T> request,T result,Throwable error){Runnable complete=()->{try(var guard=SuiteState.guard()){complete(request,result,error);}};try{worker.execute(complete);}catch(RejectedExecutionException unavailable){if(!request.done.isDone())CompletableFuture.runAsync(complete);}}
 private <T>void complete(Request<T> request,T result,Throwable error){try{if(error==null)request.done.complete(result);else request.done.completeExceptionally(error);}finally{synchronized(pending){pending.remove(request);}}}
 CompletionStage<Boolean> audit(AuditRecord record){var result=new CompletableFuture<Boolean>();try{worker.execute(()->{boolean ok;try{ok=AuditTrail.record(plugin,record);}catch(Throwable error){ok=false;}try(var guard=SuiteState.guard()){result.complete(ok);}});}catch(RejectedExecutionException error){result.complete(false);}return result;}
 @SuppressWarnings("unchecked")void stop(){stopping=true;synchronized(pending){for(var request:List.copyOf(pending))if(!request.running){if(request.raw)((CompletableFuture<Boolean>)(CompletableFuture<?>)request.done).complete(false);else request.done.completeExceptionally(new IllegalStateException("Остановка до внешнего действия"));pending.remove(request);}}}
 CompletionStage<Void> close(){worker.shutdown();return closed;}
}
