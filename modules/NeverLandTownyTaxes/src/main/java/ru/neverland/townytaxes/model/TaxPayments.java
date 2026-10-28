package ru.neverland.townytaxes.model;

import java.util.*;
import java.util.concurrent.*;
import static ru.neverland.townytaxes.model.TaxPayment.Phase.*;

/** Durable acknowledgements run before bank calls; all bank callbacks use the supplied server executor. */
public final class TaxPayments {
    public enum Leg { DEBIT,CREDIT,REFUND }
    public interface Store {TaxPayment get(UUID id);CompletionStage<Boolean> transition(TaxPayment.Phase expected,TaxPayment next);}
    public interface Bank {boolean ready(TaxPayment value,Leg leg);boolean apply(TaxPayment value,Leg leg)throws Exception;}
    private final Store store;private final Bank bank;private final Executor callbacks;
    public TaxPayments(Store store,Bank bank,Executor callbacks){this.store=store;this.bank=bank;this.callbacks=callbacks;}
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    public CompletionStage<Void> process(UUID id){
        var value=store.get(id);if(value==null||value.terminal()||value.review())return done();
        return switch(value.phase()){
            case PREPARED->attempt(value,Leg.DEBIT,DEBITING,DEBITED,REJECTED);
            case DEBITED->value.destinationScope()==Domain.Scope.GLOBAL?store.transition(DEBITED,value.phase(DONE)).thenApply(v->null):attempt(value,Leg.CREDIT,CREDITING,DONE,DEBITED);
            case REFUND_READY->attempt(value,Leg.REFUND,REFUNDING,REFUNDED,REFUND_READY);
            default->done();
        };
    }
    private CompletionStage<Void> attempt(TaxPayment value,Leg leg,TaxPayment.Phase pending,TaxPayment.Phase success,TaxPayment.Phase rejected){
        if(!bank.ready(value,leg))return done();
        return store.transition(value.phase(),value.phase(pending)).thenComposeAsync(won->{
            if(!won)return done();boolean applied;
            try{applied=bank.apply(value,leg);}catch(Exception unknown){return done();}
            return store.transition(pending,store.get(value.id()).phase(applied?success:rejected)).thenComposeAsync(saved->{
                if(!saved||!applied)return done();return process(value.id());
            },callbacks);
        },callbacks);
    }
    public CompletionStage<Boolean> resolve(UUID id,Leg leg,boolean applied){
        var value=Objects.requireNonNull(store.get(id),"Квитанция не найдена");
        var expected=switch(leg){case DEBIT->DEBITING;case CREDIT->CREDITING;case REFUND->REFUNDING;};if(value.phase()!=expected)throw new IllegalStateException("Квитанция не ожидает сверки этого этапа");
        return store.transition(expected,value.phase(switch(leg){case DEBIT->applied?DEBITED:PREPARED;case CREDIT->applied?DONE:DEBITED;case REFUND->applied?REFUNDED:REFUND_READY;}));
    }
    public CompletionStage<Boolean> cancel(UUID id){
        var value=Objects.requireNonNull(store.get(id),"Квитанция не найдена");if(value.phase()==REFUND_READY||value.phase()==REFUNDED||value.phase()==VOID)return CompletableFuture.completedFuture(false);
        if(value.phase()==PREPARED)return store.transition(PREPARED,value.phase(VOID));if(value.phase()==DEBITED)return store.transition(DEBITED,value.phase(REFUND_READY));throw new IllegalStateException("Сначала сверить неизвестный платёж; подтверждённое зачисление отменой не возвращается");
    }
}
