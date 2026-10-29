package ru.neverland.townymarket;
import java.util.*;
import java.math.*;
public final class MarketData {
    public static final long MAX=100_000_000_000L,DAY=86_400_000L;
    public enum Scope { LOCAL, GLOBAL }
    public enum ListingState { PREPARING, ACTIVE, CLOSING, CLOSED }
    public enum Phase { PREPARED, DEBIT_READY, DEBIT_PENDING, PAID, DELIVERED, CREDIT_PENDING, RETURNING, COMPLETE, CANCELLED }
    public record Listing(UUID id,UUID town,String item,String label,String product,Scope scope,boolean auto,long base,int amount,long created,ListingState state,String note){
        public Listing {if(id==null||town==null||item==null||item.isBlank()||label==null||label.isBlank()||product==null||product.isBlank()||scope==null||base<1||base>MAX||amount<1||amount>3456||created<0||state==null||note==null)throw new IllegalArgumentException("Некорректное предложение");}
        public Listing state(ListingState s,String n){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return new Listing(id,town,item,label,product,scope,auto,base,amount,created,s,n);} }
        public String shortId(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return id.toString().substring(0,8);} }
        public String scopeKey(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return scope==Scope.GLOBAL?"global":"local_"+town;} }
    }
    public record Order(UUID id,UUID lot,UUID seller,UUID buyer,UUID actor,boolean city,int amount,long unit,long created,Phase phase,long check,String note,boolean finalized,long revision,long phaseAt) implements ru.neverland.core.PurchaseSaga.Purchase<Order> {
        public Order(UUID id,UUID lot,UUID seller,UUID buyer,UUID actor,boolean city,int amount,long unit,long created,Phase phase,long check,String note,boolean finalized){this(id,lot,seller,buyer,actor,city,amount,unit,created,phase,check,note,finalized,0,created);}
        public String auditStep(){return phase.name()+":"+revision;}
        public String auditOutcome(){return switch(phase){case DEBIT_PENDING->"REVIEW_DEBIT";case CREDIT_PENDING->"REVIEW_CREDIT";case DEBIT_READY,PAID,DELIVERED,RETURNING->"PENDING";case PREPARED->"PREPARED";case COMPLETE->"COMPLETED";case CANCELLED->"CANCELLED";};}
        public String paymentStep(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return phase.name();} }
        public Order paymentStep(String step,long at,String note){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return phase(Phase.valueOf(step),at,note);} }
        public Order {if(id==null||lot==null||seller==null||buyer==null||actor==null||city&&seller.equals(buyer)||!city&&!buyer.equals(actor)||amount<1||amount>3456||unit<1||unit>MAX/amount||created<0||phase==null||check<0||note==null||revision<0||phaseAt<created||finalized&&phase!=Phase.COMPLETE&&phase!=Phase.CANCELLED)throw new IllegalArgumentException("Некорректная покупка");}
        public long total(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return unit*amount;} }
        public Order phase(Phase s,long at,String n){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return new Order(id,lot,seller,buyer,actor,city,amount,unit,created,s,at,noteText(n),false,s==phase?revision:Math.addExact(revision,1),s==phase?phaseAt:Math.max(System.currentTimeMillis(),Math.addExact(phaseAt,1)));} }
        private static String noteText(String n){return n==null?"":n;}
        public Order finish(){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return new Order(id,lot,seller,buyer,actor,city,amount,unit,created,phase,check,note,true,revision,phaseAt);} }
        public boolean terminal(){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {return phase==Phase.COMPLETE||phase==Phase.CANCELLED;} }
        public String shortId(){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {return id.toString().substring(0,8);} }
    }
    public record Demand(UUID order,String scope,String product,UUID buyer,long at,int amount){public Demand{if(order==null||scope==null||product==null||buyer==null||at<0||amount<1||amount>3456)throw new IllegalArgumentException("Некорректный спрос");}}
    public record Quote(UUID token,UUID actor,UUID town,UUID lot,int amount,long unit,long expires){}
    public static long cents(String value){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {try{long n=new BigDecimal(value.replace(',','.')).movePointRight(2).longValueExact();if(n<1||n>MAX)throw new IllegalArgumentException();return n;}catch(RuntimeException ex){throw new IllegalArgumentException("Цена: 0.01–1 000 000 000, не больше двух знаков после запятой");}} }
    public static String money(long n){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {return BigDecimal.valueOf(n,2).toPlainString();} }
}
