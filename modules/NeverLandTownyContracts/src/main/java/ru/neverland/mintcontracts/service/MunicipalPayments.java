package ru.neverland.mintcontracts.service;
import java.util.UUID;
import ru.neverland.mintcontracts.model.MunicipalPayment;
import static ru.neverland.mintcontracts.model.MunicipalPayment.*;

/** At most one automatic bank attempt once PENDING is durable. Unknown outcomes require evidence. */
public final class MunicipalPayments {
    public interface Store {MunicipalPayment get(UUID id);void put(MunicipalPayment payment)throws Exception;void result(MunicipalPayment payment,boolean paid)throws Exception;}
    public interface Bank {boolean transfer(MunicipalPayment payment)throws Exception;}
    private final Store store;private final Bank bank;
    public MunicipalPayments(Store store,Bank bank){this.store=store;this.bank=bank;}
    public void process(UUID id)throws Exception {
        var p=store.get(id);if(p==null||p.phase()!=Phase.READY)return;
        store.put(p.phase(Phase.PENDING));boolean paid;
        try{paid=bank.transfer(p);}catch(Exception ex){return;}
        resolve(id,paid);
    }
    public void resolve(UUID id,boolean paid)throws Exception {
        var p=store.get(id);if(p==null||p.phase()!=Phase.PENDING)throw new IllegalArgumentException("Платёж не ожидает сверки");
        Phase next=paid?Phase.DONE:p.kind()==Kind.RESERVE?Phase.REJECTED:Phase.READY;
        store.result(p.phase(next),paid);
    }
}
