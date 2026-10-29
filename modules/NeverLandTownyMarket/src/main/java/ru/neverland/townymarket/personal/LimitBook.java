package ru.neverland.townymarket.personal;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** A fully funded, immutable personal exchange. External effects require durable pending receipts first. */
public final class LimitBook {
    private LimitBook() {}
    public static final int MAX_ORDERS=5_000,MAX_RECEIPTS=20_000,MAX_FILLS=20_000,MAX_QUANTITY=3456,MAX_ACTIVE=32;
    public static final long MAX_CENTS=100_000_000_000L,MAX_TOTAL_CENTS=MAX_CENTS*MAX_ORDERS;
    public enum Side { BUY, SELL }
    public enum Status { FUNDING, OPEN, CANCELLED, FILLED }
    public enum Kind { BUY_FUND, SELL_STOCK, CASH_CLAIM, ITEM_CLAIM }
    public enum ReceiptStatus { PENDING, DONE, FAILED }
    public record Order(UUID id,UUID owner,Side side,String product,int quantity,int remaining,long unitCents,long created,Status status) {
        public Order {
            Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(side);Objects.requireNonNull(status);LimitBook.product(product);LimitBook.quantity(quantity);money(unitCents);
            if(unitCents>MAX_CENTS/quantity||remaining<0||remaining>quantity||created<1||status==Status.FUNDING&&remaining!=quantity||status==Status.OPEN&&remaining==0||(status==Status.CANCELLED||status==Status.FILLED)&&remaining!=0)throw invalid("Неверная заявка");
        }
    }
    public record Receipt(UUID id,UUID owner,Kind kind,UUID order,String product,int quantity,long cents,long created,ReceiptStatus status,long resolvedAt) {
        public Receipt {
            Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(kind);Objects.requireNonNull(status);
            if(created<1||status==ReceiptStatus.PENDING&&resolvedAt!=0||status!=ReceiptStatus.PENDING&&resolvedAt<created)throw invalid("Неверное время квитанции");
            switch(kind){
                case BUY_FUND -> {Objects.requireNonNull(order);LimitBook.product(product);LimitBook.quantity(quantity);money(cents);}
                case SELL_STOCK -> {Objects.requireNonNull(order);LimitBook.product(product);LimitBook.quantity(quantity);if(cents!=0)throw invalid("Деньги в квитанции товара");}
                case CASH_CLAIM -> {if(order!=null||!"".equals(product)||quantity!=0)throw invalid("Неверная выдача денег");money(cents);}
                case ITEM_CLAIM -> {if(order!=null||cents!=0)throw invalid("Неверная выдача товара");LimitBook.product(product);LimitBook.quantity(quantity);}
            }
        }
    }
    public record Fill(UUID id,UUID buy,UUID sell,UUID buyer,UUID seller,String product,int quantity,long unitCents,long at) {
        public Fill {Objects.requireNonNull(id);Objects.requireNonNull(buy);Objects.requireNonNull(sell);Objects.requireNonNull(buyer);Objects.requireNonNull(seller);LimitBook.product(product);LimitBook.quantity(quantity);money(unitCents);if(buy.equals(sell)||buyer.equals(seller)||unitCents>MAX_CENTS/quantity||at<1)throw invalid("Неверная сделка");}
    }
    public static final String EMPTY_ARCHIVE_HASH="0".repeat(64);
    /** Cumulative confirmed external claims. Its exact receipts remain in the hash-linked durable archive. */
    public record ClaimCheckpoint(long sequence,String hash,Map<UUID,Long> paidCash,Map<UUID,Map<String,Long>> paidItems) {
        public ClaimCheckpoint {
            if(sequence<0||hash==null||!hash.matches("[0-9a-f]{64}")||sequence==0&&!hash.equals(EMPTY_ARCHIVE_HASH)||sequence>0&&hash.equals(EMPTY_ARCHIVE_HASH))throw invalid("Неверная ссылка архива выдачи");
            paidCash=Map.copyOf(paidCash);var deep=new HashMap<UUID,Map<String,Long>>();paidItems.forEach((id,row)->deep.put(Objects.requireNonNull(id),Map.copyOf(row)));paidItems=Map.copyOf(deep);
            if(paidCash.size()>MAX_ORDERS||paidItems.size()>MAX_ORDERS||sequence==0&&(!paidCash.isEmpty()||!paidItems.isEmpty()))throw invalid("Неверные итоги архива выдачи");
            for(var value:paidCash.values())if(value<1||value>MAX_TOTAL_CENTS)throw invalid("Неверная сумма архива выдачи");
            for(var row:paidItems.values()){if(row.isEmpty()||row.size()>MAX_ORDERS)throw invalid("Неверный состав архива выдачи");for(var entry:row.entrySet()){LimitBook.product(entry.getKey());if(entry.getValue()<1||entry.getValue()>(long)MAX_ORDERS*MAX_QUANTITY)throw invalid("Неверное количество архива выдачи");}}
        }
        public static ClaimCheckpoint empty(){return new ClaimCheckpoint(0,EMPTY_ARCHIVE_HASH,Map.of(),Map.of());}
    }
    public record State(Map<UUID,Order> orders,Map<UUID,Receipt> receipts,Map<UUID,Long> wallets,Map<UUID,Map<String,Long>> items,List<Fill> fills,ClaimCheckpoint claimCheckpoint) {
        public State(Map<UUID,Order> orders,Map<UUID,Receipt> receipts,Map<UUID,Long> wallets,Map<UUID,Map<String,Long>> items,List<Fill> fills){this(orders,receipts,wallets,items,fills,ClaimCheckpoint.empty());}
        public State {
            orders=Map.copyOf(orders);receipts=Map.copyOf(receipts);wallets=Map.copyOf(wallets);var deep=new HashMap<UUID,Map<String,Long>>();items.forEach((id,balance)->deep.put(Objects.requireNonNull(id),Map.copyOf(balance)));items=Map.copyOf(deep);fills=List.copyOf(fills);
            Objects.requireNonNull(claimCheckpoint);validate(orders,receipts,wallets,items,fills,claimCheckpoint);
        }
        public long cash(UUID owner){return wallets.getOrDefault(owner,0L);}
        public long item(UUID owner,String product){return items.getOrDefault(owner,Map.of()).getOrDefault(product,0L);}
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException(reason);}
    private static void product(String value){if(value==null||!value.matches("[A-Z][A-Z0-9_]{0,63}"))throw invalid("Неверный вид товара");}
    private static void quantity(int value){if(value<1||value>MAX_QUANTITY)throw invalid("Количество: 1–3456");}
    private static void money(long value){if(value<1||value>MAX_CENTS)throw invalid("Сумма вне допустимого диапазона");}
    private static long add(long a,long b){return Math.addExact(a,b);}
    private static long total(Order order){return Math.multiplyExact(order.unitCents(),order.quantity());}
    private static long reserve(Order order){return Math.multiplyExact(order.unitCents(),order.remaining());}
    private static <K>void balance(Map<K,Long> values,K key,long delta){long next=add(values.getOrDefault(key,0L),delta);if(next<0)throw invalid("Недостаточно подтверждённого резерва");if(next==0)values.remove(key);else values.put(key,next);}
    private static void item(Map<UUID,Map<String,Long>> items,UUID owner,String product,long delta){var next=new HashMap<>(items.getOrDefault(owner,Map.of()));balance(next,product,delta);if(next.isEmpty())items.remove(owner);else items.put(owner,Map.copyOf(next));}
    private static Receipt outcome(Receipt r,boolean applied,long now){return new Receipt(r.id(),r.owner(),r.kind(),r.order(),r.product(),r.quantity(),r.cents(),r.created(),applied?ReceiptStatus.DONE:ReceiptStatus.FAILED,Math.max(now,r.created()));}
    private static Order remaining(Order o,int amount){return new Order(o.id(),o.owner(),o.side(),o.product(),o.quantity(),amount,o.unitCents(),o.created(),amount==0?Status.FILLED:Status.OPEN);}
    public static State empty(){return new State(Map.of(),Map.of(),Map.of(),Map.of(),List.of());}

    /** Persist returned state and await its acknowledgement before withdrawing money or taking items. */
    public static State prepareOrder(State state,UUID orderID,UUID receiptID,UUID owner,Side side,String product,int qty,long unitCents,long now){
        if(state.orders().containsKey(orderID)||state.receipts().containsKey(receiptID))throw invalid("Эта операция уже существует");
        if(state.orders().size()>=MAX_ORDERS||state.receipts().size()>=MAX_RECEIPTS)throw invalid("Журнал личного рынка заполнен; обратитесь к администрации");
        if(state.orders().values().stream().filter(o->o.owner().equals(owner)&&(o.status()==Status.OPEN||o.status()==Status.FUNDING)).count()>=MAX_ACTIVE)throw invalid("Доступно не более 32 действующих заявок");
        var order=new Order(orderID,owner,side,product,qty,qty,unitCents,now,Status.FUNDING);var receipt=new Receipt(receiptID,owner,side==Side.BUY?Kind.BUY_FUND:Kind.SELL_STOCK,orderID,product,qty,side==Side.BUY?total(order):0,now,ReceiptStatus.PENDING,0);
        var orders=new HashMap<>(state.orders());orders.put(orderID,order);var receipts=new HashMap<>(state.receipts());receipts.put(receiptID,receipt);return new State(orders,receipts,state.wallets(),state.items(),state.fills(),state.claimCheckpoint());
    }
    /** Resolve only known outcomes. An uncertain external operation must remain PENDING. */
    public static State resolve(State state,UUID receiptID,boolean applied,long now){
        var r=state.receipts().get(receiptID);if(r==null)throw invalid("Квитанция не найдена");var expected=applied?ReceiptStatus.DONE:ReceiptStatus.FAILED;
        if(r.status()!=ReceiptStatus.PENDING){if(r.status()!=expected)throw invalid("Квитанция уже сверена с другим результатом");return state;}
        var orders=new HashMap<>(state.orders());var receipts=new HashMap<>(state.receipts());var wallets=new HashMap<>(state.wallets());var items=new HashMap<>(state.items());receipts.put(r.id(),outcome(r,applied,now));
        switch(r.kind()){
            case BUY_FUND,SELL_STOCK -> {var o=orders.get(r.order());if(o==null||o.status()!=Status.FUNDING)throw invalid("Заявка не ожидает обеспечения");orders.put(o.id(),new Order(o.id(),o.owner(),o.side(),o.product(),o.quantity(),applied?o.quantity():0,o.unitCents(),o.created(),applied?Status.OPEN:Status.CANCELLED));}
            case CASH_CLAIM -> {if(!applied)balance(wallets,r.owner(),r.cents());}
            case ITEM_CLAIM -> {if(!applied)item(items,r.owner(),r.product(),r.quantity());}
        }
        return new State(orders,receipts,wallets,items,state.fills(),state.claimCheckpoint());
    }
    private static final Comparator<Order> TIME=Comparator.comparingLong(Order::created).thenComparing(Order::id);
    private static final Comparator<Order> BUY_PRIORITY=Comparator.comparing(Order::product).thenComparing(Comparator.comparingLong(Order::unitCents).reversed()).thenComparing(TIME);
    private static final Comparator<Order> SELL_PRIORITY=Comparator.comparingLong(Order::unitCents).thenComparing(TIME);
    /** One acknowledged image contains every matched escrow transfer. No external bank or inventory effect occurs here. */
    public static State match(State state,long now,int maxFills){
        if(now<1||maxFills<1||maxFills>100)throw invalid("Неверный предел сопоставления");
        var orders=new HashMap<>(state.orders());var wallets=new HashMap<>(state.wallets());var items=new HashMap<>(state.items());var fills=new ArrayList<>(state.fills());
        var buys=orders.values().stream().filter(o->o.side()==Side.BUY&&o.status()==Status.OPEN).sorted(BUY_PRIORITY).toList();var sells=new HashMap<String,NavigableSet<Order>>();orders.values().stream().filter(o->o.side()==Side.SELL&&o.status()==Status.OPEN).forEach(o->sells.computeIfAbsent(o.product(),k->new TreeSet<>(SELL_PRIORITY)).add(o));
        int added=0;outer:for(var original:buys){var buy=orders.get(original.id());if(buy.status()!=Status.OPEN)continue;var book=sells.get(buy.product());if(book==null)continue;while(buy.status()==Status.OPEN){
            if(added>=maxFills||fills.size()>=MAX_FILLS)break outer;
            // At most MAX_ACTIVE own offers can precede the first eligible counterparty.
            Order sell=null;for(var candidate:book){if(candidate.unitCents()>buy.unitCents())break;if(!buy.owner().equals(candidate.owner())){sell=candidate;break;}}if(sell==null)break;
            int quantity=Math.min(buy.remaining(),sell.remaining());long price=TIME.compare(buy,sell)<0?buy.unitCents():sell.unitCents();long paid=Math.multiplyExact(price,quantity),refund=Math.multiplyExact(buy.unitCents()-price,quantity);
            UUID fillID=UUID.nameUUIDFromBytes(("personal-market-fill:"+buy.id()+":"+buy.remaining()+":"+sell.id()+":"+sell.remaining()).getBytes(StandardCharsets.UTF_8));
            fills.add(new Fill(fillID,buy.id(),sell.id(),buy.owner(),sell.owner(),buy.product(),quantity,price,Math.max(now,Math.max(buy.created(),sell.created()))));
            balance(wallets,sell.owner(),paid);if(refund>0)balance(wallets,buy.owner(),refund);item(items,buy.owner(),buy.product(),quantity);
            book.remove(sell);var remainingSell=remaining(sell,sell.remaining()-quantity);if(remainingSell.status()==Status.OPEN)book.add(remainingSell);orders.put(sell.id(),remainingSell);
            buy=remaining(buy,buy.remaining()-quantity);orders.put(buy.id(),buy);added++;
        }}
        return added==0?state:new State(orders,state.receipts(),wallets,items,fills,state.claimCheckpoint());
    }
    public static State cancel(State state,UUID owner,UUID orderID,long now){
        var o=state.orders().get(orderID);if(o==null||!o.owner().equals(owner))throw invalid("Ваша заявка не найдена");if(o.status()==Status.CANCELLED)return state;if(o.status()!=Status.OPEN)throw invalid(o.status()==Status.FUNDING?"Обеспечение заявки требует сверки":"Заявка уже исполнена");if(now<1)throw invalid("Неверное время отмены");
        var orders=new HashMap<>(state.orders());orders.put(orderID,new Order(o.id(),o.owner(),o.side(),o.product(),o.quantity(),0,o.unitCents(),o.created(),Status.CANCELLED));var wallets=new HashMap<>(state.wallets());var items=new HashMap<>(state.items());
        if(o.side()==Side.BUY)balance(wallets,owner,reserve(o));else item(items,owner,o.product(),o.remaining());return new State(orders,state.receipts(),wallets,items,state.fills(),state.claimCheckpoint());
    }
    public static State claimCash(State state,UUID receiptID,UUID owner,long cents,long now){
        money(cents);claimAvailable(state,receiptID);var wallets=new HashMap<>(state.wallets());balance(wallets,owner,-cents);var receipts=new HashMap<>(state.receipts());receipts.put(receiptID,new Receipt(receiptID,owner,Kind.CASH_CLAIM,null,"",0,cents,now,ReceiptStatus.PENDING,0));return new State(state.orders(),receipts,wallets,state.items(),state.fills(),state.claimCheckpoint());
    }
    public static State claimItems(State state,UUID receiptID,UUID owner,String product,int qty,long now){
        LimitBook.product(product);LimitBook.quantity(qty);claimAvailable(state,receiptID);var items=new HashMap<>(state.items());item(items,owner,product,-qty);var receipts=new HashMap<>(state.receipts());receipts.put(receiptID,new Receipt(receiptID,owner,Kind.ITEM_CLAIM,null,product,qty,0,now,ReceiptStatus.PENDING,0));return new State(state.orders(),receipts,state.wallets(),items,state.fills(),state.claimCheckpoint());
    }
    private static void claimAvailable(State state,UUID receiptID){if(state.receipts().containsKey(receiptID))throw invalid("Выдача уже существует");if(state.receipts().size()>=MAX_RECEIPTS)throw invalid("Журнал выдачи заполнен; обратитесь к администрации");}

    /** Call only after the exact terminal receipts have been durably archived. Repository verifies the chain. */
    public static State checkpointClaims(State state,Collection<UUID> receiptIDs,long sequence,String archiveHash){
        if(sequence!=Math.addExact(state.claimCheckpoint().sequence(),1)||receiptIDs.isEmpty())throw invalid("Неверная последовательность архива выдачи");
        var receipts=new HashMap<>(state.receipts());var paidCash=new HashMap<>(state.claimCheckpoint().paidCash());var paidItems=new HashMap<>(state.claimCheckpoint().paidItems());var seen=new HashSet<UUID>();
        for(UUID id:receiptIDs){var r=receipts.get(id);if(!seen.add(id)||r==null||r.status()==ReceiptStatus.PENDING||r.kind()==Kind.BUY_FUND||r.kind()==Kind.SELL_STOCK)throw invalid("Архивировать можно только завершённые выдачи");
            if(r.status()==ReceiptStatus.DONE){if(r.kind()==Kind.CASH_CLAIM)balance(paidCash,r.owner(),r.cents());else item(paidItems,r.owner(),r.product(),r.quantity());}receipts.remove(id);
        }
        return new State(state.orders(),receipts,state.wallets(),state.items(),state.fills(),new ClaimCheckpoint(sequence,archiveHash,paidCash,paidItems));
    }

    /** Validation is repeated on deserialization: no open order, mailbox or wallet may invent backing. */
    private static void validate(Map<UUID,Order> orders,Map<UUID,Receipt> receipts,Map<UUID,Long> wallets,Map<UUID,Map<String,Long>> items,List<Fill> fills,ClaimCheckpoint checkpoint){
        if(orders.size()>MAX_ORDERS||receipts.size()>MAX_RECEIPTS||fills.size()>MAX_FILLS||wallets.size()>MAX_RECEIPTS||items.size()>MAX_RECEIPTS)throw invalid("Журнал превышает предел");
        long expectedCash=0,assetsCash=0;var expectedItems=new HashMap<String,Long>();var assetsItems=new HashMap<String,Long>();var funding=new HashMap<UUID,Receipt>();var filled=new HashMap<UUID,Long>();var ids=new HashSet<UUID>();
        for(long paid:checkpoint.paidCash().values())expectedCash=add(expectedCash,-paid);checkpoint.paidItems().values().forEach(row->row.forEach((key,n)->expectedItems.merge(key,-n,Math::addExact)));
        for(var e:orders.entrySet())if(!e.getKey().equals(e.getValue().id()))throw invalid("ID заявки не совпадает");
        for(var e:receipts.entrySet()){
            var r=e.getValue();if(!e.getKey().equals(r.id()))throw invalid("ID квитанции не совпадает");
            if(r.kind()==Kind.BUY_FUND||r.kind()==Kind.SELL_STOCK){var o=orders.get(r.order());if(o==null||!r.owner().equals(o.owner())||!r.product().equals(o.product())||r.quantity()!=o.quantity()||r.created()!=o.created()||r.kind()!=(o.side()==Side.BUY?Kind.BUY_FUND:Kind.SELL_STOCK)||r.cents()!=(o.side()==Side.BUY?total(o):0)||funding.put(o.id(),r)!=null)throw invalid("Квитанция не соответствует обеспечению заявки");}
            if(r.status()==ReceiptStatus.DONE)switch(r.kind()){
                case BUY_FUND -> expectedCash=add(expectedCash,r.cents());
                case SELL_STOCK -> expectedItems.merge(r.product(),(long)r.quantity(),Math::addExact);
                case CASH_CLAIM -> expectedCash=add(expectedCash,-r.cents());
                case ITEM_CLAIM -> expectedItems.merge(r.product(),-(long)r.quantity(),Math::addExact);
            }
            if(r.status()==ReceiptStatus.PENDING){if(r.kind()==Kind.CASH_CLAIM)assetsCash=add(assetsCash,r.cents());else if(r.kind()==Kind.ITEM_CLAIM)assetsItems.merge(r.product(),(long)r.quantity(),Math::addExact);}
        }
        for(var fill:fills){if(!ids.add(fill.id()))throw invalid("Повтор сделки");var b=orders.get(fill.buy());var s=orders.get(fill.sell());if(b==null||s==null||b.side()!=Side.BUY||s.side()!=Side.SELL||!b.owner().equals(fill.buyer())||!s.owner().equals(fill.seller())||!b.product().equals(s.product())||!b.product().equals(fill.product())||fill.unitCents()<s.unitCents()||fill.unitCents()>b.unitCents()||fill.at()<Math.max(b.created(),s.created()))throw invalid("Сделка не соответствует заявкам");long maker=TIME.compare(b,s)<0?b.unitCents():s.unitCents();if(fill.unitCents()!=maker)throw invalid("Неверная цена сделки");filled.merge(b.id(),(long)fill.quantity(),Math::addExact);filled.merge(s.id(),(long)fill.quantity(),Math::addExact);}
        var active=new HashMap<UUID,Integer>();for(var o:orders.values()){
            var r=funding.get(o.id());long count=filled.getOrDefault(o.id(),0L);if(r==null||count>o.quantity())throw invalid("Заявка не обеспечена или исполнена сверх количества");
            switch(o.status()){
                case FUNDING -> {if(r.status()!=ReceiptStatus.PENDING||count!=0)throw invalid("Неверный ожидающий резерв");}
                case OPEN -> {if(r.status()!=ReceiptStatus.DONE||o.remaining()!=o.quantity()-count)throw invalid("Неверный открытый резерв");if(o.side()==Side.BUY)assetsCash=add(assetsCash,reserve(o));else assetsItems.merge(o.product(),(long)o.remaining(),Math::addExact);}
                case FILLED -> {if(r.status()!=ReceiptStatus.DONE||count!=o.quantity())throw invalid("Неверная исполненная заявка");}
                case CANCELLED -> {if(r.status()==ReceiptStatus.PENDING||r.status()==ReceiptStatus.FAILED&&count!=0)throw invalid("Неверная отменённая заявка");}
            }
            if(o.status()==Status.FUNDING||o.status()==Status.OPEN)if(active.merge(o.owner(),1,Integer::sum)>MAX_ACTIVE)throw invalid("Слишком много заявок игрока");
        }
        for(var e:wallets.entrySet()){Objects.requireNonNull(e.getKey());if(e.getValue()<1||e.getValue()>MAX_TOTAL_CENTS)throw invalid("Неверный баланс выдачи");assetsCash=add(assetsCash,e.getValue());}
        for(var e:items.entrySet()){if(e.getValue().isEmpty()||e.getValue().size()>MAX_ORDERS)throw invalid("Неверный склад выдачи");for(var stock:e.getValue().entrySet()){product(stock.getKey());if(stock.getValue()<1||stock.getValue()>(long)MAX_ORDERS*MAX_QUANTITY)throw invalid("Неверный остаток товара");assetsItems.merge(stock.getKey(),stock.getValue(),Math::addExact);}}
        expectedItems.values().removeIf(n->n==0);assetsItems.values().removeIf(n->n==0);if(expectedCash<0||expectedCash!=assetsCash||!expectedItems.equals(assetsItems))throw invalid("Нарушено обеспечение личного рынка");
        ownership(orders,receipts,wallets,items,fills,checkpoint);
    }
    private static void flow(Map<UUID,Map<String,Long>> target,UUID owner,String product,long amount){target.computeIfAbsent(owner,k->new HashMap<>()).merge(product,amount,Math::addExact);}
    /** A conserved total alone is insufficient: money and goods must also belong to the correct player. */
    private static void ownership(Map<UUID,Order> orders,Map<UUID,Receipt> receipts,Map<UUID,Long> wallets,Map<UUID,Map<String,Long>> items,List<Fill> fills,ClaimCheckpoint checkpoint){
        var cash=new HashMap<UUID,Long>();var stock=new HashMap<UUID,Map<String,Long>>();
        checkpoint.paidCash().forEach((owner,n)->cash.put(owner,-n));checkpoint.paidItems().forEach((owner,row)->row.forEach((key,n)->flow(stock,owner,key,-n)));
        for(var r:receipts.values()){
            if(r.kind()==Kind.BUY_FUND&&r.status()==ReceiptStatus.DONE)cash.merge(r.owner(),r.cents(),Math::addExact);
            if(r.kind()==Kind.SELL_STOCK&&r.status()==ReceiptStatus.DONE)flow(stock,r.owner(),r.product(),r.quantity());
            if(r.kind()==Kind.CASH_CLAIM&&r.status()!=ReceiptStatus.FAILED)cash.merge(r.owner(),-r.cents(),Math::addExact);
            if(r.kind()==Kind.ITEM_CLAIM&&r.status()!=ReceiptStatus.FAILED)flow(stock,r.owner(),r.product(),-r.quantity());
        }
        for(var f:fills){long paid=Math.multiplyExact(f.unitCents(),f.quantity());cash.merge(f.buyer(),-paid,Math::addExact);cash.merge(f.seller(),paid,Math::addExact);flow(stock,f.seller(),f.product(),-f.quantity());flow(stock,f.buyer(),f.product(),f.quantity());}
        for(var o:orders.values())if(o.status()==Status.OPEN){if(o.side()==Side.BUY)cash.merge(o.owner(),-reserve(o),Math::addExact);else flow(stock,o.owner(),o.product(),-o.remaining());}
        cash.values().removeIf(n->n==0);stock.values().forEach(row->row.values().removeIf(n->n==0));stock.values().removeIf(Map::isEmpty);
        if(!cash.equals(wallets)||!stock.equals(items))throw invalid("Неверный получатель денег или товара");
    }
}
