package ru.neverland.townypower.model;
import java.util.*;
public record TownPowerState(Set<String> stopped,Map<String,Integer> priorities) {
    public TownPowerState{stopped=Set.copyOf(stopped);priorities=Map.copyOf(priorities);for(String id:stopped)valid(id);for(var e:priorities.entrySet()){valid(e.getKey());if(e.getValue()<0||e.getValue()>100)throw new IllegalArgumentException("Приоритет: 0..100");}}
    public static void valid(String id){if(id==null||!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Неверный ID здания");}
    public static TownPowerState empty(){return new TownPowerState(Set.of(),Map.of());}
    public TownPowerState stop(String id,boolean value){valid(id);var next=new HashSet<>(stopped);if(value)next.add(id);else next.remove(id);return new TownPowerState(next,priorities);}
    public TownPowerState priority(String id,int value){var next=new HashMap<>(priorities);next.put(id,value);return new TownPowerState(stopped,next);}
}
