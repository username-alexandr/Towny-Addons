package ru.neverland.townyresources.integration;
import java.util.*;
/** Production consumes deltas between cumulative, process-local acknowledged power counters. */
public final class PowerCoverage {
    private record Key(UUID town,String project){}
    private record Counter(String epoch,long observed,long powered){}
    private final Map<Key,Counter> baselines=new HashMap<>();
    public double fraction(UUID town,String project,Map<?,?> value,boolean advance){
        if(!(value.get("required") instanceof Boolean required))throw new IllegalStateException("Не определены требования питания");if(!required)return 1;
        String epoch=Objects.toString(value.get("epoch"),"");long observed=((Number)value.get("observedNanos")).longValue(),powered=((Number)value.get("poweredNanos")).longValue();
        if(epoch.isBlank()||observed<0||powered<0||powered>observed)throw new IllegalStateException("Неверный учёт времени питания");
        var key=new Key(town,project);var current=new Counter(epoch,observed,powered);var old=baselines.putIfAbsent(key,current);double result=0;
        if(old!=null&&old.epoch().equals(epoch)&&observed>old.observed()&&powered>=old.powered())result=Math.min(1,(double)(powered-old.powered())/(observed-old.observed()));
        if(advance)baselines.put(key,current);return result;
    }
    public void retain(Set<UUID> towns){baselines.keySet().removeIf(k->!towns.contains(k.town()));}
}
