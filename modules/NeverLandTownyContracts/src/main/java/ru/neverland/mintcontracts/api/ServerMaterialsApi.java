package ru.neverland.mintcontracts.api;

import java.util.*;
import java.util.concurrent.CompletionStage;
import ru.neverland.core.ApiContract;

/** Server construction integrations: asynchronous, immutable results; never join on a Folia thread. */
public interface ServerMaterialsApi extends ApiContract {
    @Override default Set<String> capabilities(){return Set.of("balances","reserve","consume","release","demand","cooperativeProjects","cooperativeProject","cooperativePlan","cooperativeFinish","cooperativeHistory","cooperativeReceipts","cooperativeUnlocks","cooperativeOrders","cooperativeDeliver","cooperativeReady");}
    default CompletionStage<Map<String,Long>> demand(){return java.util.concurrent.CompletableFuture.completedFuture(Map.of());}
    CompletionStage<Map<String,Long>> balances();
    CompletionStage<String> reserve(UUID operation,String project,Map<String,Long> costs,String actor,String reason);
    CompletionStage<String> consume(UUID operation,String actor,String reason);
    CompletionStage<String> release(UUID operation,String actor,String reason);
    /** Lock-free storage readiness; false while initializing, stopping or awaiting recovery. */
    default boolean cooperativeReady(){return false;}
    default CompletionStage<List<Map<String,Object>>> cooperativeProjects(int page){return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());}
    default CompletionStage<Map<String,Object>> cooperativeProject(String id){return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());}
    default CompletionStage<Map<String,Object>> cooperativePlan(String id,String title,String scope,String town,String unlock,long minimum,Map<String,Long> needs,String actor){return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());}
    default CompletionStage<Map<String,Object>> cooperativeFinish(String id,boolean cancel,String actor,String reason){return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());}
    default CompletionStage<List<Map<String,Object>>> cooperativeHistory(String project){return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());}
    default CompletionStage<List<Map<String,Object>>> cooperativeReceipts(UUID town,int page){return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());}
    default CompletionStage<Set<String>> cooperativeUnlocks(UUID town){return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());}
    /** Must be called on the owning player's scheduler; filters already accepted delivery orders only. */
    default List<Map<String,Object>> cooperativeOrders(org.bukkit.entity.Player player){throw new UnsupportedOperationException();}
    /** Uses the ordinary accepted order's exact items, reward and payout limits. */
    default CompletionStage<Void> cooperativeDeliver(org.bukkit.entity.Player player,UUID order,String project){return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());}
}
