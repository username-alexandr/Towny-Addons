package ru.neverland.mintcontracts.service;
import java.util.UUID;
import ru.neverland.mintcontracts.model.MunicipalPayment;
import static ru.neverland.mintcontracts.model.MunicipalPayment.*;

/** At most one automatic bank attempt once PENDING is durable. Unknown outcomes require evidence. */
public final class MunicipalPayments {
    public interface Store {MunicipalPayment get(UUID id);void put(MunicipalPayment payment)throws Exception;void result(MunicipalPayment payment,boolean paid)throws Exception;}
    public enum Outcome { PAID, REJECTED, WAITING, REVIEW }
    public interface Bank {boolean transfer(MunicipalPayment payment)throws Exception;
        default boolean asynchronous(MunicipalPayment payment){return false;}
        default Outcome poll(MunicipalPayment payment)throws Exception{throw new UnsupportedOperationException();}
    }
    private final Store store;private final Bank bank;
    public MunicipalPayments(Store store,Bank bank){this.store=store;this.bank=bank;}
    public void process(UUID id)throws Exception { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        var p=store.get(id);if(p==null)return;
        if(p.phase()==Phase.READY&&bank.asynchronous(p)){store.put(p.phase(Phase.TREASURY));p=store.get(id);}
        if(p.phase()==Phase.TREASURY){Outcome result;try{result=bank.poll(p);}catch(Exception unavailable){return;}
            if(result==Outcome.PAID||result==Outcome.REJECTED)store.result(p.phase(result==Outcome.PAID?Phase.DONE:Phase.REJECTED),result==Outcome.PAID);return;}
        if(p.phase()!=Phase.READY)return;
        store.put(p.phase(Phase.PENDING));boolean paid;
        try{paid=bank.transfer(p);}catch(Exception ex){return;}
        resolve(id,paid);
    } }
    public void resolve(UUID id,boolean paid)throws Exception { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        var p=store.get(id);if(p==null||p.phase()!=Phase.PENDING)throw new IllegalArgumentException("Платёж не ожидает сверки");
        Phase next=paid?Phase.DONE:p.kind()==Kind.RESERVE?Phase.REJECTED:Phase.READY;
        store.result(p.phase(next),paid);
    } }
}
