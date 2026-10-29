package ru.neverland.runtime;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import com.palmergames.bukkit.towny.*;
import ru.neverland.core.SuiteState;
import ru.neverland.townybuilds.api.BuildingPlanningApi;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.model.*;
import ru.neverland.townybuilds.service.DefinitionRegistry;

/** Native public API + configured catalog + actual ItemStack codec. No payment/client claim. */
public final class BuildPlanningProbe extends JavaPlugin {
    static final UUID TOWN=UUID.fromString("b0000000-0000-0000-0000-000000000001"),MAYOR=UUID.fromString("b0000000-0000-0000-0000-000000000002");
    Path proof;boolean restart;int checks;DataStore data;DefinitionRegistry definitions;BuildingPlanningApi api;World world;
    interface Work{void run()throws Exception;}
    void check(boolean valid,String message)throws Exception{if(!valid)throw new AssertionError(message);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),"PASS "+message+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+message);}
    void own(Work work){Bukkit.getRegionScheduler().runDelayed(this,world,0,0,t->{if(!SuiteState.tryRun("planning probe",()->{try{work.run();}catch(Throwable ex){fail(ex);}}))own(work);},1);}
    void fail(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"BUILD PLANNING PROBE FAILED",ex);try{Files.writeString(proof.resolve("failed.txt"),ex.toString());}catch(Exception ignored){}Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
    static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    @Override public void onEnable(){if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||!"127.0.0.1".equals(getServer().getIp()))throw new IllegalStateException("Disposable loopback fixture required");proof=getDataFolder().toPath();
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));world=Bukkit.getWorlds().getFirst();world.getChunkAtAsync(0,0).whenComplete((v,e)->{if(e!=null)fail(e);else own(this::setup);});}catch(Throwable ex){fail(ex);}},30);
    }
    void setup()throws Exception{
        var builds=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");check(builds!=null&&builds.isEnabled(),"actual Builds enabled");data=(DataStore)field(builds,"dataStore");definitions=(DefinitionRegistry)field(builds,"definitions");api=Bukkit.getServicesManager().load(BuildingPlanningApi.class);check(api!=null,"public read-only planning API registered");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("PlanningTown",TOWN);var town=TownyAPI.getInstance().getTown(TOWN);var mayor=u.getDataSource().newResident("PlanningMayor",MAYOR);mayor.setTown(town);mayor.save();town.setMayor(mayor);town.save();
            create("plan_hall",100,272,Map.of());create("plan_farm",200,120,Map.of("plan_hall",1));create("plan_mill",300,80,Map.of("plan_hall",1));definitions.saveCustomProjects();
            var state=data.town(TOWN);state.resourceFund("plan_hall",1).add(new ItemStack(Material.OAK_LOG),64);var stock=new ItemStack[54];stock[0]=new ItemStack(Material.OAK_LOG,64);stock[1]=new ItemStack(Material.OAK_LOG,32);state.setStorage(stock,54);data.markDirty();data.saveOrThrow();
        }
        codec();
        int towns=data.towns().size();var catalog=api.development(TOWN,Map.of());var result=api.development(TOWN,Map.of("plan_farm",1,"plan_mill",1));var rejected=api.development(TOWN,Map.of("plan_hall",1));check(rejected.isCompletedExceptionally(),"bounded request capacity refuses third concurrent estimate without waiting");
        CompletableFuture.allOf(catalog,result).whenComplete((v,error)->{if(error!=null){fail(error);return;}try{check(!SuiteState.held(),"future completion runs outside shared Suite state");}catch(Exception ex){fail(ex);return;}own(()->{
            var summary=result.join();check(((List<?>)catalog.join().get("catalog")).size()==definitions.all().size(),"catalog reflects live definitions including custom buildings");check(((Number)summary.get("cents")).longValue()==60000,"real configured prices total exactly once");check(((List<?>)summary.get("steps")).size()==3,"shared prerequisite appears once in dependency order");
            var supply=(Map<?,?>)((List<?>)summary.get("supply")).getFirst();check(((Number)supply.get("required")).longValue()==472,"272-item source requirement is encoded with a unit sample");check(((Number)supply.get("donated")).longValue()==64,"acknowledged project fund counted once");check(((Number)supply.get("stored")).longValue()==96,"one physical warehouse stock counted once across projects");check(((Number)supply.get("missing")).longValue()==312,"remaining quantity conserves total funds and stock");
            check(data.existingTown(TOWN).existingResourceFund("plan_hall").count(new ItemStack(Material.OAK_LOG))==64,"estimate does not spend project donations");check(data.existingTown(TOWN).storage()[0].getAmount()==64&&data.existingTown(TOWN).storage()[1].getAmount()==32,"estimate does not spend warehouse items");check(data.existingTown(TOWN).level("plan_hall")==0&&data.towns().size()==towns,"estimate does not complete buildings or create town records");
            api.development(UUID.randomUUID(),Map.of()).whenComplete((unknown,failed)->own(()->{check(failed!=null,"unknown town is rejected");check(data.towns().size()==towns,"unknown-town read leaves persistence unchanged");cycle();}));
        });});
    }
    void create(String id,double money,int count,Map<String,Integer> requires){var p=definitions.create(id,ProjectType.BUILDING);p.setName(id);p.setRequirements(requires);p.setLevel(new LevelDefinition(1,money,1,List.of(new ItemStack(Material.OAK_LOG,count)),Map.of(),List.of()));}
    void codec()throws Exception{
        var sample=new ItemStack(Material.OAK_LOG,272);var meta=sample.getItemMeta();meta.setDisplayName("Planning signed sample");sample.setItemMeta(meta);
        String encoded=ru.neverland.townybuilds.util.ItemCodec.encodeSingle(sample);var decoded=ru.neverland.townybuilds.util.ItemCodec.decodeSingle(encoded);
        check(encoded.startsWith("nlt-counted-v1:272:")&&decoded.getAmount()==272&&decoded.isSimilar(sample),"counted codec preserves 272 items and custom metadata without oversized native stack");
        var legacy=new ItemStack(Material.OAK_LOG,80);String old=ru.neverland.townybuilds.util.ItemCodec.encodeSingle(legacy);check(!old.startsWith("nlt-counted")&&ru.neverland.townybuilds.util.ItemCodec.decodeSingle(old).equals(legacy),"old native base64 format remains readable unchanged");
        for(String bad:List.of("nlt-counted-v1:0:AA==","nlt-counted-v1:1000000001:AA==","nlt-counted-v1:272:"+old)){
            boolean refused=false;try{ru.neverland.townybuilds.util.ItemCodec.decodeSingle(bad);}catch(java.io.IOException expected){refused=true;}check(refused,"invalid counted resource refuses decoding: "+bad.substring(0,Math.min(36,bad.length())));
        }
        var parse=DefinitionRegistry.class.getDeclaredMethod("parseLevel",int.class,org.bukkit.configuration.ConfigurationSection.class);parse.setAccessible(true);
        for(var bad:List.<Map<String,Object>>of(Map.of("resources-base64",List.of("nlt-counted-v1:272:invalid")),Map.of("resources",List.of("OAK_LOG:0")),Map.of("resources",List.of("MISSING_MATERIAL:16")),Map.of("resources",Map.of("OAK_LOG",16)))){
            var yaml=new org.bukkit.configuration.file.YamlConfiguration();bad.forEach(yaml::set);boolean refused=false;try{parse.invoke(definitions,1,yaml);}catch(java.lang.reflect.InvocationTargetException ex){refused=ex.getCause() instanceof IllegalArgumentException;}check(refused,"invalid resource definition blocks full level instead of omitting price: "+bad);
        }
        Path original=Path.of("plugins/NeverLandTownyBuilds/custom-projects.yml");byte[] before=Files.readAllBytes(original);var broken=new org.bukkit.configuration.file.YamlConfiguration();broken.set("plan_hall.levels.1.resources",List.of("OAK_LOG:0"));
        var load=DefinitionRegistry.class.getDeclaredMethod("loadSection",org.bukkit.configuration.ConfigurationSection.class,ProjectType.class,boolean.class);load.setAccessible(true);load.invoke(definitions,broken,ProjectType.BUILDING,true);
        check(definitions.project("plan_hall")==null,"invalid custom override cannot fall back to a cheaper inherited building");boolean blocked=false;try{definitions.saveCustomProjects();}catch(IllegalStateException expected){blocked=true;}
        check(blocked&&Arrays.equals(before,Files.readAllBytes(original)),"invalid custom catalog forbids overwriting the original price file");definitions.reload();check(definitions.project("plan_hall").levels().get(1).resources().getFirst().getAmount()==272,"healthy reload restores the original counted price after rejected edit");
    }
    void cycle()throws Exception{data.existingTown(TOWN).setLevel("plan_hall",1);definitions.project("plan_hall").setRequirements(Map.of("removed_old_dependency",1));api.development(TOWN,Map.of("plan_farm",1)).whenComplete((v,error)->own(()->{check(error==null&&((List<?>)v.get("steps")).size()==1,"satisfied building does not expand its obsolete historical dependencies");data.existingTown(TOWN).setLevel("plan_hall",0);definitions.project("plan_hall").setRequirements(Map.of());cyclic();}));}
    void cyclic()throws Exception{definitions.project("plan_hall").setRequirements(Map.of("plan_farm",1));api.development(TOWN,Map.of("plan_farm",1)).whenComplete((v,error)->own(()->{check(error!=null,"cyclic dependency refuses estimate without partial quote");definitions.project("plan_hall").setRequirements(Map.of());data.markDirty();data.saveOrThrow();TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" checks; native Folia read-only planning API, actual configured prices and ItemStack serialization, persisted funds and warehouse; no bank operation, construction action or network client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}));}
}
