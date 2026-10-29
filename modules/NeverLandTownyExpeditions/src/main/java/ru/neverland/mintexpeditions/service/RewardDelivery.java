package ru.neverland.mintexpeditions.service;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.mintexpeditions.integration.TownyHook;

/** One bounded delivery pipeline: intent ACK -> owning entity effect -> evidence -> done ACK. */
public final class RewardDelivery {
    private record Request(Player player,CompletableFuture<Boolean> done){}
    private final JavaPlugin plugin;private final ExpeditionRepository repository;private final TownyHook towny;private final PlayerSaveEvidence evidence;
    private final Map<UUID,Request> requests=new LinkedHashMap<>();private boolean running,stopping;
    private CompletionStage<Void> pipeline=CompletableFuture.completedFuture(null);
    private CompletionStage<Void> close;private final Set<CompletableFuture<?>> nativeWork=new HashSet<>();
    public RewardDelivery(JavaPlugin plugin,ExpeditionRepository repository,TownyHook towny){this.plugin=plugin;this.repository=repository;this.towny=towny;this.evidence=new PlayerSaveEvidence(plugin);}
    public CompletionStage<Boolean> claim(Player player){try(var guard=SuiteState.guard()){
        var old=requests.get(player.getUniqueId());if(old!=null)return old.done();
        if(stopping||requests.size()>=64)return CompletableFuture.failedFuture(new IllegalStateException("Очередь выдачи занята или останавливается"));
        var request=new Request(player,new CompletableFuture<>());requests.put(player.getUniqueId(),request);next();return request.done();
    }}
    private void next(){
        if(running||stopping||requests.isEmpty())return;running=true;var request=requests.values().iterator().next();var player=request.player();
        CompletionStage<Void> chain=repository.saveIfDirtyAsync();
        for(var batch:repository.rewards(player.getUniqueId())){
            if(batch.money()>0)chain=chain.thenCompose(v->repository.effects().executeAsync(batch.moneyId(),"Награда "+batch.id()+" игроку "+batch.owner()+": "+batch.money(),()->onPlayer(player,()->{
                if(!eligible(player))return CompletableFuture.completedFuture(false);return CompletableFuture.completedFuture(towny.reward(player,batch.money(),batch.moneyId()));
            })).thenAccept(ok->{if(!ok)throw new CompletionException(new IllegalStateException("Денежная награда остаётся в очереди"));}));
            var items=batch.items();for(int i=0;i<items.size();i++){final int index=i;var item=items.get(i);
                chain=chain.thenCompose(v->repository.effects().executeAsync(batch.itemId(index),"Предмет награды "+batch.id()+" игроку "+batch.owner()+" #"+index,()->onPlayer(player,()->{
                    if(!eligible(player))return CompletableFuture.completedFuture(false);
                    var planned=InventoryPlan.insert(player.getInventory().getStorageContents(),item);if(planned==null)return CompletableFuture.completedFuture(false);
                    var proof=evidence.prepare(player,batch.itemId(index));player.getInventory().setStorageContents(planned);
                    return evidence.saveAndVerify(player,proof).thenApply(saved->{if(!saved)throw new CompletionException(new IllegalStateException("Сохранение выданного предмета требует сверки"));return true;});
                })).thenAccept(ok->{if(!ok)throw new CompletionException(new IllegalStateException("Освободите место и повторите /expedition claim"));}));
            }
            chain=chain.thenCompose(v->repository.completeRewardAsync(batch));
        }
        pipeline=chain.thenCompose(v->repository.pruneEffectsAsync()).whenComplete((v,error)->{try(var guard=SuiteState.guard()){
            requests.remove(player.getUniqueId());running=false;if(error==null)request.done().complete(!repository.hasReward(player.getUniqueId()));else request.done().completeExceptionally(error);
            next();
        }});
    }
    private boolean eligible(Player player){return !stopping&&plugin.isEnabled()&&!SuiteState.frozen(plugin.getName())&&player.isOnline()&&player.getGameMode()!=GameMode.CREATIVE&&player.getGameMode()!=GameMode.SPECTATOR;}
    @FunctionalInterface private interface Action<T>{CompletionStage<T> run()throws Exception;}
    /** Deliberately native scheduler: the synchronous player save must never inherit SuiteScheduler's guard. */
    private <T>CompletionStage<T> onPlayer(Player player,Action<T> action){
        var future=new CompletableFuture<T>();try(var guard=SuiteState.guard()){if(stopping)return CompletableFuture.failedFuture(new IllegalStateException("Выдача останавливается"));nativeWork.add(future);}
        future.whenComplete((v,e)->{try(var guard=SuiteState.guard()){nativeWork.remove(future);}});
        try{var task=player.getScheduler().runDelayed(plugin,ignored->{if(stopping||!player.isOnline()){future.completeExceptionally(new IllegalStateException("Игрок недоступен; сохранённое начисление требует проверки"));return;}try{if(SuiteState.held())throw new IllegalStateException("Внешний эффект не должен удерживать Suite");action.run().whenComplete((value,error)->{if(error==null)future.complete(value);else future.completeExceptionally(error);});}catch(Throwable error){future.completeExceptionally(error);}},()->future.completeExceptionally(new IllegalStateException("Игрок вышел до выдачи")),1);if(task==null)future.completeExceptionally(new IllegalStateException("Поток игрока недоступен"));}catch(Exception error){future.completeExceptionally(error);}return future;
    }
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){
        if(close!=null)return close;stopping=true;
        // A detached save already running is allowed to drain; an uncertain external effect stays PENDING.
        for(var f:List.copyOf(nativeWork))f.completeExceptionally(new IllegalStateException("Выдача остановлена; намерение сохранено для сверки"));
        for(var r:List.copyOf(requests.values()))r.done().completeExceptionally(new IllegalStateException("Выдача остановлена; награда сохранена"));requests.clear();
        close=pipeline.handle((v,error)->null).thenCompose(v->evidence.prepareClose());return close;
    }}
}
