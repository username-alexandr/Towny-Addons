package ru.neverland.townyenvironment.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import ru.neverland.core.DerivedViewCache;
import ru.neverland.townyenvironment.model.DistrictEnvironment;

/** Disposable derived views. Reads touch immutable maps only, never SuiteState, Bukkit, Towny or storage. */
public final class DistrictEnvironmentViews {
    private static final int LIMIT=128;
    private static final long TTL=120_000_000_000L;
    private record Published(Map<String,Object> report,long at){}
    private final AtomicReference<Map<UUID,Long>> interest=new AtomicReference<>(Map.of());
    private volatile Map<UUID,Published> published=Map.of();private volatile boolean closing;
    private final LongSupplier clock;private final DerivedViewCache<DistrictEnvironment.Input,Map<String,Object>> planner;
    public DistrictEnvironmentViews(Consumer<Throwable> diagnostic){this(System::nanoTime,diagnostic);}
    public DistrictEnvironmentViews(LongSupplier clock,Consumer<Throwable> diagnostic){this.clock=Objects.requireNonNull(clock);planner=new DerivedViewCache<>("NeverLand-environment-districts",DistrictEnvironment::calculate,diagnostic);}
    public void watch(UUID town){if(town==null||closing)return;long now=clock.getAsLong();while(true){var old=interest.get();var next=new HashMap<>(old);next.entrySet().removeIf(e->now-e.getValue()>=TTL);if(!next.containsKey(town)&&next.size()>=LIMIT){UUID oldest=next.entrySet().stream().max(Comparator.comparingLong(e->now-e.getValue())).orElseThrow().getKey();next.remove(oldest);}next.put(town,now);if(interest.compareAndSet(old,Map.copyOf(next)))return;}}
    public Set<UUID> wanted(){long now=clock.getAsLong();var result=new HashSet<UUID>();interest.get().forEach((id,at)->{if(now-at<TTL)result.add(id);});return Set.copyOf(result);}
    public Map<String,Object> read(UUID town){if(closing)return unavailable("Раздел экологии останавливается",false,0,false);if(town==null)return unavailable("Город не найден",false,0,false);watch(town);var value=published.get(town);return value==null||clock.getAsLong()-value.at()>=TTL?unavailable("Данные экологии готовятся. Обновите окно через несколько секунд",true,0,false):value.report();}
    /** Called by the suite-owned metadata capture, never by an async integration reader. */
    public void capture(UUID town,DistrictEnvironment.Input input,long sourceAt,boolean paused){if(closing)return;var reading=planner.read(town,input);publish(town,reading.status().equals("READY")?report(reading.result(),reading.at(),sourceAt,paused):unavailable(reading.issue(),reading.status().equals("PENDING"),sourceAt,paused));}
    public void unavailable(UUID town,String reason,boolean pending,long sourceAt,boolean paused){if(!closing)publish(town,unavailable(reason,pending,sourceAt,paused));}
    private void publish(UUID town,Map<String,Object> report){Set<UUID> wanted=wanted();var next=new HashMap<>(published);next.keySet().retainAll(wanted);if(wanted.contains(town))next.put(town,new Published(report,clock.getAsLong()));published=Map.copyOf(next);}
    public static Map<String,Object> unavailable(String reason,boolean pending,long sourceAt,boolean paused){return Map.of("available",false,"pending",pending,"issue",reason,"checkedAt",0L,"sourceAt",sourceAt,"paused",paused,"rows",Map.of(),"totals",Map.of());}
    public static Map<String,Object> report(Map<String,Object> result,long checkedAt,long sourceAt,boolean paused){return Map.of("available",true,"pending",false,"issue","","checkedAt",checkedAt,"sourceAt",sourceAt,"paused",paused,"rows",result.get("rows"),"totals",result.get("totals"));}
    public CompletionStage<Void> prepareClose(){closing=true;interest.set(Map.of());published=Map.of();return planner.prepareClose();}
    public void awaitClosed(){planner.awaitClosed();}
}
