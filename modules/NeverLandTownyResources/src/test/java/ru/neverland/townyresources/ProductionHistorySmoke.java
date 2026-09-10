package ru.neverland.townyresources;
import java.util.*;
import java.nio.file.*;
import java.time.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyresources.model.*;
import ru.neverland.townyresources.data.ResourcesRepository;
public final class ProductionHistorySmoke {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void run()throws Exception{
        long now=Instant.parse("2026-09-13T23:59:59Z").toEpochMilli();UUID town=UUID.randomUUID();Path file=Files.createTempDirectory("production-history").resolve("resources.yml");var repo=new ResourcesRepository(file);repo.load();var base=TownState.initial(Map.of(Resource.WOOD,10000L,Resource.STONE,10000L));repo.replace(Map.of(town,base));
        check(repo.productionWeeks(town).isEmpty(),"initial inventory is not production");var next=new TownState(base.balances(),base.reserves(),Set.of(),Map.of(),1,now,Map.of(Resource.WOOD,1250L),Map.of(Resource.STONE,500L),1,1);repo.replaceCycle(Map.of(town,next));String week=ProductionHistory.week(now);check(repo.productionWeeks(town).get(week).get("produced_wood")==1250&&repo.productionWeeks(town).get(week).get("consumed_stone")==500,"actual cycle flows stored");
        repo.replace(Map.of(town,next.balance(Resource.WOOD,20000)));check(repo.productionWeeks(town).get(week).get("cycles")==1,"manual inventory edits do not count production");var restart=new ResourcesRepository(file);restart.load();check(restart.productionWeeks(town).equals(repo.productionWeeks(town)),"flow and stock survive same atomic commit");
        UUID reservation=UUID.randomUUID();restart.reserve(reservation,town,Map.of(Resource.STONE,1000L),Map.of());check(restart.productionWeeks(town).equals(repo.productionWeeks(town)),"reservation is not consumption");restart.settle(reservation,true);var totals=restart.productionWeeks(town);restart.settle(reservation,true);check(restart.productionWeeks(town).equals(totals),"settled upkeep counted once");check(totals.values().stream().mapToLong(v->v.get("consumed_stone")).sum()==1500,"cycle and paid upkeep consumption included");
        UUID refund=UUID.randomUUID();restart.reserve(refund,town,Map.of(Resource.STONE,1000L),Map.of());restart.settle(refund,false);check(restart.productionWeeks(town).equals(totals),"returned reservation is not consumption");
        var before=restart.states().get(town);var monday=new TownState(before.balances(),before.reserves(),Set.of(),Map.of(),2,now+1000,Map.of(Resource.WOOD,500L),Map.of(),1,1);restart.replaceCycle(Map.of(town,monday));check(restart.productionWeeks(town).get("2026-09-14").get("produced_wood")==500,"UTC Monday separate week");
        var yaml=new YamlConfiguration();yaml.load(file.toFile());yaml.set("schema",2);yaml.set("production",null);yaml.save(file.toFile());var legacy=new ResourcesRepository(file);legacy.load();check(legacy.states().get(town).equals(monday)&&legacy.productionWeeks(town).isEmpty(),"schema 2 migration preserves stock without invented past income");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"safe");var third=new TownState(monday.balances(),monday.reserves(),Set.of(),Map.of(),3,now+2000,Map.of(Resource.WOOD,10L),Map.of(),1,1);boolean failed=false;try{legacy.replaceCycle(Map.of(town,third));}catch(Exception expected){failed=true;}check(failed&&legacy.states().get(town).equals(monday)&&legacy.productionWeeks(town).isEmpty(),"failed disk commit advances neither inventory nor report");
        var huge=new HashMap<String,Long>();for(String key:ProductionHistory.keys())huge.put(key,0L);huge.put("produced_wood",Long.MAX_VALUE-1);var saturated=ProductionHistory.advance(Map.of(town,Map.of(ProductionHistory.week(now+2000),huge)),Map.of(town,monday),Map.of(town,third),Map.of(),Map.of(),now+2000,true);check(saturated.get(town).get(ProductionHistory.week(now+2000)).get("overflow")==1&&saturated.get(town).get(ProductionHistory.week(now+2000)).get("produced_wood")==Long.MAX_VALUE,"report overflow is explicit and does not stop the resource cycle");
        System.out.println("ProductionHistorySmoke OK: actual cycle counts, UTC rollover, upkeep consume/refund deduplication, schema 2 migration and atomic stock/report persistence");
    }
}
