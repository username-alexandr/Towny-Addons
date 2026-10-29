package ru.neverland.townytreasury.service;

import java.util.*;import java.util.concurrent.*;import java.util.function.*;
import org.bukkit.*;import org.bukkit.plugin.Plugin;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.*;import ru.neverland.townytreasury.data.TreasuryRepository;import ru.neverland.townytreasury.debits.AsyncDebit;import ru.neverland.townytreasury.model.*;
import static ru.neverland.townytreasury.debits.AsyncDebit.State.*;

/** Additive asynchronous bank boundary; every phase and budget reservation share one durable image. */
final class AsyncBudgetGateway {
    record Outcome(AsyncDebit.State state,String reason){}
    private final Plugin plugin;private final TreasuryService host;private final TreasuryRepository repo;private final DebitAudit audit;
    private CompletionStage<?> operation=CompletableFuture.completedFuture(null);private UUID invoking;private volatile boolean stopping;private volatile RawRequest raw;private Map<UUID,AsyncDebit> indexedReceipts=Map.of(),refundIndex=Map.of();
    private final ThreadPoolExecutor results=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"nlt-treasury-results");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final class RawRequest {final CompletableFuture<Outcome> result=new CompletableFuture<>();volatile boolean running;}
    AsyncBudgetGateway(Plugin plugin,TreasuryService host,TreasuryRepository repo){this.plugin=plugin;this.host=host;this.repo=repo;audit=new DebitAudit(plugin);}
    boolean busy(){return !operation.toCompletableFuture().isDone();}
    boolean owns(UUID id){return id!=null&&repo.image().debits().containsKey(id);}
    boolean holdFlow(UUID id){var d=id==null?null:repo.peek().debits().get(id);return d!=null&&d.unresolved();}
    Map<String,Object> status(UUID id){try(var guard=SuiteState.guard()){var image=repo.image();var d=image.debits().get(id);return d==null?Map.of("operation",id.toString(),"status","ABSENT"):view(image,d);}}
    private Map<String,Object> view(TreasuryRepository.Image image,AsyncDebit d){try(var guard=SuiteState.guard()){if(indexedReceipts!=image.debits()){var index=new HashMap<UUID,AsyncDebit>();for(var r:image.debits().values())if(r.terms().kind()==AsyncDebit.Kind.REFUND&&r.state()!=REJECTED)index.put(r.terms().backingDebit(),r);refundIndex=Map.copyOf(index);indexedReceipts=image.debits();}var out=new HashMap<>(d.view());if(d.terms().kind()==AsyncDebit.Kind.DEBIT){var refund=refundIndex.get(d.terms().operation());out.put("backingAvailable",d.state()==PAID&&refund==null);out.put("refundedCents",refund!=null&&refund.state()==PAID?refund.terms().cents():0L);out.put("refundOperation",refund==null?"":refund.terms().operation().toString());}return Map.copyOf(out);}}
    List<Map<String,Object>> statuses(){try(var guard=SuiteState.guard()){var image=repo.image();return image.debits().values().stream().map(d->view(image,d)).toList();}}
    private boolean ownerReady(String owner){var p=Bukkit.getPluginManager().getPlugin(owner);return p!=null&&p.isEnabled()&&!SuiteState.frozen(owner);}
    CompletionStage<Map<String,Object>> request(AsyncDebit.Terms terms){try(var guard=SuiteState.guard()){
        var before=repo.image();var previous=before.debits().get(terms.operation());if(previous!=null){previous.requireSame(terms);return CompletableFuture.completedFuture(view(before,previous));}
        if(stopping||busy()||repo.busy())return failed("Казна сохраняет другое изменение; повторите тот же ID позже");
        if(!ownerReady(terms.owner()))return failed("Аддон-владелец запроса выключен или приостановлен");
        host.requireAsyncReady(terms.town());if(before.debits().size()>=AsyncDebit.MAX_RECEIPTS)return failed("Архив квитанций достиг предела; новые списания приостановлены");
        var city=before.cities().get(terms.town());if(city==null)return failed("Казна города ещё не инициализирована");
        long now=System.currentTimeMillis();boolean refund=terms.kind()==AsyncDebit.Kind.REFUND;if(refund&&city.pending()!=null)return failed("Бюджет города занят другой квитанцией; повторите тот же ID возврата позже");boolean allowed=refund||LedgerEngine.canSpend(city,Budget.parse(terms.category()),terms.cents());
        var receipt=new AsyncDebit(terms,allowed?PREPARED:REJECTED,now,now,allowed?"":"Недостаточно средств в статье бюджета либо уже есть незавершённый счёт");
        var cities=new HashMap<>(before.cities());if(allowed)cities.put(terms.town(),refund?city.next(city.balance(),city.enabled(),city.funds(),city.shares(),city.weeks(),terms.pending(now),null):LedgerEngine.prepare(city,terms.pending(now)));var records=new HashMap<>(before.debits());records.put(terms.operation(),receipt);
        var future=repo.commit(before,new TreasuryRepository.Image(cities,records)).thenApply(x->view(x,receipt));track(future);return future;
    }}
    CompletionStage<Map<String,Object>> refund(UUID refundOperation,UUID paidDebitOperation,UUID actor,String owner,String reason){try(var guard=SuiteState.guard()){
        var image=repo.image();var debit=image.debits().get(paidDebitOperation);if(debit==null||debit.state()!=PAID||debit.terms().kind()!=AsyncDebit.Kind.DEBIT||!debit.terms().owner().equals(owner))return failed("Нет оплаченного списания этого аддона для полного возврата");
        var t=debit.terms();var terms=new AsyncDebit.Terms(refundOperation,t.town(),t.category(),t.source(),t.cents(),reason,actor,owner,AsyncDebit.Kind.REFUND,paidDebitOperation);
        var existing=image.debits().get(refundOperation);if(existing!=null){existing.requireSame(terms);return CompletableFuture.completedFuture(view(image,existing));}
        if(image.debits().values().stream().anyMatch(d->paidDebitOperation.equals(d.terms().backingDebit())&&d.state()!=REJECTED))return failed("Для этого списания уже есть возврат или квитанция сверки");
        return request(terms);
    }}
    private static <T>CompletionStage<T> failed(String why){return CompletableFuture.failedFuture(new IllegalStateException(why));}
    private void track(CompletionStage<?> future){operation=future;future.whenComplete((v,e)->{if(e!=null)plugin.getLogger().log(java.util.logging.Level.WARNING,"Подтверждаемая операция казны сохранена для проверки",e);});}
    void pulse(){
        if(stopping||busy()||repo.busy()||!repo.writable())return;
        if(recover())return;
        for(var d:repo.image().debits().values())if(!d.audited()){track(audit(d));return;}
        for(var d:repo.image().debits().values())if(d.state()==PREPARED&&ownerReady(d.terms().owner())){start(d);return;}
    }
    /** Recovery only: safe even while a captured-flow gap blocks every new bank call. */
    boolean recover(){if(stopping||busy()||repo.busy()||!repo.writable())return false;
        for(var d:repo.image().debits().values())if(d.state()==INVOKING&&!d.terms().operation().equals(invoking)){track(finish(d,new Outcome(REVIEW,"Сервер остановился во время банковского вызова; проверьте точную квитанцию")));return true;}return false;
    }
    private void start(AsyncDebit d){
        var t=d.terms();if(!host.asyncReady(t.town()))return;var town=TownyAPI.getInstance().getTown(t.town());if(town==null){track(finish(d,new Outcome(t.kind()==AsyncDebit.Kind.REFUND?REVIEW:REJECTED,"Город удалён до банковского вызова; возврат требует сверки обеспечения")));return;}
        if(!TownyEconomyHandler.isActive()||TownyEconomyHandler.activeAdapter()==null)return;
        final BankAccount account=town.getAccount();var before=repo.image();var current=before.debits().get(t.operation());if(!d.equals(current))return;
        var records=new HashMap<>(before.debits());var started=d.state(INVOKING,System.currentTimeMillis(),"");records.put(t.operation(),started);invoking=t.operation();
        var future=repo.commit(before,new TreasuryRepository.Image(before.cities(),records)).thenCompose(ignored->bank(started,account)).thenCompose(result->finish(started,result));
        track(future.whenComplete((v,e)->{try(var guard=SuiteState.guard()){if(t.operation().equals(invoking))invoking=null;}}));
    }
    private CompletionStage<Outcome> bank(AsyncDebit debit,BankAccount account){
        if(stopping)return CompletableFuture.completedFuture(new Outcome(PREPARED,"Остановка до банковского вызова"));
        var request=new RawRequest();raw=request;
        try{Bukkit.getGlobalRegionScheduler().run(plugin,task->{
            if(request.result.isDone())return;request.running=true;Outcome outcome;
            if(stopping||!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!ownerReady(debit.terms().owner()))outcome=new Outcome(PREPARED,"Административная пауза до вызова банка");
            else try{outcome=withdraw(debit,account);}catch(Throwable error){outcome=new Outcome(REVIEW,"Банковский callback завершился без подтверждения: "+error.getClass().getSimpleName());}
            complete(request,outcome);
        });}catch(RuntimeException e){complete(request,new Outcome(PREPARED,"Планировщик остановлен до вызова банка"));}return request.result;
    }
    private Outcome withdraw(AsyncDebit d,BankAccount account){
        var t=d.terms();boolean refund=t.kind()==AsyncDebit.Kind.REFUND;String token="["+(refund?"async-refund":"async-budget")+":"+t.operation()+"]";var proof=new PaymentEvidence(account,refund,token,t.cents());
        var observer=new AccountObserver(){public void withdrew(Account a,double n,String reason){proof.observe(a,false,n,reason);}public void deposited(Account a,double n,String reason){proof.observe(a,true,n,reason);}};
        synchronized(account){
            try{double balance=account.getHoldingBalance(true);if(!Double.isFinite(balance))return new Outcome(PREPARED,"Баланс банка пока недоступен");
                if(refund&&(balance>Money.MAX/100.0-t.cents()/100.0||Money.cents(balance)>Money.MAX-t.cents()))return new Outcome(PREPARED,"Возврат превысит допустимый баланс; требуется проверка");
                if(!refund&&balance<t.cents()/100.0||!host.rawBudgetAllows(t))return new Outcome(refund?PREPARED:REJECTED,"Средства или статья бюджета временно недоступны до вызова банка");
            }catch(Throwable e){return new Outcome(PREPARED,"Не удалось проверить баланс до банковского вызова");}
            account.addObserver(observer);try{
                try{if(refund)account.deposit(t.cents()/100.0,"[NLT|refund|"+t.category()+"|"+t.source()+"] "+token+" "+t.reason());else account.withdraw(t.cents()/100.0,"[NLT|"+t.operation()+"|"+t.category()+"|"+t.source()+"] "+token+" "+t.reason());}
                catch(Throwable e){if(!proof.observed())return new Outcome(REVIEW,"Банковский вызов не подтвердил точную квитанцию: "+e.getClass().getSimpleName());}
                return proof.observed()?new Outcome(PAID,""):new Outcome(REVIEW,"Банк вернул ответ без точного наблюдения суммы и ID; автоматического повтора нет");
            }finally{account.removeObserver(observer);}
        }
    }
    private void complete(RawRequest request,Outcome outcome){
        Runnable action=()->{try(var guard=SuiteState.guard()){request.result.complete(outcome);if(raw==request)raw=null;}};
        try{results.execute(action);}catch(RejectedExecutionException e){if(!request.result.isDone())CompletableFuture.runAsync(action);}
    }
    private CompletionStage<Void> finish(AsyncDebit previous,Outcome outcome){try(var guard=SuiteState.guard()){
        var before=repo.image();var current=before.debits().get(previous.terms().operation());if(current==null)return failed("Квитанция исчезла до подтверждения");
        var terms=current.terms();var cities=new HashMap<>(before.cities());var city=cities.get(terms.town());if(city==null)return failed("Учёт города исчез до подтверждения");
        if(outcome.state()==PAID){if(city.pending()==null||!city.pending().id().equals(terms.operation()))return failed("Резерв бюджета не совпадает с оплаченной квитанцией");boolean refund=terms.kind()==AsyncDebit.Kind.REFUND;if(refund)city=LedgerEngine.cancel(city,terms.operation());city=LedgerEngine.observe(city,terms.cents(),refund,Budget.parse(terms.category()),terms.source(),refund,System.currentTimeMillis(),terms.operation());}
        else if(outcome.state()==REJECTED){if(city.pending()!=null&&city.pending().id().equals(terms.operation()))city=LedgerEngine.cancel(city,terms.operation());}
        cities.put(terms.town(),city);var records=new HashMap<>(before.debits());records.put(terms.operation(),current.state(outcome.state(),System.currentTimeMillis(),outcome.reason()));
        return repo.commit(before,new TreasuryRepository.Image(cities,records)).thenAccept(ack->{if(outcome.state()==PAID)host.removeAsyncFlow(terms.operation());});
    }}
    private CompletionStage<Void> audit(AsyncDebit receipt){return audit.append(receipt).thenCompose(ok->{if(!ok)return CompletableFuture.completedFuture(null);try(var guard=SuiteState.guard()){
        var before=repo.image();var current=before.debits().get(receipt.terms().operation());if(!receipt.equals(current))return CompletableFuture.completedFuture(null);var records=new HashMap<>(before.debits());records.put(receipt.terms().operation(),receipt.auditAck());return repo.commit(before,new TreasuryRepository.Image(before.cities(),records)).thenApply(v->null);
    }});}
    CompletionStage<Map<String,Object>> resolve(UUID id,boolean paid,String actor){try(var guard=SuiteState.guard()){
        if(stopping||busy()||repo.busy())return failed("Казна завершает другую операцию");var current=repo.image().debits().get(id);if(current==null||current.state()!=REVIEW)return failed("Нет конкретной квитанции REVIEW для сверки");
        var future=finish(current,new Outcome(paid?PAID:REJECTED,"Ручная сверка "+actor+": "+(paid?"списание подтверждено":"списания не было"))).thenApply(v->status(id));track(future);return future;
    }}
    CompletionStage<Void> stop(){try(var guard=SuiteState.guard()){
        stopping=true;if(raw!=null&&!raw.running&&!raw.result.isDone())raw.result.complete(new Outcome(PREPARED,"Остановка до банковского вызова"));
        return operation.handle((v,e)->null).thenCompose(v->audit.closeAsync()).thenApply(v->{results.shutdown();return null;});
    }}
}
