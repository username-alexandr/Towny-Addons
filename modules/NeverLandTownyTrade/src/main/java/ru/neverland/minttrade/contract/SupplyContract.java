package ru.neverland.minttrade.contract;
import java.util.*;
import java.math.BigDecimal;
/** Immutable signed terms; all amounts are whole items and integer kopecks. */
public record SupplyContract(Terms terms,Status status,Set<UUID> pausedBy,long nextDue,long nextCheck,long deliveries,
                             List<Receipt> history,String note,Attempt attempt) {
    public static final long DAY=86_400_000L;
    public enum Status { PROPOSED, ACTIVE, CANCELLED, EXPIRED }
    public enum Phase { PREPARED, DEBIT_READY, DEBIT_PENDING, PAID, DELIVERED, CREDIT_PENDING, RETURNING, COMPLETE, RETURNED }
    public record Terms(UUID id,UUID seller,UUID buyer,String itemData,String itemName,int amount,long cents,int days,long created,long expires) {
        public Terms {
            if(id==null||seller==null||buyer==null||seller.equals(buyer)||itemData==null||itemData.isBlank()||itemName==null||itemName.isBlank()
                ||amount<1||amount>3456||cents<1||cents>100_000_000_000L||days<1||days>30||created<0||expires<=created)throw new IllegalArgumentException("Некорректные условия договора");
        }
        public boolean party(UUID town){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return seller.equals(town)||buyer.equals(town);} }
        public String shortId(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return id.toString().substring(0,8);} }
    }
    public record Attempt(UUID id,Phase phase,long started,long revision,long transitionedAt){
        public Attempt(UUID id,Phase phase,long started){this(id,phase,started,0,started);}
        public Attempt{if(id==null||phase==null||started<0||revision<0||transitionedAt<started)throw new IllegalArgumentException("Повреждена попытка поставки");}
        public Attempt phase(Phase p){try(var guard=ru.neverland.core.SuiteState.guard()){return phase==p?this:new Attempt(id,p,started,Math.addExact(revision,1),Math.max(System.currentTimeMillis(),Math.addExact(transitionedAt,1)));}}
        public String auditStep(){return phase.name()+":"+revision;}
        public String auditOutcome(){return switch(phase){case DEBIT_PENDING->"REVIEW_DEBIT";case CREDIT_PENDING->"REVIEW_CREDIT";case DEBIT_READY,PAID,DELIVERED,RETURNING->"PENDING";case PREPARED->"PREPARED";case COMPLETE->"COMPLETED";case RETURNED->"RETURNED";};}
    }
    public record Receipt(UUID id,long at){public Receipt{if(id==null||at<0)throw new IllegalArgumentException("Повреждена квитанция");}}
    public SupplyContract {
        Objects.requireNonNull(terms);Objects.requireNonNull(status);pausedBy=Set.copyOf(pausedBy);history=List.copyOf(history);
        if(nextDue<0||nextCheck<0||deliveries<0||note==null||history.size()>20||history.size()>deliveries||pausedBy.stream().anyMatch(x->!terms.party(x))
            ||(status==Status.PROPOSED||status==Status.EXPIRED)&&(attempt!=null||deliveries!=0||nextDue!=0))throw new IllegalArgumentException("Повреждено состояние договора");
        if(new HashSet<>(history.stream().map(Receipt::id).toList()).size()!=history.size())throw new IllegalArgumentException("Повторная квитанция");
    }
    public static SupplyContract proposal(Terms terms){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return new SupplyContract(terms,Status.PROPOSED,Set.of(),0,0,0,List.of(),"Ожидает согласия покупателя",null);} }
    public SupplyContract accept(UUID buyer,long now){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        if(status!=Status.PROPOSED||!terms.buyer.equals(buyer)||now>=terms.expires)throw new IllegalArgumentException("Предложение недоступно для принятия");
        long due=next(now,terms.days);return new SupplyContract(terms,Status.ACTIVE,Set.of(),due,due,0,history,"Подписан обеими сторонами",null);
    } }
    public SupplyContract cancel(UUID party){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {if(!terms.party(party))throw new IllegalArgumentException("Вы не сторона договора");return new SupplyContract(terms,Status.CANCELLED,pausedBy,nextDue,nextCheck,deliveries,history,"Отменён; начатая оплата завершается",attempt);} }
    public SupplyContract expire(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return new SupplyContract(terms,Status.EXPIRED,pausedBy,0,0,0,history,"Срок предложения истёк",null);} }
    public SupplyContract pause(UUID party,boolean pause){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        if(status!=Status.ACTIVE||!terms.party(party))throw new IllegalArgumentException("Договор недоступен");
        Set<UUID> next=new HashSet<>(pausedBy);if(pause)next.add(party);else next.remove(party);
        return new SupplyContract(terms,status,next,nextDue,0,deliveries,history,next.isEmpty()?"Договор возобновлён":"Пауза: начатая оплата завершается",attempt);
    } }
    public SupplyContract attempt(Attempt value,long check,String message){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {return new SupplyContract(terms,status,pausedBy,nextDue,check,deliveries,history,message,value);} }
    public SupplyContract waiting(long check,String message){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {return attempt(attempt,check,message);} }
    public SupplyContract finish(long now,long retry){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        if(attempt==null||attempt.phase!=Phase.COMPLETE&&attempt.phase!=Phase.RETURNED)throw new IllegalStateException("Поставка не завершена");
        boolean done=attempt.phase==Phase.COMPLETE;var receipts=new ArrayList<>(history);
        if(done){receipts.add(0,new Receipt(attempt.id,now));if(receipts.size()>20)receipts.remove(receipts.size()-1);}
        return new SupplyContract(terms,status,pausedBy,done?next(now,terms.days):nextDue,done?next(now,terms.days):Math.addExact(now,retry),
            done?Math.addExact(deliveries,1):deliveries,receipts,done?"Поставка оплачена и доставлена":"Резерв возвращён. "+note,null);
    } }
    public boolean enabled(){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {return status==Status.ACTIVE&&pausedBy.isEmpty();} }
    public boolean open(){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {return status==Status.PROPOSED||status==Status.ACTIVE||attempt!=null;} }
    public static long next(long now,int days){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {return Math.addExact(now,Math.multiplyExact(DAY,days));} }
    public static long price(String text){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {try{long n=new BigDecimal(text.replace(',','.')).movePointRight(2).longValueExact();if(n<1||n>100_000_000_000L)throw new IllegalArgumentException();return n;}catch(RuntimeException ex){throw new IllegalArgumentException("Цена: от 0.01 до 1 000 000 000, не более двух знаков после запятой");}} }
    public static String money(long cents){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {return BigDecimal.valueOf(cents,2).toPlainString();} }
}
