package ru.neverland.townytaxes.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.math.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.*;
import ru.neverland.townytaxes.data.*;
import ru.neverland.townytaxes.integration.TownyHook;
import ru.neverland.townytaxes.model.*;

/** Database acknowledgements never block Folia; only short bank callbacks enter the suite ledger. */
public final class TaxCoordinator {
    private final JavaPlugin plugin;private final CivicRepository civic;private final TownyHook towny;private final TaxStore store;private final TaxPayments engine;
    private final Map<UUID,CompletableFuture<Void>> active=new HashMap<>();private final Set<String> starting=new HashSet<>();
    private final Function<TaxPayment,CompletionStage<Void>> completed;private volatile boolean activated,closing,internal;
    private CompletableFuture<Void> prepared;
    public TaxCoordinator(JavaPlugin plugin,CivicRepository civic,TownyHook towny,Function<TaxPayment,CompletionStage<Void>> completed){
        this.plugin=plugin;this.civic=civic;this.towny=towny;this.completed=completed;var debts=new HashMap<UUID,Long>();civic.debts().forEach((id,n)->debts.put(id,cents(n)));
        store=new TaxStore(plugin.getDataFolder().toPath().resolve("payments.db"),cents(civic.serverBank()),debts,civic.usesPaymentsDb());
        engine=new TaxPayments(store,new TaxPayments.Bank(){
            public boolean ready(TaxPayment r,TaxPayments.Leg leg){
                if(!store.healthy()||!civic.healthy()||!towny.economyAvailable()||!plugin.isEnabled()||closing&&leg==TaxPayments.Leg.DEBIT)return false;
                var party=accountParty(r,leg);if(party==null||party.account()==null)return false;
                var destination=leg==TaxPayments.Leg.DEBIT&&r.destinationScope()!=Domain.Scope.GLOBAL?towny.resolve(r.destinationScope(),r.destinationId()):null;
                return leg!=TaxPayments.Leg.DEBIT||r.destinationScope()==Domain.Scope.GLOBAL||destination!=null&&destination.account()!=null;
            }
            public boolean apply(TaxPayment r,TaxPayments.Leg leg)throws Exception{return bank(r,leg);}
        },this::post);
        store.ready().whenComplete((v,error)->{try{post(()->{if(error!=null){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Денежный учёт налогов недоступен; списания приостановлены",error);return;}if(closing)return;try{civic.markPaymentsDb();activated=true;}catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Не подтверждён перенос налогового учёта; списания остановлены",e);}});}catch(RejectedExecutionException ignored){}});
        ModuleShutdown.register(plugin,this::prepareClose);
    }
    public static long cents(double value){if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Неверная прежняя сумма налогового учёта");long n=BigDecimal.valueOf(value).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact();if(n>TaxCalculation.MAX_CENTS)throw new IllegalArgumentException("Прежний налоговый баланс превышает предел");return n;}
    public TaxStore store(){return store;}public boolean ready(){return activated&&!closing&&store.healthy()&&civic.healthy();}public boolean internal(){return internal;}
    private static String subject(Domain.Scope scope,UUID id,UUID policy,TaxPayment.Kind kind){return scope+":"+id+":"+kind+":"+policy;}
    public boolean busy(Domain.Scope scope,UUID id,UUID policy,TaxPayment.Kind kind){return starting.contains(subject(scope,id,policy,kind))||store.snapshot().receipts().values().stream().anyMatch(r->!r.terminal()&&r.sourceScope()==scope&&r.sourceId().equals(id)&&Objects.equals(r.policy(),policy)&&r.kind()==kind);}
    public CompletionStage<Void> begin(TaxPayment receipt){
        if(!ready())return CompletableFuture.failedFuture(new IllegalStateException("Налоговый учёт ещё не готов"));
        var existing=store.get(receipt.id());if(existing!=null)return kick(existing.id());
        String key=subject(receipt.sourceScope(),receipt.sourceId(),receipt.policy(),receipt.kind());if(busy(receipt.sourceScope(),receipt.sourceId(),receipt.policy(),receipt.kind()))return CompletableFuture.failedFuture(new IllegalStateException("Предыдущий налоговый платёж ещё не завершён"));starting.add(key);
        var finalReceipt=new CompletableFuture<Void>();active.put(receipt.id(),finalReceipt);
        store.create(receipt).whenComplete((saved,error)->post(()->{
            starting.remove(key);if(error!=null){active.remove(receipt.id());finalReceipt.completeExceptionally(error);return;}
            CompletionStage<Void> flow;try{flow=engine.process(saved.id());}catch(Exception e){flow=CompletableFuture.failedFuture(e);}finish(saved.id(),flow,finalReceipt);
        }));return finalReceipt;
    }
    public CompletionStage<Void> kick(UUID id){
        var running=active.get(id);if(running!=null)return running;var value=store.get(id);if(value==null)return CompletableFuture.completedFuture(null);
        var receipt=new CompletableFuture<Void>();active.put(id,receipt);CompletionStage<Void> flow;try{flow=engine.process(id);}catch(Exception e){flow=CompletableFuture.failedFuture(e);}finish(id,flow,receipt);return receipt;
    }
    private void finish(UUID id,CompletionStage<Void> flow,CompletableFuture<Void> receipt){
        flow.whenComplete((v,error)->post(()->{
            if(error!=null){active.remove(id);receipt.completeExceptionally(error);return;}
            CompletionStage<Void> notification;try{notification=notifyReceipt(id);}catch(Exception e){notification=CompletableFuture.failedFuture(e);}
            notification.whenComplete((ignored,failure)->post(()->{active.remove(id);if(failure==null)receipt.complete(null);else receipt.completeExceptionally(failure);}));
        }));
    }
    private CompletionStage<Void> notifyReceipt(UUID id){var current=store.get(id);if(current!=null)return completed.apply(current);return store.inspect(id).thenComposeAsync(r->r==null?CompletableFuture.completedFuture(null):completed.apply(r),this::post);}
    public void pulse(){if(!ready())return;store.snapshot().receipts().values().stream().filter(r->!r.terminal()&&!r.review()&&!active.containsKey(r.id())).limit(10).toList().forEach(r->kick(r.id()));}
    public CompletionStage<Void> resolve(UUID id,TaxPayments.Leg leg,boolean applied){
        if(!ready()||active.containsKey(id))throw new IllegalStateException("Дождитесь окончания текущей обработки");
        return engine.resolve(id,leg,applied).thenComposeAsync(changed->kick(id),this::post);
    }
    public CompletionStage<Void> cancel(UUID id){
        if(!ready()||active.containsKey(id))throw new IllegalStateException("Дождитесь окончания текущей обработки");
        return engine.cancel(id).thenComposeAsync(changed->kick(id),this::post);
    }
    private TownyHook.Party accountParty(TaxPayment r,TaxPayments.Leg leg){return leg==TaxPayments.Leg.CREDIT?towny.resolve(r.destinationScope(),r.destinationId()):towny.resolve(r.sourceScope(),r.sourceId());}
    private boolean bank(TaxPayment r,TaxPayments.Leg leg)throws Exception{
        if(!towny.economyAvailable()||!civic.healthy())throw new IllegalStateException("Экономика недоступна; этап требует проверки");
        var party=accountParty(r,leg);if(party==null||party.account()==null)throw new IllegalStateException("Счёт недоступен; этап требует проверки");Account account=party.account();boolean incoming=leg!=TaxPayments.Leg.DEBIT;String token="[nlt-tax:"+r.id()+":"+leg+"]";var proof=new PaymentEvidence(account,incoming,token,r.calculation().cents());
        var observer=new AccountObserver(){public void withdrew(Account source,double n,String reason){proof.observe(source,false,n,reason);}public void deposited(Account source,double n,String reason){proof.observe(source,true,n,reason);}};
        synchronized(account){account.addObserver(observer);internal=true;try{double before=account.getHoldingBalance(true);if(!Double.isFinite(before))throw new IllegalStateException("Баланс недоступен");boolean result;try{result=incoming?account.deposit(r.calculation().money(),token+" "+r.note()):account.withdraw(r.calculation().money(),token+" "+r.note());}catch(RuntimeException e){if(proof.observed())return true;throw e;}
            if(!result&&!proof.observed()){double after=account.getHoldingBalance(true);if(!Double.isFinite(after)||Math.abs(after-before)>.000001)throw new IllegalStateException("Баланс изменился без подтверждённой квитанции; нужна сверка");}return proof.result(result);
        }finally{internal=false;account.removeObserver(observer);}}
    }
    /** Committed continuations must finish while Control freezes new work, before it closes the plugin loader. */
    public void post(Runnable task){
        if(!plugin.isEnabled())throw new RejectedExecutionException("Плагин налогов отключён");
        Bukkit.getGlobalRegionScheduler().run(plugin,t->{if(!SuiteState.tryRun("Tax continuation",task))Bukkit.getGlobalRegionScheduler().runDelayed(plugin,next->post(task),1);});
    }
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){
        if(prepared!=null)return prepared;closing=true;var pending=active.values().toArray(CompletableFuture[]::new);prepared=new CompletableFuture<>();
        CompletableFuture.allOf(pending).whenComplete((v,error)->{
            if(error!=null){finishClose(error);return;}
            post(()->{
                var neutral=new ArrayList<CompletableFuture<Void>>();
                for(var r:store.snapshot().receipts().values())if(r.phase()==TaxPayment.Phase.PREPARED||r.phase()==TaxPayment.Phase.DEBITED){
                    var settled=engine.cancel(r.id()).thenComposeAsync(changed->engine.process(r.id()),this::post).thenComposeAsync(ignored->notifyReceipt(r.id()),this::post);neutral.add(settled.toCompletableFuture());
                }
                CompletableFuture.allOf(neutral.toArray(CompletableFuture[]::new)).whenComplete((ignored,failure)->finishClose(failure));
            });
        });return prepared;
    }}
    private void finishClose(Throwable error){store.close();store.closed().whenComplete((unused,closedError)->{Throwable failure=error==null?closedError:error;if(failure==null)prepared.complete(null);else prepared.completeExceptionally(failure);});}
    public void closeAfterSchedulerStop(){closing=true;store.close();}
    public void awaitClosed(){var done=store.closed().toCompletableFuture();if(!done.isDone()&&SuiteState.held())throw new IllegalStateException("Нельзя ждать диск под общей блокировкой");done.join();}
}
