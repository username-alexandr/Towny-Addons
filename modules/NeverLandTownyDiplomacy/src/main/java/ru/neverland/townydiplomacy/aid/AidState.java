package ru.neverland.townydiplomacy.aid;

import java.util.*;

public record AidState(Map<UUID,AidPact> pacts,Map<UUID,AidCall> calls,List<History> history,Map<String,ru.neverland.core.AuditRecord> outbox) {
    public AidState(Map<UUID,AidPact> pacts,Map<UUID,AidCall> calls,List<History> history){this(pacts,calls,history,Map.of());}
    public record History(UUID id,UUID pact,long at,String actor,String action){public History{Objects.requireNonNull(id);Objects.requireNonNull(pact);if(at<0||actor==null||actor.isBlank()||actor.length()>100||action==null||action.isBlank()||action.length()>200)throw new IllegalArgumentException("Повреждена история помощи");}}
    public AidState {
        pacts=Map.copyOf(pacts);calls=Map.copyOf(calls);history=List.copyOf(history);outbox=Map.copyOf(outbox);
        if(pacts.size()>2000||calls.size()>2000||history.size()>4000)throw new IllegalArgumentException("Реестр помощи достиг предела");
        for(var e:pacts.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("Ключ соглашения не совпадает");
        for(var e:calls.entrySet()){var c=e.getValue();var p=pacts.get(c.pact());if(!e.getKey().equals(c.id())||p==null||!p.party(c.helper())||!p.party(c.beneficiary())||c.requiredMilliDamage()!=p.requiredMilliDamage())throw new IllegalArgumentException("Вызов другого соглашения");}
        for(var h:history)if(!pacts.containsKey(h.pact()))throw new IllegalArgumentException("История другого соглашения");
        if(outbox.size()>4096)throw new IllegalArgumentException("Очередь аудита помощи заполнена");for(var e:outbox.entrySet())if(!e.getKey().equals(e.getValue().id())||!e.getValue().module().equals("NeverLandTownyDiplomacy")||!e.getValue().kind().equals("DIPLOMACY_AID"))throw new IllegalArgumentException("Повреждена квитанция аудита помощи");
    }
    public static AidState empty(){return new AidState(Map.of(),Map.of(),List.of());}
    public AidState withOutbox(Map<String,ru.neverland.core.AuditRecord> records){return new AidState(pacts,calls,history,records);}
    public AidState put(AidPact pact,String actor,String action,long at){var p=new HashMap<>(pacts);p.put(pact.id(),pact);var h=new ArrayList<>(history);h.add(new History(UUID.randomUUID(),pact.id(),at,actor,action));if(h.size()>4000)h.remove(0);return new AidState(p,calls,h,outbox);}
    public AidState neutral(String why){var c=new HashMap<>(calls);calls.values().stream().filter(v->v.status()==AidCall.Status.OPEN).forEach(v->c.put(v.id(),v.close(AidCall.Status.UNASSESSED,why)));return new AidState(pacts,c,history,outbox);}
    public AidState observe(AidFact fact,Set<UUID> eligible){
        var c=new HashMap<>(calls);for(var call:calls.values())if(eligible.contains(call.pact()))c.put(call.id(),call.observe(fact));
        if(fact.territory().equals(fact.victimTown()))for(var pact:pacts.values())if(eligible.contains(pact.id())&&pact.live(fact.at())&&pact.party(fact.victimTown())&&!pact.party(fact.actorTown())){
            boolean recent=c.values().stream().anyMatch(v->v.pact().equals(pact.id())&&v.beneficiary().equals(fact.victimTown())&&v.enemy().equals(fact.actorTown())&&fact.at()<v.deadline());
            if(!recent&&c.size()<2000){var call=AidCall.open(pact,fact);c.put(call.id(),call);}
        }
        return new AidState(pacts,c,history,outbox);
    }
    public AidState expire(long now,Set<UUID> eligible){
        var c=new HashMap<>(calls);for(var call:calls.values())if(call.status()==AidCall.Status.OPEN){if(!eligible.contains(call.pact()))c.put(call.id(),call.close(AidCall.Status.UNASSESSED,"Союз или поставщик данных недоступен; оценка остановлена"));else if(now>=call.deadline())c.put(call.id(),call.close(AidCall.Status.UNANSWERED,"За срок недостаточно подтверждённых действий; автоматических последствий нет"));}
        return new AidState(pacts,c,history,outbox);
    }
}
