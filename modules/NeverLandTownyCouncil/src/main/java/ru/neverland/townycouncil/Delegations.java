package ru.neverland.townycouncil;

import java.util.*;

/** Temporary grants supplement existing authority; expiry never removes unrelated ranks or personal rights. */
public final class Delegations {
    private Delegations(){}
    public static final long HOUR=3_600_000;
    public record Grant(UUID id,UUID town,UUID resident,UUID mayor,String action,long grantedAt,long expiresAt,String actor,String reason,boolean revoked,long revision){
        public Grant{Objects.requireNonNull(id);Objects.requireNonNull(town);Objects.requireNonNull(resident);Objects.requireNonNull(mayor);TownAction.parse(action);if(!TownAction.parse(action).id().equals(action)||resident.equals(mayor)||grantedAt<0||expiresAt<=grantedAt||actor==null||actor.isBlank()||actor.length()>80||reason==null||reason.isBlank()||reason.length()>240||revision<1)throw new IllegalArgumentException("Некорректные временные полномочия");}
        public String key(){return town+"/"+resident+"/"+action;}
        public boolean active(UUID currentMayor,boolean member,boolean officeEligible,long now){return !revoked&&member&&officeEligible&&mayor.equals(currentMayor)&&now>=grantedAt&&now<expiresAt;}
        public Grant revoke(){return revoked?this:new Grant(id,town,resident,mayor,action,grantedAt,expiresAt,actor,reason,true,Math.incrementExact(revision));}
    }
    public record Entry(UUID grant,UUID town,UUID resident,String action,UUID actor,String name,long at,String kind,String reason){
        public Entry{Objects.requireNonNull(grant);Objects.requireNonNull(town);Objects.requireNonNull(resident);Objects.requireNonNull(actor);TownAction.parse(action);if(name==null||name.isBlank()||name.length()>80||at<0||!Set.of("GRANT","REVOKE").contains(kind)||reason==null||reason.isBlank()||reason.length()>240)throw new IllegalArgumentException("Неверная история полномочий");}
    }
    public record State(Map<String,Grant> grants,List<Entry> history,long revision){
        public State{grants=Map.copyOf(grants);history=List.copyOf(history);if(grants.size()>10000||history.size()>1000||revision<0)throw new IllegalArgumentException("Требуется архивирование временных полномочий");var ids=new HashSet<UUID>();grants.forEach((key,g)->{if(!key.equals(g.key())||!ids.add(g.id()))throw new IllegalArgumentException("Повтор временного поручения");});}
        public static State empty(){return new State(Map.of(),List.of(),0);}
    }
    private static State next(State state,Grant grant,Entry entry){var grants=new HashMap<>(state.grants());grants.put(grant.key(),grant);var history=new ArrayList<>(state.history());history.add(entry);if(history.size()>1000)history=new ArrayList<>(history.subList(history.size()-1000,history.size()));return new State(grants,history,Math.incrementExact(state.revision()));}
    public static State grant(State state,Grant grant,long expected){if(state.revision()!=expected)throw new IllegalArgumentException("Полномочия изменились. Откройте подтверждение заново");if(grant.revoked()||grant.revision()!=1)throw new IllegalArgumentException("Нужно новое поручение");var old=state.grants().get(grant.key());if(old!=null&&old.active(grant.mayor(),true,true,grant.grantedAt()))throw new IllegalArgumentException("Это действие уже выдано на время; сначала отзовите прежнее поручение");if(state.grants().values().stream().anyMatch(g->g.id().equals(grant.id())))throw new IllegalArgumentException("Повтор номера поручения");return next(state,grant,new Entry(grant.id(),grant.town(),grant.resident(),grant.action(),grant.mayor(),grant.actor(),grant.grantedAt(),"GRANT",grant.reason()));}
    public static State revoke(State state,String key,UUID expectedId,long expectedRevision,UUID mayor,String name,String reason,long now){var grant=state.grants().get(key);if(grant==null||!grant.id().equals(expectedId)||grant.revision()!=expectedRevision)throw new IllegalArgumentException("Поручение изменилось. Откройте его заново");if(grant.revoked())return state;return next(state,grant.revoke(),new Entry(grant.id(),grant.town(),grant.resident(),grant.action(),mayor,name,now,"REVOKE",reason));}
}
