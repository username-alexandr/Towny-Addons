package ru.neverland.townypower.model;
import java.util.*;
/** Immutable saved controls and charge. A power lease is deliberately never restored. */
public record TownPowerState(Set<String> stopped,Map<String,Integer> priorities,Map<String,Integer> emergencyPriorities,long charge) {
    public static final long MAX_CHARGE=1_000_000_000_000L;
    public TownPowerState(Set<String> stopped,Map<String,Integer> priorities){this(stopped,priorities,Map.of(),0);}
    public TownPowerState{stopped=Set.copyOf(stopped);priorities=Map.copyOf(priorities);emergencyPriorities=Map.copyOf(emergencyPriorities);for(String id:stopped)valid(id);validate(priorities);validate(emergencyPriorities);if(charge<0||charge>MAX_CHARGE)throw new IllegalArgumentException("Неверный заряд накопителя");}
    private static void validate(Map<String,Integer> values){for(var e:values.entrySet()){valid(e.getKey());if(e.getValue()<0||e.getValue()>100)throw new IllegalArgumentException("Приоритет: 0..100");}}
    public static void valid(String id){if(id==null||!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Неверный ID здания");}
    public static TownPowerState empty(){return new TownPowerState(Set.of(),Map.of());}
    public TownPowerState stop(String id,boolean value){valid(id);var next=new HashSet<>(stopped);if(value)next.add(id);else next.remove(id);return new TownPowerState(next,priorities,emergencyPriorities,charge);}
    public TownPowerState priority(String id,int value){var next=new HashMap<>(priorities);next.put(id,value);return new TownPowerState(stopped,next,emergencyPriorities,charge);}
    public TownPowerState emergency(String id,int value){var next=new HashMap<>(emergencyPriorities);next.put(id,value);return new TownPowerState(stopped,priorities,next,charge);}
    public TownPowerState charge(long value){return new TownPowerState(stopped,priorities,emergencyPriorities,value);}
}
