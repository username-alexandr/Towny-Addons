package ru.neverland.townycouncil;

import java.util.*;

/** Immutable city-local overrides, scoped to the current mayor's term. Missing means inherit. */
public record AccessRules(UUID mayor,long revision,Map<String,Map<String,Boolean>> roles,Map<UUID,Map<String,Boolean>> residents) {
    public AccessRules {
        Objects.requireNonNull(mayor);if(revision<0)throw new IllegalArgumentException("Повреждена версия прав");
        roles=copy(roles);residents=copy(residents);
        roles.keySet().forEach(k->{if(!MinisterRole.parse(k).id().equals(k))throw new IllegalArgumentException("Некорректная должность");});
        if(residents.containsKey(mayor))throw new IllegalArgumentException("Права мэра нельзя ограничить");
    }
    private static <K> Map<K,Map<String,Boolean>> copy(Map<K,Map<String,Boolean>> map){
        var result=new HashMap<K,Map<String,Boolean>>();map.forEach((k,v)->{
            Objects.requireNonNull(k);v.forEach((a,b)->{if(!TownAction.parse(a).id().equals(a))throw new IllegalArgumentException("Некорректное действие");Objects.requireNonNull(b);});
            if(!v.isEmpty())result.put(k,Map.copyOf(v));
        });return Map.copyOf(result);
    }
    public static AccessRules empty(UUID mayor){return new AccessRules(mayor,0,Map.of(),Map.of());}
    public Boolean override(UUID resident,String role,TownAction action){
        Boolean personal=residents.getOrDefault(resident,Map.of()).get(action.id());
        if(personal!=null)return personal;
        return roles.getOrDefault(role,Map.of()).get(action.id());
    }
    public AccessRules change(String role,UUID resident,TownAction action,Boolean allow){
        var nextRoles=new HashMap<>(roles);var nextResidents=new HashMap<>(residents);
        if(resident!=null)edit(nextResidents,resident,action.id(),allow);else{MinisterRole.parse(role);edit(nextRoles,role,action.id(),allow);}
        return new AccessRules(mayor,Math.addExact(revision,1),nextRoles,nextResidents);
    }
    private static <K> void edit(Map<K,Map<String,Boolean>> map,K key,String action,Boolean allow){
        var next=new HashMap<>(map.getOrDefault(key,Map.of()));if(allow==null)next.remove(action);else next.put(action,allow);
        if(next.isEmpty())map.remove(key);else map.put(key,Map.copyOf(next));
    }
    public AccessRules without(UUID resident){var next=new HashMap<>(residents);next.remove(resident);return new AccessRules(mayor,Math.addExact(revision,1),roles,next);}
}
