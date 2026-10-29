package ru.neverland.townyquests.cooperative;

import java.util.*;

/** Short in-memory bookkeeping only; no world, storage, network or scheduler access. */
final class CooperativeUnlockCache {
    static final long REFRESH=20_000_000_000L,STALE=60_000_000_000L,IDLE=120_000_000_000L;
    private record Result(Set<String> keys,long at,boolean ready){Result{keys=Set.copyOf(keys);}}
    private final Map<UUID,Long> watched=new LinkedHashMap<>();
    private final Map<UUID,Result> results=new HashMap<>();
    private final Set<UUID> pending=new HashSet<>();
    synchronized void watch(UUID town,long now){if(!watched.containsKey(town)&&watched.size()>=128){UUID first=watched.keySet().iterator().next();watched.remove(first);results.remove(first);}watched.put(town,now);}
    synchronized boolean start(UUID town,long now){var old=results.get(town);return watched.containsKey(town)&&pending.size()<16&&(old==null||now-old.at()>=REFRESH)&&pending.add(town);}
    synchronized void complete(UUID town,Set<String> keys,long now,boolean ready){pending.remove(town);if(watched.containsKey(town))results.put(town,new Result(ready?keys:Set.of(),now,ready));}
    synchronized List<UUID> due(long now){watched.entrySet().removeIf(e->now-e.getValue()>IDLE);results.keySet().retainAll(watched.keySet());return watched.keySet().stream().filter(t->!pending.contains(t)).filter(t->{var r=results.get(t);return r==null||now-r.at()>=REFRESH;}).sorted(Comparator.comparingLong(t->{var r=results.get(t);return r==null?Long.MIN_VALUE:r.at();})).limit(Math.max(0,16-pending.size())).toList();}
    synchronized boolean read(UUID town,String key,long now){var result=results.get(town);if(result==null||!result.ready()||now-result.at()>STALE)throw new IllegalStateException("Результаты общих строек ожидают сверки со складом");return result.keys().contains(key);}
    synchronized void clear(){watched.clear();results.clear();pending.clear();}
}
