package ru.neverland.townypower.model;
import java.util.*;
/** Cumulative monotonic counters avoid unbounded per-tick history. They never cross a process epoch. */
public final class PowerUptime {
    private final String epoch=UUID.randomUUID().toString();private final long started;
    private final Map<UUID,Map<String,Long>> totals=new HashMap<>();
    private final Map<UUID,Long> settled=new HashMap<>();
    public PowerUptime(long now){started=now;}
    public void settle(UUID town,PowerWindow window,PowerEngine.Grid grid,long now){
        if(window==null||now-window.expires()<0)return;var prior=settled.get(town);if(prior!=null&&window.expires()-prior<=0)return;settled.put(town,window.expires());long duration=elapsed(window,now);if(duration==0)return;var values=totals.computeIfAbsent(town,k->new HashMap<>());
        grid.buildings().forEach((id,a)->{if(a.powered())values.merge(id,duration,Math::addExact);});
    }
    public Map<String,Object> read(UUID town,String project,boolean required,PowerWindow current,PowerEngine.Grid grid,long now){
        long value=totals.getOrDefault(town,Map.of()).getOrDefault(project,0L);var a=grid==null?null:grid.buildings().get(project);
        var prior=settled.get(town);if(a!=null&&a.powered()&&(current!=null&&(prior==null||current.expires()-prior>0)))value=Math.addExact(value,elapsed(current,now));
        return Map.of("epoch",epoch,"observedNanos",Math.max(0,now-started),"poweredNanos",value,"required",required);
    }
    private static long elapsed(PowerWindow w,long now){if(w==null||now-w.opened()<0)return 0;return Math.max(0,Math.min(now-w.opened(),w.expires()-w.opened()));}
    public void retain(Set<UUID> towns){totals.keySet().retainAll(towns);settled.keySet().retainAll(towns);}
}
