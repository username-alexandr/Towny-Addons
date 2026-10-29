package ru.neverland.townyideologies.reforms;

import java.util.*;
import ru.neverland.townyideologies.model.TownIdeology;

/** Immutable ledger: a paid target becomes active in the very same acknowledged state as its receipt. */
public final class ReformBook {
    private ReformBook(){}
    public static final int MAX_HISTORY=10000;
    public enum Phase { RUNNING, READY, PAYMENT_PENDING, COMPLETED, CANCELLED }
    public record Attempt(UUID id, UUID actor, String result, long at) {
        public Attempt {if(id==null||actor==null||!Set.of("PENDING","PAID","NOT_APPLIED").contains(result)||at<1)throw new IllegalArgumentException("Неверная квитанция реформы");}
    }
    public record Reform(UUID id,UUID actor,TownIdeology previous,TownIdeology target,long cents,List<Long> stages,long worked,Phase phase,boolean paused,long created,long finished,List<Attempt> attempts) {
        public Reform {
            stages=List.copyOf(stages);attempts=List.copyOf(attempts);
            if(id==null||actor==null||target==null||target.terms()==null||phase==null||previous!=null&&!previous.townId().equals(target.townId())||cents<0||cents>100_000_000_000L||stages.size()!=3||stages.stream().anyMatch(n->n<1000||n>30L*86400000)||worked<0||worked>total(stages)||created<1||finished<0||attempts.size()>1000)throw new IllegalArgumentException("Неверное состояние реформы");
            if((phase==Phase.RUNNING)!=(worked<total(stages))&&phase!=Phase.CANCELLED)throw new IllegalArgumentException("Этап реформы не соответствует прогрессу");
            if((finished>0)!=(phase==Phase.COMPLETED||phase==Phase.CANCELLED))throw new IllegalArgumentException("Неверное завершение реформы");
            var ids=new HashSet<UUID>();int pending=0,paid=0;for(var attempt:attempts){if(!ids.add(attempt.id()))throw new IllegalArgumentException("Повтор квитанции реформы");if(attempt.result().equals("PENDING"))pending++;if(attempt.result().equals("PAID"))paid++;}
            if(pending!=(phase==Phase.PAYMENT_PENDING?1:0)||paid>(cents==0?0:1)||paid!=(phase==Phase.COMPLETED&&cents>0?1:0))throw new IllegalArgumentException("Квитанция не соответствует реформе");
        }
        public UUID town(){return target.townId();}
        public boolean active(){return phase!=Phase.COMPLETED&&phase!=Phase.CANCELLED;}
        public long total(){return total(stages);}
        public int stage(){long sum=0;for(int i=0;i<3;i++){sum+=stages.get(i);if(worked<sum)return i;}return 3;}
        public static long total(List<Long> stages){long result=0;for(long n:stages)result=Math.addExact(result,n);return result;}
    }
    public record State(Map<UUID,TownIdeology> towns,Map<UUID,Reform> reforms) {
        public State {
            towns=Map.copyOf(towns);reforms=Map.copyOf(reforms);if(towns.size()>10000||reforms.size()>MAX_HISTORY)throw new IllegalArgumentException("Журнал реформ заполнен");
            var active=new HashSet<UUID>();var receipts=new HashSet<UUID>();
            for(var e:towns.entrySet())if(!e.getKey().equals(e.getValue().townId()))throw new IllegalArgumentException("Город идеологии не совпадает");
            for(var e:reforms.entrySet()){var r=e.getValue();if(!e.getKey().equals(r.id()))throw new IllegalArgumentException("ID реформы не совпадает");if(r.active()&&(!active.add(r.town())||!Objects.equals(towns.get(r.town()),r.previous())))throw new IllegalArgumentException("Параллельная реформа или изменённые прежние условия");for(var a:r.attempts())if(!receipts.add(a.id()))throw new IllegalArgumentException("Повтор платёжной квитанции");}
        }
        public Optional<Reform> active(UUID town){return reforms.values().stream().filter(r->r.town().equals(town)&&r.active()).findFirst();}
        public State put(Reform reform){var next=new HashMap<>(reforms);next.put(reform.id(),reform);return new State(towns,next);}
    }
    public static State empty(){return new State(Map.of(),Map.of());}
    public static State begin(State state,Reform reform){
        if(state.reforms().containsKey(reform.id())||state.active(reform.town()).isPresent()||reform.phase()!=Phase.RUNNING||reform.worked()!=0||!reform.attempts().isEmpty()||!Objects.equals(state.towns().get(reform.town()),reform.previous()))throw new IllegalArgumentException("Реформа уже идёт или условия изменились");return state.put(reform);
    }
    public static State advance(State state,long elapsed){
        if(elapsed<0||elapsed>10000)throw new IllegalArgumentException("Неверный шаг времени реформы");var next=new HashMap<>(state.reforms());
        for(var r:state.reforms().values())if(r.phase()==Phase.RUNNING&&!r.paused()){
            long worked=Math.min(r.total(),r.worked()+elapsed);next.put(r.id(),copy(r,worked,worked==r.total()?Phase.READY:Phase.RUNNING,r.paused(),0,r.attempts()));
        }return new State(state.towns(),next);
    }
    public static State pause(State state,UUID id,boolean paused){var r=require(state,id);if(!r.active()||r.phase()==Phase.PAYMENT_PENDING)throw new IllegalArgumentException("Платёж сначала требует сверки");return state.put(copy(r,r.worked(),r.phase(),paused,0,r.attempts()));}
    public static State cancel(State state,UUID id,long now){var r=require(state,id);if(!r.active()||r.phase()==Phase.PAYMENT_PENDING)throw new IllegalArgumentException("Нельзя отменить закрытую реформу или неизвестный платёж");return state.put(copy(r,r.worked(),Phase.CANCELLED,false,now,r.attempts()));}
    public static State preparePayment(State state,UUID id,UUID receipt,UUID actor,long now){var r=require(state,id);if(r.phase()!=Phase.READY||r.paused()||r.cents()==0)throw new IllegalArgumentException("Реформа ещё не готова к оплате");var attempts=new ArrayList<>(r.attempts());attempts.add(new Attempt(receipt,actor,"PENDING",now));return state.put(copy(r,r.worked(),Phase.PAYMENT_PENDING,false,0,attempts));}
    public static State resolve(State state,UUID id,boolean paid,long now){var r=require(state,id);if(r.phase()!=Phase.PAYMENT_PENDING)throw new IllegalArgumentException("Нет платежа для сверки");var attempts=new ArrayList<>(r.attempts());int at=attempts.size()-1;var prior=attempts.get(at);if(!prior.result().equals("PENDING"))throw new IllegalArgumentException("Последняя квитанция не ожидает сверки");attempts.set(at,new Attempt(prior.id(),prior.actor(),paid?"PAID":"NOT_APPLIED",now));return finish(state,r,paid,now,attempts);}
    public static State free(State state,UUID id,long now){var r=require(state,id);if(r.cents()!=0||r.phase()!=Phase.READY||r.paused())throw new IllegalArgumentException("Нет готовой бесплатной реформы");return finish(state,r,true,now,r.attempts());}
    private static State finish(State state,Reform r,boolean paid,long now,List<Attempt> attempts){var reforms=new HashMap<>(state.reforms());reforms.put(r.id(),copy(r,r.worked(),paid?Phase.COMPLETED:Phase.READY,false,paid?now:0,attempts));var towns=new HashMap<>(state.towns());if(paid)towns.put(r.town(),r.target());return new State(towns,reforms);}
    public static State set(State state,UUID town,TownIdeology value){if(state.active(town).isPresent())throw new IllegalArgumentException("Сначала отмените или завершите текущую реформу");var next=new HashMap<>(state.towns());if(value==null)next.remove(town);else next.put(town,value);return new State(next,state.reforms());}
    public static Reform require(State state,UUID id){var r=state.reforms().get(id);if(r==null)throw new IllegalArgumentException("Реформа не найдена");return r;}
    private static Reform copy(Reform r,long worked,Phase phase,boolean paused,long finished,List<Attempt> attempts){return new Reform(r.id(),r.actor(),r.previous(),r.target(),r.cents(),r.stages(),worked,phase,paused,r.created(),finished,attempts);}
}
