package ru.neverland.core;
import java.util.*;

/** Single purchase saga. Ambiguous bank results are persistent stops, never blind retries. */
public final class PurchaseSaga {
    public interface Purchase<O> {
        UUID id();long created();long check();boolean city();boolean finalized();String note();String paymentStep();
        O paymentStep(String step,long at,String note);O finish();
    }
    public interface Store<O> {O order(UUID id);void put(O order)throws Exception;}
    public interface Gateway<O> {String ready(O o);String reserve(O o)throws Exception;boolean debit(O o)throws Exception;String deliver(O o)throws Exception;boolean credit(O o)throws Exception;String refund(O o)throws Exception;String receipt(O o)throws Exception;void acknowledge(O o)throws Exception;}
    private PurchaseSaga(){}
    public static <O extends Purchase<O>> void advance(UUID id,long now,long retry,Store<O> store,Gateway<O> gateway)throws Exception {
        O o=store.order(id);if(o==null||o.finalized()||o.check()>now)return;
        switch(o.paymentStep()){
            case "PREPARED" -> {
                String blocked=now>=o.created()+60_000?"Истёк срок неоплаченной покупки":gateway.ready(o);
                if(blocked!=null){store.put(o.paymentStep("RETURNING",now,blocked));return;}
                String reserved=gateway.reserve(o);
                if(!reserved.equals("HELD")){if(Set.of("DELIVERED","PICKUP","CLAIM_PENDING","CLAIMED").contains(reserved))throw new IllegalStateException("Неоплаченный товар уже выдан");store.put(o.paymentStep("RETURNING",now,stockNote(reserved)));return;}
                o=o.paymentStep("DEBIT_PENDING",now,"Списание ожидает сверки");store.put(o);
                boolean paid=gateway.debit(o);store.put(o.paymentStep(paid?"PAID":"RETURNING",now,paid?"Оплачено, товар готовится к выдаче":"Банк отказал в списании"));
            }
            case "PAID" -> {
                String status=gateway.deliver(o);
                if(status.equals(o.city()?"DELIVERED":"PICKUP"))store.put(o.paymentStep("DELIVERED",now,"Товар передан; ожидает оплаты продавцу"));
                else if(Set.of("MISSING","RETURNED").contains(status))throw new IllegalStateException("Оплаченный резерв потерян: "+o.id());
                else store.put(o.paymentStep("PAID",now+retry,stockNote(status)));
            }
            case "DELIVERED" -> {
                o=o.paymentStep("CREDIT_PENDING",now,"Зачисление ожидает сверки");store.put(o);boolean paid=gateway.credit(o);
                store.put(o.paymentStep(paid?"COMPLETE":"DELIVERED",paid?now:now+retry,paid?(o.city()?"Доставлено в склад города":"Оплачено. Заберите покупку в меню рынка"):"Банк продавца временно недоступен"));
            }
            case "RETURNING" -> {
                String status=gateway.refund(o);if(status.equals("RETURNED")||status.equals("MISSING"))store.put(o.paymentStep("CANCELLED",now,o.note()));
                else if(Set.of("DELIVERED","PICKUP","CLAIM_PENDING","CLAIMED").contains(status))throw new IllegalStateException("Неоплаченная покупка уже доставлена");
                else store.put(o.paymentStep("RETURNING",now+retry,stockNote(status)));
            }
            case "COMPLETE", "CANCELLED" -> {
                String receipt=gateway.receipt(o);
                if(o.paymentStep().equals("COMPLETE")&&!o.city()&&!Set.of("CLAIMED","MISSING").contains(receipt))return;
                gateway.acknowledge(o);store.put(o.finish());
            }
            case "DEBIT_PENDING", "CREDIT_PENDING" -> { }
        }
    }
    public static <O extends Purchase<O>> O resolve(O o,String decision,long now){if(o==null)throw new IllegalArgumentException("Покупка не найдена");String p=switch(decision){
        case "debit-paid"->o.paymentStep().equals("DEBIT_PENDING")?"PAID":null;case "debit-unpaid"->o.paymentStep().equals("DEBIT_PENDING")?"RETURNING":null;
        case "credit-paid"->o.paymentStep().equals("CREDIT_PENDING")?"COMPLETE":null;case "credit-unpaid"->o.paymentStep().equals("CREDIT_PENDING")?"DELIVERED":null;default->null;};
        if(p==null)throw new IllegalArgumentException("Решение не соответствует этапу платежа");return o.paymentStep(p,now,"Платёж сверен администратором");}
    public static String stockNote(String status){return switch(status){case "BUSY"->"Склад открыт игроком";case "FULL"->"Недостаточно места";case "STOCK_LOW"->"Товара уже недостаточно";case "CLOSED"->"Предложение закрыто";case "CITY_MISSING"->"Город удалён";case "CLAIM_PENDING"->"Выдача ожидает сверки";default->"Склад временно недоступен";};}
}
