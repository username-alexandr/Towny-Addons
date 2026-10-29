package ru.neverland.townylogistics.service;

import java.util.*;
import java.util.concurrent.*;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Network.*;

/** Bounded derived CPU cache. Invalidating one component never rebuilds unrelated towns or graph components. */
public final class RouteCache {
    public record Reading(String status,RoutePlanner.Result result,String explanation){}
    public record Key(UUID town,String from,String to,Set<Link> excluded,double maximum){public Key{excluded=Set.copyOf(excluded);}}
    private record Entry(Network graph,Set<String> component,CompletableFuture<RoutePlanner.Result> result){}
    private final Map<Key,Entry> entries=new LinkedHashMap<>();private final Set<CompletableFuture<?>> auxiliary=new HashSet<>();private final CompletableFuture<Void> terminated=new CompletableFuture<>();private final ThreadPoolExecutor worker;private boolean closed;private long calculations,invalidated;
    public RouteCache(){worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{var thread=new Thread(r,"NeverLand-logistics-paths");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){terminated.complete(null);}};}
    public synchronized Reading read(Network graph,String from,String to,Set<Link> excluded,double maximum){
        if(closed)return new Reading("CLOSED",null,"Расчёт останавливается");
        if(graph.nodes().size()>RoutePlanner.MAX_NODES||graph.links().size()>RoutePlanner.MAX_EDGES||!Double.isFinite(maximum)||maximum<=0)return new Reading("FAILED",null,"Сеть или предел пути слишком велики; разделите дорожную сеть");
        var key=new Key(graph.town(),from,to,excluded,maximum);var entry=entries.get(key);if(entry!=null){if(!entry.result().isDone())return new Reading("PENDING",null,"Проверяем путь и запасные дороги");try{return new Reading("READY",entry.result().join(),"");}catch(CompletionException|CancellationException error){return new Reading("FAILED",null,"Путь не удалось проверить; сообщите администрации");}}
        if(entries.size()>=256){var iterator=entries.entrySet().iterator();boolean removed=false;while(iterator.hasNext())if(iterator.next().getValue().result().isDone()){iterator.remove();removed=true;break;}if(!removed)return new Reading("PENDING",null,"Очередь проверки дорог занята");}
        var future=new CompletableFuture<RoutePlanner.Result>();try{worker.execute(()->{try{future.complete(RoutePlanner.plan(graph.nodes(),graph.links(),from,to,key.excluded(),maximum,3));}catch(Throwable error){future.completeExceptionally(error);}});}catch(RejectedExecutionException full){return new Reading("PENDING",null,"Очередь проверки дорог занята");}entries.put(key,new Entry(graph,RoutePlanner.component(graph,List.of(from,to)),future));calculations++;return new Reading("PENDING",null,"Проверяем путь и запасные дороги");
    }
    public synchronized void changed(Network before,Network after){
        if(before.nodes().equals(after.nodes())&&before.links().equals(after.links()))return;var changedNodes=new HashSet<String>();var all=new HashSet<>(before.nodes().keySet());all.addAll(after.nodes().keySet());for(String id:all)if(!Objects.equals(before.nodes().get(id),after.nodes().get(id)))changedNodes.add(id);
        var removed=new HashSet<>(before.links());removed.removeAll(after.links());var added=new HashSet<>(after.links());added.removeAll(before.links());for(var link:added){changedNodes.add(link.a());changedNodes.add(link.b());}
        var affected=new HashSet<>(RoutePlanner.component(before,changedNodes));affected.addAll(RoutePlanner.component(after,changedNodes));
        entries.entrySet().removeIf(e->{if(!e.getKey().town().equals(after.town()))return false;boolean drop=affected.contains(e.getKey().from())||affected.contains(e.getKey().to());if(!drop&&!removed.isEmpty()){var future=e.getValue().result();if(!future.isDone()||future.isCompletedExceptionally())drop=removed.stream().anyMatch(link->e.getValue().component().contains(link.a())||e.getValue().component().contains(link.b()));else{var result=future.getNow(null);drop=result!=null&&result.paths().stream().anyMatch(p->!Collections.disjoint(p.links(),removed));}}if(drop)invalidated++;return drop;});
    }
    public synchronized void invalidateLinks(UUID town,Set<Link> changed){entries.entrySet().removeIf(e->{if(!e.getKey().town().equals(town))return false;var future=e.getValue().result();boolean pending=!future.isDone()||future.isCompletedExceptionally();boolean drop=pending&&changed.stream().anyMatch(link->e.getValue().component().contains(link.a())||e.getValue().component().contains(link.b()));if(!pending){var result=future.getNow(null);drop=result!=null&&result.paths().stream().anyMatch(p->!Collections.disjoint(p.links(),changed));}if(drop)invalidated++;return drop;});}
    public synchronized void forget(UUID town){entries.keySet().removeIf(key->key.town().equals(town));}
    public synchronized CompletionStage<RouteIndex> index(Map<UUID,Network> networks){
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Расчёт останавливается"));var snapshot=Map.copyOf(networks);var future=new CompletableFuture<RouteIndex>();auxiliary.add(future);
        try{worker.execute(()->{try{var index=new RouteIndex();snapshot.values().forEach(index::update);future.complete(index);}catch(Throwable error){future.completeExceptionally(error);}finally{synchronized(this){auxiliary.remove(future);}}});}catch(RejectedExecutionException full){auxiliary.remove(future);future.completeExceptionally(full);}return future;
    }
    public synchronized void invalidateDestinations(UUID town,Set<String> destinations){entries.keySet().removeIf(key->{boolean drop=key.town().equals(town)&&destinations.contains(key.to());if(drop)invalidated++;return drop;});}
    public synchronized long calculations(){return calculations;}public synchronized long invalidated(){return invalidated;}
    public synchronized CompletionStage<Void> prepareClose(){if(!closed){closed=true;worker.shutdownNow();entries.values().forEach(e->e.result().completeExceptionally(new CancellationException("Расчёт остановлен")));auxiliary.forEach(f->f.completeExceptionally(new CancellationException("Расчёт остановлен")));auxiliary.clear();}return terminated;}
}
