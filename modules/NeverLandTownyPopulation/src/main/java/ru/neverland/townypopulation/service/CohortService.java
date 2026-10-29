package ru.neverland.townypopulation.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import ru.neverland.core.DerivedViewCache;
import ru.neverland.townypopulation.model.DistrictCohorts;

/** Disposable derived views. Reads touch immutable maps only, never SuiteState, Bukkit, Towny or storage. */
public final class CohortService {
    private static final int LIMIT=128;
    private static final long TTL=120_000_000_000L;
    private record Published(Map<String,Object> report,long at){}
    private final AtomicReference<Map<UUID,Long>> interest=new AtomicReference<>(Map.of());
    private volatile Map<UUID,Published> published=Map.of();private volatile boolean closing;
    private final LongSupplier clock;private final DerivedViewCache<DistrictCohorts.Input,DistrictCohorts.Result> planner;
    public CohortService(Consumer<Throwable> diagnostic){this(System::nanoTime,diagnostic);}
    public CohortService(LongSupplier clock,Consumer<Throwable> diagnostic){this.clock=Objects.requireNonNull(clock);planner=new DerivedViewCache<>("NeverLand-population-cohorts",DistrictCohorts::calculate,diagnostic);}
    public void watch(UUID town){if(town==null||closing)return;long now=clock.getAsLong();while(true){var old=interest.get();var next=new HashMap<>(old);next.entrySet().removeIf(e->now-e.getValue()>=TTL);if(!next.containsKey(town)&&next.size()>=LIMIT){UUID oldest=next.entrySet().stream().max(Comparator.comparingLong(e->now-e.getValue())).orElseThrow().getKey();next.remove(oldest);}next.put(town,now);if(interest.compareAndSet(old,Map.copyOf(next)))return;}}
    public Set<UUID> wanted(){long now=clock.getAsLong();var result=new HashSet<UUID>();interest.get().forEach((id,at)->{if(now-at<TTL)result.add(id);});return Set.copyOf(result);}
    public Map<String,Object> read(UUID town){if(closing)return unavailable("Раздел населения останавливается",false,0,false);if(town==null)return unavailable("Город не найден",false,0,false);watch(town);var value=published.get(town);return value==null||clock.getAsLong()-value.at()>=TTL?unavailable("Данные населения готовятся. Обновите окно через несколько секунд",true,0,false):value.report();}
    /** Called by the suite-owned metadata capture, never by an async integration reader. */
    public void capture(UUID town,DistrictCohorts.Input input,long sourceAt,boolean paused){if(closing)return;var reading=planner.read(town,input);publish(town,reading.status().equals("READY")?report(reading.result(),reading.at(),sourceAt,paused):unavailable(reading.issue(),reading.status().equals("PENDING"),sourceAt,paused));}
    public void unavailable(UUID town,String reason,boolean pending,long sourceAt,boolean paused){if(!closing)publish(town,unavailable(reason,pending,sourceAt,paused));}
    private void publish(UUID town,Map<String,Object> report){Set<UUID> wanted=wanted();var next=new HashMap<>(published);next.keySet().retainAll(wanted);if(wanted.contains(town))next.put(town,new Published(report,clock.getAsLong()));published=Map.copyOf(next);}
    public static Map<String,Object> unavailable(String reason,boolean pending,long sourceAt,boolean paused){return Map.of("available",false,"pending",pending,"issue",reason,"checkedAt",0L,"sourceAt",sourceAt,"paused",paused,"rows",Map.of(),"totals",Map.of());}
    public static Map<String,Object> report(DistrictCohorts.Result result,long checkedAt,long sourceAt,boolean paused){
        var rows=new TreeMap<String,Object>();for(var group:result.groups()){
            var row=new LinkedHashMap<String,Object>();row.put("id",group.id());row.put("name",group.name());row.put("population",group.population());row.put("housing",group.housing());row.put("jobs",group.jobs());row.put("workforce",group.workforce());row.put("employed",group.employed());row.put("unemployed",group.unemployed());row.put("other",group.other());row.put("foodDemand",group.foodDemand().toPlainString());row.put("waterDemand",group.waterDemand().toPlainString());row.put("foodSupply",group.foodSupply().toPlainString());row.put("waterSupply",group.waterSupply().toPlainString());
            var district=group.district();row.put("district",district!=null);row.put("connectionKnown",district!=null&&district.connectionKnown());row.put("connected",district==null?0:district.connected());row.put("plots",district==null?0:district.plots());var services=new TreeMap<String,Object>();if(district!=null)for(var service:district.services())services.put(service.id(),Map.of("title",service.title(),"known",service.known(),"served",service.served(),"total",service.plots()));row.put("services",Map.copyOf(services));rows.put(group.id(),Map.copyOf(row));
        }
        var totals=new LinkedHashMap<String,Object>();totals.put("population",result.population());totals.put("housing",result.housing());totals.put("jobs",result.jobs());totals.put("workforce",result.workforce());totals.put("employed",result.employed());totals.put("unemployed",result.workforce()-result.employed());totals.put("other",result.population()-result.workforce());totals.put("foodDemand",result.foodDemand().toPlainString());totals.put("waterDemand",result.waterDemand().toPlainString());totals.put("foodSupply",result.foodSupply().toPlainString());totals.put("waterSupply",result.waterSupply().toPlainString());
        return Map.of("available",true,"pending",false,"issue","","checkedAt",checkedAt,"sourceAt",sourceAt,"paused",paused,"rows",Map.copyOf(rows),"totals",Map.copyOf(totals));
    }
    public CompletionStage<Void> prepareClose(){closing=true;interest.set(Map.of());published=Map.of();return planner.prepareClose();}
    public void awaitClosed(){planner.awaitClosed();}
}
