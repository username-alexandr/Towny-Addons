package ru.neverland.minttrade.contract;
import java.util.*;
import java.io.IOException;
import static ru.neverland.minttrade.contract.SupplyContract.*;
/** Persist intent before external side effects. An unknown bank outcome is NEVER retried automatically. */
public final class SupplyProcessor {
    public interface Store {SupplyContract get(UUID id);void put(SupplyContract contract)throws IOException;}
    public interface Gateway {
        String ready(SupplyContract contract); // null means ready; evaluated anew before each unpaid supply
        String reserve(SupplyContract contract)throws Exception;
        String settle(SupplyContract contract,boolean deliver)throws Exception;
        boolean debit(SupplyContract contract)throws Exception;
        boolean credit(SupplyContract contract)throws Exception;
        void acknowledge(SupplyContract contract)throws Exception;
    }
    private SupplyProcessor(){}
    public static void advance(UUID id,long now,long retry,Store store,Gateway gateway)throws Exception {
        var c=store.get(id);if(c==null||now<c.nextCheck())return;
        if(c.attempt()==null){
            if(!c.enabled()||now<c.nextDue())return;
            String blocked=gateway.ready(c);if(blocked!=null){store.put(c.waiting(now+retry,blocked));return;}
            c=c.attempt(new Attempt(UUID.randomUUID(),Phase.PREPARED,now),now,"Подготовка поставки");store.put(c);
        }
        switch(c.attempt().phase()) {
            case PREPARED -> {
                String blocked=c.enabled()?gateway.ready(c):"Договор остановлен";
                if(blocked!=null){store.put(c.attempt(c.attempt().phase(Phase.RETURNING),now,blocked));return;}
                String status=gateway.reserve(c);
                if(!status.equals("RESERVED")){if(status.equals("DELIVERED")||status.equals("RETURNED"))throw new IllegalStateException("Квитанция склада противоречит договору");store.put(c.waiting(now+retry,warehouseNote(status)));return;}
                c=c.attempt(c.attempt().phase(Phase.DEBIT_PENDING),now,"Результат списания требует сверки");store.put(c);
                // Deliberately outside generic retry handling: exception leaves DEBIT_PENDING on disk.
                boolean paid=gateway.debit(c);
                store.put(c.attempt(c.attempt().phase(paid?Phase.PAID:Phase.RETURNING),now,paid?"Оплата зарезервирована":"Банк отклонил списание"));
            }
            case PAID -> {
                String status=gateway.settle(c,true);
                if(status.equals("DELIVERED"))store.put(c.attempt(c.attempt().phase(Phase.DELIVERED),now,"Товар доставлен; ожидает оплаты продавцу"));
                else {if(status.equals("RETURNED")||status.equals("MISSING"))throw new IllegalStateException("Оплаченный резерв не найден");store.put(c.waiting(now+retry,warehouseNote(status)));}
            }
            case DELIVERED -> {
                c=c.attempt(c.attempt().phase(Phase.CREDIT_PENDING),now,"Результат зачисления требует сверки");store.put(c);
                boolean paid=gateway.credit(c);
                store.put(c.attempt(c.attempt().phase(paid?Phase.COMPLETE:Phase.DELIVERED),paid?now:now+retry,paid?"Расчёт завершён":"Банк продавца отклонил зачисление"));
            }
            case RETURNING -> {
                String status=gateway.settle(c,false);
                if(status.equals("RETURNED")||status.equals("MISSING"))store.put(c.attempt(c.attempt().phase(Phase.RETURNED),now,c.note()));
                else {if(status.equals("DELIVERED"))throw new IllegalStateException("Неоплаченный товар уже доставлен");store.put(c.waiting(now+retry,warehouseNote(status)));}
            }
            case COMPLETE, RETURNED -> {gateway.acknowledge(c);store.put(c.finish(now,retry));}
            case DEBIT_PENDING, CREDIT_PENDING -> { /* A restart or ambiguous bank result requires operator reconciliation. */ }
        }
    }
    public static SupplyContract resolve(SupplyContract c,UUID attempt,String decision,long now){
        if(c==null||c.attempt()==null||!c.attempt().id().equals(attempt))throw new IllegalArgumentException("Укажите полный текущий ID поставки");
        Phase from=c.attempt().phase();Phase next=switch(decision){
            case "debit-paid" -> from==Phase.DEBIT_PENDING?Phase.PAID:null;
            case "debit-unpaid" -> from==Phase.DEBIT_PENDING?Phase.RETURNING:null;
            case "credit-paid" -> from==Phase.CREDIT_PENDING?Phase.COMPLETE:null;
            case "credit-unpaid" -> from==Phase.CREDIT_PENDING?Phase.DELIVERED:null;
            default -> null;};
        if(next==null)throw new IllegalArgumentException("Решение не соответствует ожидающему платежу");
        return c.attempt(c.attempt().phase(next),now,"Результат платежа подтверждён администратором");
    }
    public static String warehouseNote(String status){return switch(status){
        case "BUSY" -> "Склад открыт игроком";case "STOCK_LOW" -> "На складе продавца не хватает товара";
        case "FULL" -> "Недостаточно места на складе";case "CITY_MISSING" -> "Один из городов удалён";
        default -> "API поставок склада недоступно";};}
}
