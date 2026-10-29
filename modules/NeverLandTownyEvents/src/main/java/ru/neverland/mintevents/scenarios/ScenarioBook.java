package ru.neverland.mintevents.scenarios;

import java.util.*;

/** Immutable optional history. No resources, money, commands or ordinary event outcomes live here. */
public final class ScenarioBook {
    private ScenarioBook(){}
    public static final int MAX_HISTORY=5000,MAX_ACTIVE=128;
    public enum Phase { WARNING,DECISION,RESPONSE,RECOVERY,COMPLETED,CANCELLED }
    public enum SourceState { RUNNING,PAUSED,SUCCESS,FAILURE,MISSING }
    public record Mix(double crops,double output){
        public Mix{for(double v:new double[]{crops,output})if(!Double.isFinite(v)||v<.25||v>1.5)throw new IllegalArgumentException("Множитель последствий: 0.25–1.5");}
        public static Mix neutral(){return new Mix(1,1);}
    }
    public record Plan(String id,String name,String description,Mix response,Mix recovery){
        public Plan{key(id);text(name,80);text(description,300);Objects.requireNonNull(response);Objects.requireNonNull(recovery);}
    }
    public record Terms(String mode,String title,long warningMillis,int recoveryPercent,List<Plan> plans){
        public Terms{if(!Set.of("DROUGHT","FLOOD").contains(mode))throw new IllegalArgumentException("Сценарии поддерживают засуху и наводнение");text(title,100);if(warningMillis<1000||warningMillis>3_600_000||recoveryPercent<1||recoveryPercent>99)throw new IllegalArgumentException("Неверный срок предупреждения или этап восстановления");plans=List.copyOf(plans);if(plans.size()<2||plans.size()>3||plans.stream().map(Plan::id).distinct().count()!=plans.size())throw new IllegalArgumentException("Нужны два или три разных плана");}
        public Plan plan(String id){return plans.stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("План отсутствует в сохранённых условиях"));}
    }
    public record Scenario(UUID id,UUID town,String townName,String eventId,long started,Terms terms,Phase phase,long worked,
                           String choice,UUID actor,String actorName,long chosen,long ended,String outcome){
        public Scenario{
            Objects.requireNonNull(id);Objects.requireNonNull(town);text(townName,100);text(eventId,128);Objects.requireNonNull(terms);Objects.requireNonNull(phase);
            if(started<1||worked<0||worked>terms.warningMillis()||chosen<0||ended<0)throw new IllegalArgumentException("Неверное время сценария");
            boolean selected=!choice.isEmpty();if(selected){terms.plan(choice);if(actor==null||chosen<1)throw new IllegalArgumentException("Нет автора решения");text(actorName,80);}else if(actor!=null||chosen!=0||!actorName.isEmpty())throw new IllegalArgumentException("Автор без решения");
            boolean terminal=phase==Phase.COMPLETED||phase==Phase.CANCELLED;
            if(terminal!=(ended>0)||!Set.of("","SUCCESS","FAILURE","CANCELLED","UNAVAILABLE").contains(outcome)||terminal==outcome.isEmpty())throw new IllegalArgumentException("Неверный итог сценария");
            if(phase==Phase.COMPLETED&&!Set.of("SUCCESS","FAILURE").contains(outcome)||phase==Phase.CANCELLED&&!Set.of("CANCELLED","UNAVAILABLE").contains(outcome)||selected&&worked!=terms.warningMillis())throw new IllegalArgumentException("Итог или решение не соответствует сохранённому этапу");
            if((phase==Phase.WARNING||phase==Phase.DECISION)&&selected||(phase==Phase.RESPONSE||phase==Phase.RECOVERY)&&!selected)throw new IllegalArgumentException("Решение не соответствует этапу");
            if(phase==Phase.WARNING&&worked>=terms.warningMillis()||phase==Phase.DECISION&&worked<terms.warningMillis())throw new IllegalArgumentException("Время предупреждения не соответствует этапу");
        }
        public boolean active(){return phase!=Phase.COMPLETED&&phase!=Phase.CANCELLED;}
        public Mix effects(){if(phase==Phase.RESPONSE)return terms.plan(choice).response();if(phase==Phase.RECOVERY)return terms.plan(choice).recovery();return Mix.neutral();}
    }
    public record Source(SourceState state,int progress,int goal){public Source{Objects.requireNonNull(state);if(progress<0||goal<1)throw new IllegalArgumentException("Неверный прогресс события");}}
    public record State(Map<UUID,Scenario> entries){
        public State{entries=Map.copyOf(entries);if(entries.size()>MAX_HISTORY||entries.values().stream().filter(Scenario::active).count()>MAX_ACTIVE)throw new IllegalArgumentException("Журнал сценариев заполнен");for(var e:entries.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("Неверный ID сценария");}
        public List<Scenario> active(){return entries.values().stream().filter(Scenario::active).toList();}
        public State put(Scenario scenario){var next=new HashMap<>(entries);next.put(scenario.id(),scenario);return new State(next);}
    }
    public static State empty(){return new State(Map.of());}
    public static Scenario fresh(UUID instance,UUID town,String townName,String eventId,long started,Terms terms){return new Scenario(instance,town,townName,eventId,started,terms,Phase.WARNING,0,"",null,"",0,0,"");}
    public static State begin(State state,Scenario scenario){
        if(state.entries().containsKey(scenario.id())){if(!state.entries().get(scenario.id()).town().equals(scenario.town()))throw new IllegalArgumentException("ID занят другим городом");return state;}
        if(state.entries().size()>=MAX_HISTORY)throw new IllegalArgumentException("Журнал сценариев заполнен");return state.put(scenario);
    }
    public static State choose(State state,UUID id,String plan,UUID actor,String actorName,long now){
        if(state.entries().size()>=MAX_HISTORY)throw new IllegalArgumentException("Журнал сценариев заполнен; новые решения приостановлены");var r=require(state,id);if(r.phase()!=Phase.DECISION)throw new IllegalArgumentException("Решение уже принято или этап ещё не наступил");r.terms().plan(plan);
        return state.put(new Scenario(r.id(),r.town(),r.townName(),r.eventId(),r.started(),r.terms(),Phase.RESPONSE,r.worked(),plan,actor,actorName,now,0,""));
    }
    public static State advance(State state,long elapsed,Map<UUID,Source> observations,long now){
        if(elapsed<0||elapsed>10000||now<1)throw new IllegalArgumentException("Неверный шаг сценария");var result=state;
        for(var r:state.active()){
            var source=observations.get(r.id());if(source==null||source.state()==SourceState.PAUSED)continue;
            if(source.state()==SourceState.MISSING){result=result.put(end(r,Phase.CANCELLED,"UNAVAILABLE",now));continue;}
            if(source.state()==SourceState.SUCCESS||source.state()==SourceState.FAILURE){result=result.put(end(r,Phase.COMPLETED,source.state().name(),now));continue;}
            long worked=r.worked();Phase phase=r.phase();if(phase==Phase.WARNING){worked=Math.min(r.terms().warningMillis(),worked+elapsed);if(worked>=r.terms().warningMillis())phase=Phase.DECISION;}
            if(phase==Phase.RESPONSE&&(long)source.progress()*100>=((long)source.goal())*r.terms().recoveryPercent())phase=Phase.RECOVERY;
            if(phase!=r.phase()||worked!=r.worked())result=result.put(new Scenario(r.id(),r.town(),r.townName(),r.eventId(),r.started(),r.terms(),phase,worked,r.choice(),r.actor(),r.actorName(),r.chosen(),0,""));
        }
        return result;
    }
    public static State cancel(State state,UUID id,long now){var r=require(state,id);if(!r.active())throw new IllegalArgumentException("Сценарий уже закрыт");return state.put(end(r,Phase.CANCELLED,"CANCELLED",now));}
    private static Scenario end(Scenario r,Phase phase,String result,long now){return new Scenario(r.id(),r.town(),r.townName(),r.eventId(),r.started(),r.terms(),phase,r.worked(),r.choice(),r.actor(),r.actorName(),r.chosen(),now,result);}
    public static Scenario require(State state,UUID id){var r=state.entries().get(id);if(r==null)throw new IllegalArgumentException("Сценарий не найден");return r;}
    public static double loss(double base,double factor){if(!Double.isFinite(base)||base<0||base>1||!Double.isFinite(factor)||factor<.25||factor>1.5)throw new IllegalArgumentException("Неверная сила последствия");return Math.max(0,Math.min(.9,base*factor));}
    private static void key(String value){if(value==null||!value.matches("[a-z0-9_-]{1,80}"))throw new IllegalArgumentException("Неверный ключ сценария");}
    private static void text(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("Неверное описание сценария");}
}
