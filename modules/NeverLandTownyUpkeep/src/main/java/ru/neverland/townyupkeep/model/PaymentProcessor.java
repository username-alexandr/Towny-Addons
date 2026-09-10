package ru.neverland.townyupkeep.model;
import java.util.*;
import static ru.neverland.townyupkeep.model.Entry.*;
/** Durable saga. An interrupted external money call is never guessed or retried. */
public final class PaymentProcessor {
    public interface Store { Entry get(Key key);void put(Key key,Entry value)throws Exception; }
    public interface Gateway {
        boolean reserve(UUID id,UUID town,Map<String,Long> amounts)throws Exception;
        void settle(UUID id,boolean consume)throws Exception;
        boolean canPay(UUID town,long cents)throws Exception;
        boolean withdraw(UUID town,long cents,UUID invoice)throws Exception;
        void forget(UUID id)throws Exception;
    }
    private final Store store;private final Gateway gateway;
    public PaymentProcessor(Store store,Gateway gateway){this.store=store;this.gateway=gateway;}
    public void process(Key key,long clock,int retry)throws Exception{
        Entry entry=store.get(key);if(entry==null||entry.invoice()==null)return;
        Invoice bill=entry.invoice();
        if(bill.phase()==Phase.MONEY_PENDING)return; // Administrator must reconcile the Towny transaction log.
        if(bill.phase()==Phase.PREPARED){
            if(!gateway.reserve(bill.id(),key.town(),bill.cost().resources())){
                store.put(key,new Entry(false,clock+retry,"Не хватает ресурсов с учётом резерва города",null));return;}
            if(bill.cost().money()>0){
                if(!gateway.canPay(key.town(),bill.cost().money())){cancel(key,"Не хватает денег или экономика недоступна");}
                else {
                    store.put(key,entry.phase(Phase.MONEY_PENDING));
                    boolean paid=gateway.withdraw(key.town(),bill.cost().money(),bill.id());
                    if(paid)store.put(key,store.get(key).phase(Phase.MONEY_PAID));else cancel(key,"Казна не оплатила содержание");
                }
            }else store.put(key,entry.phase(Phase.MONEY_PAID));
        }
        entry=store.get(key);bill=entry.invoice();
        if(bill.phase()==Phase.MONEY_PAID){
            gateway.settle(bill.id(),true);
            store.put(key,new Entry(true,clock+bill.period(),"Содержание оплачено",null));
        }else if(bill.phase()==Phase.CANCELLED){
            gateway.settle(bill.id(),false);
            store.put(key,new Entry(false,clock+retry,entry.reason(),null));
        }else return;
        // The completed payment is already durable. Cleanup failures must not undo it or charge it again.
        try{gateway.forget(bill.id());}catch(Exception ignored){}
    }
    public void cancel(Key key,String reason)throws Exception{var e=store.get(key);store.put(key,new Entry(false,e.due(),reason,e.invoice().phase(Phase.CANCELLED)));}
    public void resolve(Key key,boolean paid)throws Exception{
        var e=store.get(key);if(e==null||e.invoice()==null||e.invoice().phase()!=Phase.MONEY_PENDING)throw new IllegalArgumentException("Счёт не требует сверки");
        store.put(key,new Entry(false,e.due(),paid?"Оплата подтверждена администратором":"Списание не произошло — возврат резерва",e.invoice().phase(paid?Phase.MONEY_PAID:Phase.CANCELLED)));
    }
}
