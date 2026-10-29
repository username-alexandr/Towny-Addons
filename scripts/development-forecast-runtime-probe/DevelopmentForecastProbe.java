package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townyupkeep.api.TownyUpkeepApi;
import ru.neverland.townyupkeep.service.UpkeepService;
import ru.neverland.townyupkeep.data.UpkeepRepository;
import ru.neverland.townyupkeep.model.Cost;
import ru.neverland.townyupkeep.model.Entry;
import ru.neverland.townyupkeep.model.Entry.*;
import ru.neverland.townypower.api.TownyPowerApi;
import ru.neverland.townypower.service.PowerService;
import ru.neverland.townypower.data.PowerRepository;
import ru.neverland.townypower.model.TownPowerState;
import ru.neverland.townypolicies.api.TownyPoliciesApi;
import ru.neverland.townypolicies.service.PoliciesService;

/** Real providers and Towny claims; registered sites and a pending invoice are explicit fixtures. */
public final class DevelopmentForecastProbe extends JavaPlugin {
    static final UUID T=UUID.nameUUIDFromBytes("development-town".getBytes()),M=UUID.nameUUIDFromBytes("development-mayor".getBytes());
    final Map<String,Integer> plan=Map.of("generator",4,"foundry",4);Path proof;World world;Location origin;boolean restart;int checks;UpkeepService upkeep;PowerService power;UpkeepRepository bills;PowerRepository batteries;DataStore builds;PoliciesService policies;
    interface Work{void run()throws Exception;}
    Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    void check(boolean value,String why)throws Exception{if(!value)throw new AssertionError(why);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),"PASS "+why+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+why);}
    void later(long ticks,Work work){Bukkit.getRegionScheduler().runDelayed(this,origin,t->{if(!SuiteState.tryRun("Development forecast native probe",()->{try{work.run();}catch(Throwable e){fail(e);}}))later(1,work);},ticks);}
    void waitFor(BooleanSupplier condition,Work work,int left)throws Exception{if(condition.getAsBoolean()){work.run();return;}if(left<=0)throw new AssertionError("Forecast provider timeout");later(1,()->waitFor(condition,work,left-1));}
    void await(CompletionStage<?> future,Work work){future.whenComplete((v,error)->{if(error!=null)fail(error);else later(1,work);});}
    void fail(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"DEVELOPMENT PROBE FAILED",error);try{Files.writeString(proof.resolve("failed.txt"),error.toString());}catch(Exception ignored){}Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
    @Override public void onEnable(){if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}proof=getDataFolder().toPath();Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));world=Bukkit.getWorlds().getFirst();origin=new Location(world,8,140,8);world.getChunkAtAsync(0,0).whenComplete((c,e)->{if(e!=null)fail(e);else later(2,this::setup);});}catch(Throwable e){fail(e);}},40);}
    boolean known(Map<String,Object> result){return Boolean.TRUE.equals(result.get("known"));}
    long n(Map<String,Object> result,String part,String key){return((Number)((Map<?,?>)result.get(part)).get(key)).longValue();}
    void site(String project,int x){var town=builds.town(T);town.setConstructionSite(new ConstructionSite(project,world.getUID(),x,160,0,BlockFace.NORTH,3,3,4,false,6));town.setLevel(project,3);builds.markDirty();}
    void setup()throws Exception{
        upkeep=(UpkeepService)Bukkit.getServicesManager().load(TownyUpkeepApi.class);power=(PowerService)Bukkit.getServicesManager().load(TownyPowerApi.class);policies=(PoliciesService)Bukkit.getServicesManager().load(TownyPoliciesApi.class);bills=(UpkeepRepository)field(upkeep,"repo");batteries=(PowerRepository)field(power,"repository");builds=(DataStore)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds"),"dataStore");
        check(upkeep.capabilities().contains("development")&&power.capabilities().contains("development"),"native providers advertise additive v1 development capability");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("ForecastCity",T);var town=TownyAPI.getInstance().getTown(T);var r=u.getDataSource().newResident("ForecastMayor",M);r.setTown(town);town.setMayor(r);r.save();for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){var block=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(block);block.setTown(town);if(x==0&&z==0)town.setHomeBlock(block);block.save();}town.setSpawn(origin);town.save();site("generator",0);site("foundry",40);builds.saveOrThrow();}
        policies.refresh();waitFor(()->known(upkeep.development(T,plan))&&known(power.development(T,plan))&&upkeep.buildings(T).size()==2,()->{((BukkitTask)field(upkeep,"task")).cancel();((BukkitTask)field(power,"task")).cancel();waitFor(()->!batteries.busy(),restart?this::resume:this::first,100);},200);
    }
    void first()throws Exception{
        var u=upkeep.development(T,plan);var p=power.development(T,plan);check(n(u,"before","cents")==21600&&n(u,"after","cents")==32400&&n(u,"delta","cents")==10800,"actual policies and size factor reprice old and planned buildings together");check(((Map<?,?>)((Map<?,?>)u.get("after")).get("resources")).get("wood").equals(32400L),"real forecast retains exact resource thousandths");check(u.get("periodSeconds").equals(7200)&&p.get("periodSeconds").equals(1),"provider periods come from installed custom configurations");check(policies.upkeepMultiplier(T,"generator")==1.5,"forecast uses genuine Policies provider rather than a substituted multiplier");
        check(n(p,"before","generation")==100&&n(p,"before","demand")==20&&n(p,"after","generation")==200&&n(p,"after","demand")==50,"native Builds footprints feed current and final power levels");var future=power.development(T,Map.of("water_wheel",3));check(((List<?>)future.get("conditionalProjects")).contains("water_wheel")&&((List<?>)future.get("assumptions")).size()>=3,"new unplaced generator is explicitly conditional, never a power promise");
        check(!known(upkeep.development(T,Map.of("generator",6)))&&!known(power.development(T,Map.of("generator",6))),"both native APIs refuse unsupported levels without rounding");check(!known(upkeep.development(UUID.randomUUID(),plan))&&!known(power.development(UUID.randomUUID(),plan)),"unknown city is unavailable rather than free or zero-powered");
        var foreign=UUID.nameUUIDFromBytes("forecast-foreign".getBytes());TownyUniverse.getInstance().newTownInternal("ForecastForeign",foreign);var other=TownyAPI.getInstance().getTown(foreign);var resident=TownyUniverse.getInstance().getDataSource().newResident("ForecastOther",UUID.nameUUIDFromBytes("forecast-other".getBytes()));resident.setTown(other);other.setMayor(resident);resident.save();var home=new TownBlock(1000,1000,TownyUniverse.getInstance().getWorld(world.getName()));TownyUniverse.getInstance().addTownBlock(home);home.setTown(other);other.setHomeBlock(home);other.setSpawn(new Location(world,16008,140,16008));home.save();other.save();var chunk=TownyAPI.getInstance().getTownBlock(new Location(world,8,160,8));chunk.setTown(TownyAPI.getInstance().getTown(foreign));try{var blocked=upkeep.development(T,plan);check(n(blocked,"after","cents")==16800&&((List<?>)blocked.get("buildings")).stream().anyMatch(row->((Map<?,?>)row).get("siteState").equals("EXISTING_FOREIGN")),"foreign existing footprint is excluded and visibly distinguished from a new site");check(n(power.development(T,plan),"after","generation")==0,"power forecast cannot assume acquisition of an existing foreign site");}finally{chunk.setTown(TownyAPI.getInstance().getTown(T));}
        var state=batteries.towns().getOrDefault(T,TownPowerState.empty()).charge(12345);await(batteries.replaceAsync(Map.of(T,state)),this::readOnly);
    }
    void readOnly()throws Exception{
        var entry=bills.entries().get(new Key(T,"foundry"));var invoice=new Invoice(UUID.randomUUID(),new Cost(1234,Map.of("wood",4321L)),7200,Phase.MONEY_PENDING);upkeep.put(new Key(T,"foundry"),new Entry(false,entry.due(),"Explicit pending receipt fixture",invoice));
        var oldBills=bills.entries();var oldClock=bills.clock();var oldBatteries=batteries.towns();var originalBuild=builds.town(T).level("generator");for(int i=0;i<100;i++){upkeep.development(T,plan);power.development(T,plan);}
        check(bills.entries().equals(oldBills)&&bills.clock()==oldClock&&batteries.towns().equals(oldBatteries)&&builds.town(T).level("generator")==originalBuild,"100 real API forecasts change no invoice, paid clock, charge or building level");var view=upkeep.development(T,plan);var pending=(List<?>)view.get("existingInvoices");check(pending.size()==1&&((Map<?,?>)pending.getFirst()).get("id").equals(invoice.id().toString())&&((Map<?,?>)pending.getFirst()).get("cents").equals(1234L),"existing pending invoice is disclosed unchanged beside future rates");
        upkeep.suspend(T,"generator",true,M);var paused=upkeep.development(T,plan);check(n(paused,"before","cents")==14400&&n(paused,"after","cents")==21600,"real city suspension excludes future charges while retaining size contribution");check(n(power.development(T,plan),"after","generation")==0,"actual unpaid or suspended existing generator is not promised power after upgrade");
        SuiteState.freeze("NeverLandTownyPolicies");try{var unavailable=upkeep.development(T,plan);check(!known(unavailable)&&!unavailable.containsKey("after"),"paused Policies cannot be silently presented as neutral known costs");}finally{SuiteState.thaw("NeverLandTownyPolicies");}
        SuiteState.freeze("NeverLandTownyUpkeep");try{check(!known(upkeep.development(T,plan))&&!known(power.development(T,plan)),"paused upkeep makes both linked forecasts explicitly unavailable");}finally{SuiteState.thaw("NeverLandTownyUpkeep");}
        SuiteState.freeze("NeverLandTownyPower");try{check(!known(power.development(T,plan)),"paused power is never shown as a zero deficit guarantee");}finally{SuiteState.thaw("NeverLandTownyPower");}
        Files.writeString(proof.resolve("invoice.txt"),invoice.id()+"\n"+entry.due()+"\n");finish();
    }
    void resume()throws Exception{
        var expected=Files.readAllLines(proof.resolve("invoice.txt"));var invoice=bills.entries().get(new Key(T,"foundry")).invoice();check(invoice!=null&&invoice.id().toString().equals(expected.getFirst())&&invoice.phase()==Phase.MONEY_PENDING&&invoice.cost().money()==1234,"restart keeps exact pending invoice, without spending through forecast");check(batteries.towns().get(T).charge()==12345,"restart retains exact existing charge with no forecast accrual");check(upkeep.paused(T,"generator")&&n(upkeep.development(T,plan),"after","cents")==21600,"saved suspension remains reflected in development rate");var before=bills.entries();var energy=batteries.towns();for(int i=0;i<50;i++){upkeep.development(T,plan);power.development(T,plan);}check(before.equals(bills.entries())&&energy.equals(batteries.towns()),"restarted provider forecasts remain read-only");finish();
    }
    void finish()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" checks; actual Folia/Towny/Builds/Upkeep/Power/Policies API; explicit registered sites, custom profiles and pending invoice; no client, bank payment or future connection claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
