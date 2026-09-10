package ru.neverland.townymarket;
import java.util.*;
import static ru.neverland.townymarket.MarketData.*;
/** Single purchase saga. Ambiguous bank results are persistent stops, never blind retries. */
public final class MarketPayments {
    public interface Store {Order order(UUID id);void put(Order order)throws Exception;}
    public interface Gateway {String ready(Order o);String reserve(Order o)throws Exception;boolean debit(Order o)throws Exception;String deliver(Order o)throws Exception;boolean credit(Order o)throws Exception;String refund(Order o)throws Exception;String receipt(Order o)throws Exception;void acknowledge(Order o)throws Exception;}
    private MarketPayments(){}
    public static void advance(UUID id,long now,long retry,Store store,Gateway gateway)throws Exception {
        Order o=store.order(id);if(o==null||o.finalized()||o.check()>now)return;
        switch(o.phase()){
            case PREPARED -> {
                String blocked=now>=o.created()+60_000?"Истёк срок неоплаченной покупки":gateway.ready(o);
                if(blocked!=null){store.put(o.phase(Phase.RETURNING,now,blocked));return;}
                String reserved=gateway.reserve(o);
                if(!reserved.equals("HELD")){if(Set.of("DELIVERED","PICKUP","CLAIM_PENDING","CLAIMED").contains(reserved))throw new IllegalStateException("Неоплаченный товар уже выдан");store.put(o.phase(Phase.RETURNING,now,stockNote(reserved)));return;}
                o=o.phase(Phase.DEBIT_PENDING,now,"Списание ожидает сверки");store.put(o);
                boolean paid=gateway.debit(o);store.put(o.phase(paid?Phase.PAID:Phase.RETURNING,now,paid?"Оплачено, товар готовится к выдаче":"Банк отказал в списании"));
            }
            case PAID -> {
                String status=gateway.deliver(o);
                if(status.equals(o.city()?"DELIVERED":"PICKUP"))store.put(o.phase(Phase.DELIVERED,now,"Товар передан; ожидает оплаты продавцу"));
                else if(Set.of("MISSING","RETURNED").contains(status))throw new IllegalStateException("Оплаченный резерв потерян: "+o.id());
                else store.put(o.phase(Phase.PAID,now+retry,stockNote(status)));
            }
            case DELIVERED -> {
                o=o.phase(Phase.CREDIT_PENDING,now,"Зачисление ожидает сверки");store.put(o);boolean paid=gateway.credit(o);
                store.put(o.phase(paid?Phase.COMPLETE:Phase.DELIVERED,paid?now:now+retry,paid?(o.city()?"Доставлено в склад города":"Оплачено. Заберите покупку в меню рынка"):"Банк продавца временно недоступен"));
            }
            case RETURNING -> {
                String status=gateway.refund(o);if(status.equals("RETURNED")||status.equals("MISSING"))store.put(o.phase(Phase.CANCELLED,now,o.note()));
                else if(Set.of("DELIVERED","PICKUP","CLAIM_PENDING","CLAIMED").contains(status))throw new IllegalStateException("Неоплаченная покупка уже доставлена");
                else store.put(o.phase(Phase.RETURNING,now+retry,stockNote(status)));
            }
            case COMPLETE, CANCELLED -> {
                String receipt=gateway.receipt(o);
                if(o.phase()==Phase.COMPLETE&&!o.city()&&!Set.of("CLAIMED","MISSING").contains(receipt))return;
                gateway.acknowledge(o);store.put(o.finish());
            }
            case DEBIT_PENDING, CREDIT_PENDING -> { }
        }
    }
    public static Order resolve(Order o,String decision,long now){if(o==null)throw new IllegalArgumentException("Покупка не найдена");Phase p=switch(decision){
        case "debit-paid"->o.phase()==Phase.DEBIT_PENDING?Phase.PAID:null;case "debit-unpaid"->o.phase()==Phase.DEBIT_PENDING?Phase.RETURNING:null;
        case "credit-paid"->o.phase()==Phase.CREDIT_PENDING?Phase.COMPLETE:null;case "credit-unpaid"->o.phase()==Phase.CREDIT_PENDING?Phase.DELIVERED:null;default->null;};
        if(p==null)throw new IllegalArgumentException("Решение не соответствует этапу платежа");return o.phase(p,now,"Платёж сверен администратором");}
    public static String stockNote(String status){return switch(status){case "BUSY"->"Склад открыт игроком";case "FULL"->"Недостаточно места";case "STOCK_LOW"->"Товара уже недостаточно";case "CLOSED"->"Предложение закрыто";case "CITY_MISSING"->"Город удалён";case "CLAIM_PENDING"->"Выдача ожидает сверки";default->"Склад временно недоступен";};}
}
