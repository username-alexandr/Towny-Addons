package ru.neverland.townyresources.model;
import java.util.*;
import java.time.*;
import java.time.temporal.*;
/** Actual completed resource cycles and settled upkeep, committed with the inventory. */
public final class ProductionHistory {
    private ProductionHistory(){}
    public static String week(long at){return Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();}
    public static Map<UUID,Map<String,Map<String,Long>>> copy(Map<UUID,Map<String,Map<String,Long>>> input){var out=new HashMap<UUID,Map<String,Map<String,Long>>>();for(var entry:input.entrySet()){var weeks=new TreeMap<String,Map<String,Long>>();for(var item:entry.getValue().entrySet()){if(LocalDate.parse(item.getKey()).getDayOfWeek()!=DayOfWeek.MONDAY)throw new IllegalArgumentException("Неверная неделя производства");var values=new HashMap<String,Long>();if(item.getValue().size()!=18)throw new IllegalArgumentException("Неполный недельный отчёт ресурсов");for(String key:keys()){Long value=item.getValue().get(key);if(value==null||value<0)throw new IllegalArgumentException("Неверный итог ресурсов");values.put(key,value);}weeks.put(item.getKey(),Map.copyOf(values));}if(weeks.size()>13)throw new IllegalArgumentException("Слишком длинная история производства");out.put(entry.getKey(),Map.copyOf(weeks));}return Map.copyOf(out);}
    public static List<String> keys(){var out=new ArrayList<String>();out.add("cycles");out.add("overflow");for(var r:Resource.values()){out.add("produced_"+r.id());out.add("consumed_"+r.id());}return out;}
    private static void add(Map<UUID,Map<String,Map<String,Long>>> totals,UUID town,String week,Map<String,Long> delta){var weeks=new TreeMap<>(totals.getOrDefault(town,Map.of()));var values=new HashMap<>(weeks.getOrDefault(week,Map.of()));for(String key:keys()){long old=values.getOrDefault(key,0L),add=delta.getOrDefault(key,0L);if(Long.MAX_VALUE-old<add){values.put(key,Long.MAX_VALUE);values.put("overflow",1L);}else values.put(key,old+add);}weeks.put(week,Map.copyOf(values));while(weeks.size()>13)weeks.pollFirstEntry();totals.put(town,Map.copyOf(weeks));}
    public static Map<UUID,Map<String,Map<String,Long>>> advance(Map<UUID,Map<String,Map<String,Long>>> previous,Map<UUID,TownState> before,Map<UUID,TownState> after,Map<UUID,Reservation> oldReceipts,Map<UUID,Reservation> receipts,long now,boolean recordCycles){var totals=new HashMap<>(previous);
        if(recordCycles) for(var entry:after.entrySet()){var old=before.get(entry.getKey());var next=entry.getValue();long prior=old==null?0:old.cycles();if(next.cycles()<prior||next.cycles()>prior+1)throw new IllegalArgumentException("Пропущен цикл производства");if(next.cycles()==prior+1){var delta=new HashMap<String,Long>();delta.put("cycles",1L);for(var r:Resource.values()){delta.put("produced_"+r.id(),next.income().get(r));delta.put("consumed_"+r.id(),next.expense().get(r));}add(totals,entry.getKey(),week(next.lastCycle()),delta);}}
        for(var entry:receipts.entrySet()){var old=oldReceipts.get(entry.getKey());var next=entry.getValue();if(old!=null&&old.status()==Reservation.Status.HELD&&next.status()==Reservation.Status.CONSUMED){var delta=new HashMap<String,Long>();for(var r:Resource.values())delta.put("consumed_"+r.id(),next.amounts().get(r));add(totals,next.town(),week(now),delta);}}
        // UUID-keyed history survives a town's deletion; a newly created town cannot inherit it.
        return copy(totals);
    }
}
