package ru.neverland.townyresearch.model;
import java.util.*;
public record CityStudy(Map<String,Integer> learned,Study active,Set<UUID> cleanup) {
    public CityStudy{learned=Technology.checked(learned);if(!Technology.IDS.containsAll(learned.keySet())||(active!=null&&!Technology.IDS.contains(active.technology())))throw new IllegalArgumentException("Неизвестная сохранённая технология");cleanup=Set.copyOf(cleanup);if(cleanup.size()>1024)throw new IllegalArgumentException("Слишком много квитанций");if(active!=null&&(active.level()!=learned.getOrDefault(active.technology(),0)+1||cleanup.contains(active.invoice())))throw new IllegalArgumentException("Несогласованный уровень исследования");}
    public enum Phase{PREPARED,RUNNING,COMPLETING,CANCELLING}
    public record Study(UUID invoice,String technology,int level,long cost,int duration,int remaining,Map<String,Integer> buildings,Phase phase,int fraction){
        public Study(UUID invoice,String technology,int level,long cost,int duration,int remaining,Map<String,Integer> buildings,Phase phase){this(invoice,technology,level,cost,duration,remaining,buildings,phase,0);}
        public Study{Objects.requireNonNull(invoice);Objects.requireNonNull(phase);Technology.validId(technology);buildings=Technology.checked(buildings);if(fraction<0||fraction>999||(remaining==0&&fraction!=0)||level<1||level>5||cost<1||cost>1_000_000_000_000L||duration<5||duration>604800||remaining<0||remaining>duration||buildings.isEmpty()||(phase==Phase.COMPLETING&&remaining!=0))throw new IllegalArgumentException("Неверное сохранённое исследование");}
        public Study phase(Phase p){return new Study(invoice,technology,level,cost,duration,remaining,buildings,p,fraction);}
        public Study advance(int seconds){return advance(seconds,0);}
        public Study advance(int seconds,double bonus){if(seconds<0||seconds>60)throw new IllegalArgumentException("Шаг расчёта: 0..60 секунд");double safe=ru.neverland.integration.SpecializationRules.bound("research_speed",bonus);long elapsed=seconds*1000L+Math.round(seconds*1000L*safe)+fraction;int left=Math.max(0,remaining-(int)(elapsed/1000));return new Study(invoice,technology,level,cost,duration,left,buildings,left==0?Phase.COMPLETING:Phase.RUNNING,left==0?0:(int)(elapsed%1000));}
    }
    public static CityStudy empty(){return new CityStudy(Map.of(),null,Set.of());}
    public CityStudy study(Study s){return new CityStudy(learned,s,cleanup);}
    public CityStudy finish(boolean complete){var next=new HashMap<>(learned);if(complete)next.put(active.technology(),active.level());var pending=new HashSet<>(cleanup);pending.add(active.invoice());return new CityStudy(next,null,pending);}
    public CityStudy forget(UUID id){var next=new HashSet<>(cleanup);next.remove(id);return new CityStudy(learned,active,next);}
}
