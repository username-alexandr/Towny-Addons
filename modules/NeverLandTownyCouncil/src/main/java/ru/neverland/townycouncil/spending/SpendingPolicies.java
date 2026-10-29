package ru.neverland.townycouncil.spending;

import java.math.BigDecimal;
import java.util.*;

/** A limit is a restriction, never an authorization to perform a city action. */
public final class SpendingPolicies {
    private SpendingPolicies(){}
    public static final long MAX_CENTS=100_000_000_000L;
    public static final int MAX_POLICIES=10_000,MAX_HISTORY=20_000;
    public static final UUID SYSTEM=new UUID(0,0);
    public record Limits(long operation,long day){public Limits{money(operation);money(day);}public static Limits unlimited(){return new Limits(0,0);}}
    public record Policy(UUID town,UUID resident,Limits limits,UUID mayor,String actorName,long changedAt,long revision){
        public Policy{Objects.requireNonNull(town);Objects.requireNonNull(resident);Objects.requireNonNull(limits);Objects.requireNonNull(mayor);text(actorName,64);if(resident.equals(SYSTEM)||mayor.equals(SYSTEM)||changedAt<0||revision<1)throw new IllegalArgumentException("Неверное правило расходов");}
        public String key(){return town+"/"+resident;}
    }
    public record Entry(UUID id,Policy before,Policy after,String townName,String residentName,String reason,boolean audited){
        public Entry{Objects.requireNonNull(id);Objects.requireNonNull(after);text(townName,100);text(residentName,64);text(reason,200);if(before!=null&&(!before.key().equals(after.key())||before.revision()>=after.revision()||before.changedAt()>after.changedAt()||before.limits().equals(after.limits())))throw new IllegalArgumentException("Неверная история правила жителя");}
        public Entry ack(){return new Entry(id,before,after,townName,residentName,reason,true);}
    }
    public record State(Map<String,Policy> policies,List<Entry> history,long revision){
        public State{policies=Map.copyOf(policies);history=List.copyOf(history);if(revision<0||policies.size()>MAX_POLICIES||history.size()>MAX_HISTORY||revision!=history.size())throw new IllegalArgumentException("Архив ограничений расходов повреждён или достиг предела");var replay=new HashMap<String,Policy>();var ids=new HashSet<UUID>();long sequence=0,at=0;for(var e:history){var p=e.after();if(!ids.add(e.id())||p.revision()!=++sequence||p.changedAt()<at||!Objects.equals(replay.get(p.key()),e.before()))throw new IllegalArgumentException("Нарушена последовательность правил расходов");replay.put(p.key(),p);at=p.changedAt();}if(!replay.equals(policies))throw new IllegalArgumentException("Правила расходов не соответствуют подтверждённой истории");}
        public static State empty(){return new State(Map.of(),List.of(),0);}
        public Limits limits(UUID town,UUID resident){var p=policies.get(town+"/"+resident);return p==null?Limits.unlimited():p.limits();}
    }
    public record Quote(UUID id,UUID town,UUID resident,UUID mayor,String actorName,String townName,String residentName,Limits limits,long expectedRevision,long createdAt,String reason){
        public Quote{Objects.requireNonNull(id);Objects.requireNonNull(town);Objects.requireNonNull(resident);Objects.requireNonNull(mayor);Objects.requireNonNull(limits);text(actorName,64);text(townName,100);text(residentName,64);text(reason,200);if(resident.equals(SYSTEM)||mayor.equals(SYSTEM)||createdAt<0||expectedRevision<0)throw new IllegalArgumentException("Неверное подтверждение ограничения");}
    }
    public static State apply(State before,Quote quote,long now){
        if(before.revision()!=quote.expectedRevision())throw new IllegalArgumentException("Ограничения изменились; откройте подтверждение заново");
        if(now<quote.createdAt()||now-quote.createdAt()>60_000)throw new IllegalArgumentException("Подтверждение устарело");
        if(before.history().size()>=MAX_HISTORY)throw new IllegalArgumentException("Журнал ограничений заполнен; обратитесь к администратору");
        String key=quote.town()+"/"+quote.resident();var old=before.policies().get(key);if(old!=null&&old.limits().equals(quote.limits())||old==null&&quote.limits().equals(Limits.unlimited()))throw new IllegalArgumentException("Такие ограничения уже действуют");
        if(old==null&&before.policies().size()>=MAX_POLICIES)throw new IllegalArgumentException("Реестр ограничений заполнен");
        long at=before.history().isEmpty()?now:Math.max(now,before.history().get(before.history().size()-1).after().changedAt());
        var policy=new Policy(quote.town(),quote.resident(),quote.limits(),quote.mayor(),quote.actorName(),at,Math.addExact(before.revision(),1));var rows=new HashMap<>(before.policies());rows.put(key,policy);var history=new ArrayList<>(before.history());history.add(new Entry(quote.id(),old,policy,quote.townName(),quote.residentName(),quote.reason(),false));return new State(rows,history,policy.revision());
    }
    public static State acknowledged(State state,Set<UUID> ids){if(ids.size()>64)throw new IllegalArgumentException("Слишком большая пачка журнала");return new State(state.policies(),state.history().stream().map(e->ids.contains(e.id())?e.ack():e).toList(),state.revision());}
    public static long parse(String value){try{long n=new BigDecimal(value.replace(',','.')).movePointRight(2).longValueExact();money(n);return n;}catch(RuntimeException e){throw new IllegalArgumentException("Укажите сумму от 0 до 1 000 000 000 с точностью до копеек; 0 — без ограничения");}}
    public static String amount(long cents){money(cents);return BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString();}
    public static String label(long cents){return cents==0?"Без ограничения":amount(cents)+" монет";}
    private static void money(long n){if(n<0||n>MAX_CENTS)throw new IllegalArgumentException("Недопустимая сумма ограничения");}
    private static void text(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(c->c<' '))throw new IllegalArgumentException("Неверное описание ограничения расходов");}
}
