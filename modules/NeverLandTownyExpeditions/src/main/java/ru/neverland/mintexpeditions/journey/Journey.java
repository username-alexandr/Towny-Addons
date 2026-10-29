package ru.neverland.mintexpeditions.journey;

import java.util.*;
import ru.neverland.mintexpeditions.model.*;

/** Frozen opt-in rules. Missing journey markers in old expeditions keep their original rules. */
public record Journey(UUID id, ExpeditionDefinition frozen, Branch branch, Set<UUID> participants,
                      Map<UUID,Role> roles, Map<String,Contribution> contributions, State state, String note) {
    public enum Branch { SCOUT_FIRST, FIGHT_FIRST;
        public String title(){return this==SCOUT_FIRST?"Разведка → зачистка":"Зачистка → поиск";}
    }
    public enum Role { EXPLORER, GUARD;
        public String title(){return this==EXPLORER?"Разведчик":"Защитник";}
    }
    public enum State { ACTIVE, REVIEW, CLOSED }
    public enum Action { OBJECTIVE, KILL }
    public record Contribution(String key,Action action,UUID player,Role role,long at){
        public Contribution{Objects.requireNonNull(key);Objects.requireNonNull(action);Objects.requireNonNull(player);if(at<=0||key.length()>100)throw new IllegalArgumentException("Неверное подтверждение вклада");}
    }
    public Journey {
        Objects.requireNonNull(id);Objects.requireNonNull(branch);Objects.requireNonNull(state);Objects.requireNonNull(note);
        if(state==State.CLOSED&&!Set.of("COMPLETED","FAILED","CANCELLED").contains(note))throw new IllegalArgumentException("Неизвестный итог похода");
        frozen=freeze(frozen);participants=Set.copyOf(participants);roles=Map.copyOf(roles);contributions=Map.copyOf(contributions);
        if(participants.isEmpty()||participants.size()>64||!participants.containsAll(roles.keySet())||contributions.size()>512||note.length()>512)throw new IllegalArgumentException("Превышены границы похода");
        for(var entry:contributions.entrySet())if(!entry.getKey().equals(entry.getValue().key())||!participants.contains(entry.getValue().player()))throw new IllegalArgumentException("Вклад другого похода");
        if(count(contributions,Action.OBJECTIVE)>frozen.goal()||count(contributions,Action.KILL)>frozen.mobCount())throw new IllegalArgumentException("Вклад выше цели");
    }
    @Override public ExpeditionDefinition frozen(){return freeze(frozen);}
    public static ExpeditionDefinition freeze(ExpeditionDefinition d){
        Objects.requireNonNull(d);if(d.goal()<1||d.goal()>256||d.mobCount()<1||d.mobCount()>256||d.durationSeconds()<1||d.durationSeconds()>604800||!Double.isFinite(d.moneyPerParticipant())||d.moneyPerParticipant()<0||d.moneyPerParticipant()>1_000_000||d.costs().size()>64||d.rewards().size()>64)throw new IllegalArgumentException("Условия не подходят для поэтапного похода");
        var points=new HashSet<String>();for(int i=0;i<d.goal();i++){double angle=2*Math.PI*i/d.goal();points.add(Math.round(Math.cos(angle)*5)+","+Math.round(Math.sin(angle)*5));}if(points.size()!=d.goal())throw new IllegalArgumentException("Цели шаблона накладываются друг на друга");
        return new ExpeditionDefinition(d.id(),d.name(),d.type(),d.objective(),d.icon(),d.slot(),d.minCampLevel(),d.durationSeconds(),d.minDistance(),d.maxDistance(),d.goal(),d.objectiveMaterial(),d.mob(),d.mobCount(),copyItems(d.costs()),copyItems(d.rewards()),java.math.BigDecimal.valueOf(d.moneyPerParticipant()).setScale(2,java.math.RoundingMode.HALF_UP).doubleValue(),List.copyOf(d.description()));
    }
    private static List<ItemAmount> copyItems(List<ItemAmount> items){return items.stream().map(i->{if(i.amount()<1||i.amount()>1_000_000)throw new IllegalArgumentException("Неверное число предметов");return new ItemAmount(i.key(),i.item().clone(),i.amount());}).toList();}
    private static int count(Map<String,Contribution> items,Action action){return (int)items.values().stream().filter(c->c.action()==action).count();}
    public int objectives(){return count(contributions,Action.OBJECTIVE);}public int kills(){return count(contributions,Action.KILL);}
    public boolean complete(){return objectives()==frozen.goal()&&kills()==frozen.mobCount();}
    public Action stage(){return branch==Branch.SCOUT_FIRST?(objectives()<frozen.goal()?Action.OBJECTIVE:Action.KILL):(kills()<frozen.mobCount()?Action.KILL:Action.OBJECTIVE);}
    public String stageText(){return state!=State.ACTIVE?note:complete()?"Все этапы завершены":stage()==Action.OBJECTIVE?"Найдите цели: "+objectives()+" / "+frozen.goal()+(branch==Branch.SCOUT_FIRST?". Враги пока не участвуют в бою.":". Зачистка завершена."):"Победите врагов: "+kills()+" / "+frozen.mobCount()+(branch==Branch.FIGHT_FIRST?". Поиск целей откроется после зачистки.":". Разведка завершена.");}
    public Journey role(UUID player,Role role){if(state!=State.ACTIVE||!participants.contains(player))throw new IllegalArgumentException("Роль выбирает участник действующего похода");var next=new HashMap<>(roles);if(role==null)next.remove(player);else next.put(player,role);return new Journey(id,frozen,branch,participants,next,contributions,state,note);}
    public Journey credit(String key,Action action,UUID player,long now){
        if(state!=State.ACTIVE||complete()||stage()!=action||!participants.contains(player)||contributions.containsKey(key))throw new IllegalArgumentException("Действие уже учтено или этот этап пока недоступен");
        var next=new LinkedHashMap<>(contributions);next.put(key,new Contribution(key,action,player,roles.get(player),now));return new Journey(id,frozen,branch,participants,roles,next,state,note);
    }
    public boolean contributed(UUID player){return contributions.values().stream().anyMatch(c->c.player().equals(player));}
    public Journey state(State value,String reason){return new Journey(id,frozen,branch,participants,roles,contributions,value,reason);}
}
