package ru.neverland.runtime;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.event.inventory.*;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.townypopulation.*;
import ru.neverland.townypopulation.service.*;
import ru.neverland.townypopulation.data.*;
import ru.neverland.townydistricts.*;
import ru.neverland.townydistricts.model.*;
import ru.neverland.townydistricts.model.District;
import ru.neverland.townydistricts.service.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.construction.*;

/** Actual region metadata APIs, explicit persisted completed-site fixtures and offline native inventory actor. */
public final class PopulationCohortsProbe extends AdmissionsProbe {
    NeverLandTownyPopulation populationPlugin;PopulationService population;PopulationRepository repository;NeverLandTownyDistricts districtsPlugin;DistrictService districts;DataStore buildings;byte[] populationFile,districtFile;
    void in(Work work){try{work.run();}catch(Throwable error){fail(error);}}
    @SuppressWarnings("unchecked")Map<String,Map<String,Object>> rows(Map<String,Object> report){return(Map<String,Map<String,Object>>)report.get("rows");}
    @SuppressWarnings("unchecked")Map<String,Object> totals(Map<String,Object> report){return(Map<String,Object>)report.get("totals");}
    void ready(int remaining,Consumer<Map<String,Object>> next)throws Exception{population.refreshCohorts(T);var report=population.cohorts(T);if(Boolean.TRUE.equals(report.get("available"))){next.accept(report);return;}if(!Boolean.TRUE.equals(report.get("pending"))||remaining<=0)throw new AssertionError("cohorts not ready: "+report);later(1,()->ready(remaining-1,next));}
    void startReady(int remaining,Work next)throws Exception{if(repository.busy()||!Boolean.TRUE.equals(field(population,"startupReady"))){if(remaining<=0)throw new AssertionError("population startup");later(1,()->startReady(remaining-1,next));return;}next.run();}
    @Override void setup()throws Exception{
        populationPlugin=(NeverLandTownyPopulation)Bukkit.getPluginManager().getPlugin("NeverLandTownyPopulation");population=(PopulationService)field(populationPlugin,"service");repository=(PopulationRepository)field(population,"repository");districtsPlugin=(NeverLandTownyDistricts)Bukkit.getPluginManager().getPlugin("NeverLandTownyDistricts");districts=(DistrictService)field(districtsPlugin,"service");buildings=(DataStore)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds"),"dataStore");mayor=actor(M,"CohortMayor");
        check(populationPlugin.isEnabled()&&populationPlugin.getDescription().getVersion().equals("0.2.1"),"validated Population 0.2.1 enabled with real Towny Builds Districts");
        startReady(80,()->{if(restart){town=TownyAPI.getInstance().getTown(T);ready(80,r->in(()->resumeCohorts(r)));return;}
            var u=TownyUniverse.getInstance();u.newTownInternal("CohortCity",T);town=TownyAPI.getInstance().getTown(T);var resident=u.getDataSource().newResident("CohortMayor",M);resident.setTown(town);town.setMayor(resident);resident.save();
            for(int x=-2;x<=15;x++)for(int z=-3;z<=3;z++){var claim=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);if(x==0&&z==0)town.setHomeBlock(claim);claim.save();}town.setSpawn(origin);town.save();
            for(var e:Map.of("town_hall",0,"tavern_inn",5,"forge",12).entrySet()){var site=new ConstructionSite(e.getKey(),world.getUID(),e.getValue()*16+8,160,8,BlockFace.NORTH,5,5,1,false);site.blueprintSet(BlueprintCatalog.currentPin(e.getKey(),BuildingStyle.MEDIEVAL));buildings.town(T).setConstructionSite(site);buildings.town(T).setLevel(e.getKey(),5);}buildings.markDirty();buildings.saveOrThrow();
            var residential=new HashSet<Cell>();var industrial=new HashSet<Cell>();for(int x=2;x<=8;x++)for(int z=-3;z<=3;z++)residential.add(new Cell(world.getUID(),x,z));for(int x=9;x<=15;x++)for(int z=-3;z<=3;z++)industrial.add(new Cell(world.getUID(),x,z));districts.put(new District(T,"homes","Жилой квартал",DistrictType.RESIDENTIAL,residential),true);districts.put(new District(T,"industry","Мастерские",DistrictType.INDUSTRIAL,industrial),true);
            await(population.setPopulation(T,37),saved->in(()->{population.refreshTownView(T);populationFile=Files.readAllBytes(populationPlugin.getDataFolder().toPath().resolve("population-data.yml"));districtFile=Files.readAllBytes(districtsPlugin.getDataFolder().toPath().resolve("districts-data.yml"));ready(80,r->in(()->baseline(r)));}));
        });
    }
    void conserved(Map<String,Object> report)throws Exception{var total=totals(report);var rows=rows(report);var snapshot=population.population(T).orElseThrow();for(String key:List.of("population","housing","jobs","workforce","employed")){long sum=rows.values().stream().mapToLong(r->((Number)r.get(key)).longValue()).sum();check(sum==((Number)total.get(key)).longValue(),"derived groups conserve city "+key);}
        check(((Number)total.get("population")).intValue()==snapshot.population()&&((Number)total.get("housing")).intValue()==snapshot.capacity().housing()&&((Number)total.get("jobs")).intValue()==snapshot.capacity().jobs(),"cohort totals match confirmed simulation snapshot");
        for(String key:List.of("foodDemand","waterDemand","foodSupply","waterSupply")){var sum=BigDecimal.ZERO;for(var row:rows.values())sum=sum.add(new BigDecimal((String)row.get(key)));check(sum.compareTo(new BigDecimal((String)total.get(key)))==0,"decimal group shares conserve "+key);}
    }
    void baseline(Map<String,Object> report)throws Exception{
        conserved(report);check(rows(report).size()==3&&((Number)rows(report).get("district:homes").get("population")).intValue()>0&&((Number)rows(report).get("district:industry").get("population")).intValue()==0,"actual residential building receives residents while industrial workplace alone does not create housing");
        check(((Number)rows(report).get("district:industry").get("jobs")).intValue()>0&&((Number)rows(report).get("city").get("population")).intValue()>0,"physical industrial jobs and unassigned base housing retain separate groups");
        var future=java.util.concurrent.CompletableFuture.supplyAsync(()->population.cohorts(T));later(2,()->{check(future.isDone()&&Boolean.TRUE.equals(future.join().get("available")),"async public cohorts API uses detached cache without waiting for suite ownership");menusAndPause();});
    }
    void menusAndPause()throws Exception{
        populationPlugin.getCommand("townypopulation").execute(mayor,"population",new String[]{});check(windows.get(M).getItem(47).getType()==Material.FILLED_MAP,"main native menu has one clear district population button");
        populationPlugin.getCommand("townypopulation").execute(mayor,"population",new String[]{"cohorts"});check(windows.get(M)!=null&&windows.get(M).getSize()==54,"real population cohorts command opens native paginated inventory");
        String text="";for(var item:windows.get(M).getContents())if(item!=null&&item.hasItemMeta()){text+=item.getItemMeta().getDisplayName();if(item.getItemMeta().hasLore())text+=String.join(" ",item.getItemMeta().getLore());}check(text.contains("Жилой квартал")&&text.contains("Мастерские")&&text.contains("МСК"),"native board presents actual district names and Moscow source timestamps");
        var original=windows.get(M);var click=new InventoryClickEvent(view(mayor,M),InventoryType.SlotType.CONTAINER,49,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(click);check(click.isCancelled(),"native refresh inventory click is cancelled before item movement");
        later(2,()->{check(windows.get(M)!=original&&windows.get(M).getItem(4)!=null&&lore(mayor,4).contains("Дополнительного списания"),"refresh action opens another valid board with explicit no-extra-debit explanation");pendingWindowReplacement();});
    }
    void pendingWindowReplacement()throws Exception{
        var home=town.getHomeBlock();town.setHomeBlock(TownyUniverse.getInstance().getTownBlock(new WorldCoord(world.getName(),1,0)));
        populationPlugin.getCommand("townypopulation").execute(mayor,"population",new String[]{"cohorts"});check(windows.get(M).getItem(22)!=null&&windows.get(M).getItem(22).getType()==Material.CLOCK,"changed district home gives a real pending source and schedules bounded menu retry");
        var sentinel=Bukkit.createInventory(null,9);mayor.openInventory(sentinel);
        later(12,()->{town.setHomeBlock(home);town.save();check(windows.get(M)==sentinel,"pending cohort refresh never reopens a replaced player inventory");ready(80,r->in(()->{populationPlugin.getCommand("townypopulation").execute(mayor,"population",new String[]{"cohorts"});remainingMenuChecks();}));});
    }
    void remainingMenuChecks()throws Exception{
        var denied=limitedActor(P,"CohortVisitor",false);populationPlugin.getCommand("townypopulation").execute(denied,"population",new String[]{"cohorts",town.getName()});check(!windows.containsKey(P),"visitor without admin permission cannot inspect another town through command");
        var noUse=limitedActor(M,"CohortMayor",true);var before=windows.get(M);populationPlugin.getCommand("townypopulation").execute(noUse,"population",new String[]{"cohorts"});check(windows.get(M)==before,"revoked use permission cannot open another cohorts view");
        check(Arrays.equals(populationFile,Files.readAllBytes(populationPlugin.getDataFolder().toPath().resolve("population-data.yml")))&&Arrays.equals(districtFile,Files.readAllBytes(districtsPlugin.getDataFolder().toPath().resolve("districts-data.yml"))),"repeated viewing leaves population and district files byte-for-byte unchanged");
        populationPlugin.getConfig().set("cohorts.enabled",false);population.refreshCohorts(T);check(Boolean.FALSE.equals(population.cohorts(T).get("available"))&&population.population(T).orElseThrow().population()==37,"optional cohorts disable preserves confirmed residents");populationPlugin.getConfig().set("cohorts.enabled",true);
        districtsPlugin.getConfig().set("assessment.enabled",false);population.refreshCohorts(T);check(Boolean.FALSE.equals(population.cohorts(T).get("available"))&&population.population(T).orElseThrow().population()==37&&!population.population(T).orElseThrow().paused(),"missing optional district assessment affects only cohorts not existing simulation");districtsPlugin.getConfig().set("assessment.enabled",true);
        ready(80,r->in(()->{check(((Number)totals(r).get("population")).intValue()==37,"restored optional source reconstructs same population totals");Files.writeString(proof.resolve("expected-totals.txt"),new TreeMap<>(totals(r)).toString());finishCohorts();}));
    }
    void resumeCohorts(Map<String,Object> report)throws Exception{check(town!=null&&districts.list(T).size()==2&&buildings.town(T).level("tavern_inn")==5,"actual districts and completed residential fixture survive full restart");conserved(report);check(new TreeMap<>(totals(report)).toString().equals(Files.readString(proof.resolve("expected-totals.txt"))),"full restart reconstructs exact previous cohort totals without new demand");finishCohorts();}
    org.bukkit.entity.Player limitedActor(UUID id,String name,boolean denyUse){var base=actor(id,name);var handler=java.lang.reflect.Proxy.getInvocationHandler(base);return(org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(org.bukkit.entity.Player.class.getClassLoader(),new Class<?>[]{org.bukkit.entity.Player.class},(proxy,method,args)->{if(method.getName().equals("hasPermission")){if(args[0].equals("neverlandtownypopulation.admin"))return false;if(denyUse&&args[0].equals("neverlandtownypopulation.use"))return false;}return handler.invoke(base,method,args);});}
    void finishCohorts()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; real Folia Towny Builds Districts Population, exact population and decimal totals, physical district assignment, async cache, native menu, neutral optional toggles and full restart; explicit completed-site fixtures and offline Player InventoryView EntityScheduler; no client or bank transaction claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
