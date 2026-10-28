package ru.neverland.runtime;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import io.papermc.paper.threadedregions.scheduler.*;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townypower.api.*;
import ru.neverland.townypower.service.PowerService;
import ru.neverland.townypower.data.PowerRepository;
import ru.neverland.townypower.model.*;
import ru.neverland.townypower.config.PowerSettings;
import ru.neverland.townypower.gui.PowerMenu;
import ru.neverland.townyresources.service.ResourcesService;
import ru.neverland.townyresources.api.TownyResourcesApi;
import ru.neverland.townyresources.model.Resource;

/** Actual Folia/Builds/Power/Control lifecycle; registered-site and offline player fixtures, no GUI client. */
public final class PowerStorageProbe extends TntRuntimeProbe {
    JavaPlugin owner;PowerService service;PowerRepository repository;DataStore data;Player mayor,resident;Inventory top;List<String> messages=new ArrayList<>();
    ResourcesService resources;
    @SuppressWarnings("unchecked") Player actor(UUID id,String name){
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(p,m,a)->switch(m.getName()){
            case "runDelayed"->Bukkit.getRegionScheduler().runDelayed(this,origin,(Consumer<ScheduledTask>)a[1],(Long)a[3]);
            case "run"->Bukkit.getRegionScheduler().run(this,origin,(Consumer<ScheduledTask>)a[1]);default->throw new UnsupportedOperationException(m.getName());});
        return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->name;case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp"->false;case "getScheduler"->scheduler;
            case "hasPermission"->a[0].equals("neverlandtownypower.use");case "sendMessage"->{if(a[0] instanceof String s)messages.add(s);yield null;}
            case "openInventory"->{top=(Inventory)a[0];yield null;}case "getOpenInventory"->Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,n,b)->n.getName().equals("getTopInventory")?top:null);
            case "hashCode"->id.hashCode();case "equals"->p==a[0];default->null;});
    }
    void waitFor(BooleanSupplier ready,Work next,int left)throws Exception{if(ready.getAsBoolean()){next.run();return;}if(left<=0)throw new AssertionError("power native wait timed out");later(1,()->waitFor(ready,next,left-1));}
    void site(String id,int level,int x,int z){var town=data.town(DEF);town.setConstructionSite(new ConstructionSite(id,world.getUID(),x,160,z,BlockFace.NORTH,level,level,level+1,false,6));town.setLevel(id,level);data.markDirty();}
    void command(Player p,String...args){owner.getCommand("townypower").execute(p,"townypower",args);}
    long charge(){return((Number)service.energy(DEF).get("charge")).longValue();}
    @SuppressWarnings("unchecked") boolean freshPower(){try{var w=((Map<UUID,PowerWindow>)field(service,"windows")).get(DEF);return w!=null&&w.expires()-System.nanoTime()>200_000_000L;}catch(Exception error){throw new java.util.concurrent.CompletionException(error);}}
    @Override void setup()throws Exception{
        owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyPower");service=(PowerService)Bukkit.getServicesManager().load(TownyPowerApi.class);repository=(PowerRepository)field(service,"repository");data=(DataStore)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds"),"dataStore");
        check(owner.isEnabled()&&service!=null&&owner.getDescription().getVersion().equals("0.2.0"),"actual Power 0.2 and Builds services enabled");
        resources=(ResourcesService)Bukkit.getServicesManager().load(TownyResourcesApi.class);check(resources!=null,"actual Resources service enabled for paid-time production integration");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("PowerStorageCity",DEF);var town=TownyAPI.getInstance().getTown(DEF);for(var e:Map.of(MAYOR,"PowerMayor",ACTOR,"PowerMember").entrySet()){var r=u.getDataSource().newResident(e.getValue(),e.getKey());r.setTown(town);if(e.getKey().equals(MAYOR))town.setMayor(r);r.save();}for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){var c=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(c);c.setTown(town);if(x==0&&z==0)town.setHomeBlock(c);c.save();}town.setSpawn(origin);town.save();site("generator",3,0,0);data.saveOrThrow();}
        mayor=actor(MAYOR,"PowerMayor");resident=actor(ACTOR,"PowerMember");
        if(restart){resume();return;}
        check(charge()==0,"new city starts with no invented battery stock");
        waitFor(()->charge()>=150,()->charged(),200);
    }
    void charged()throws Exception{
        check(charge()>0&&((Number)service.energy(DEF).get("capacity")).longValue()==30000,"live actual generator charges its configured maintained accumulator");
        long before=charge();for(int i=0;i<50;i++)service.refresh();check(charge()==before,"repeated refresh within a live period cannot credit production again");
        site("foundry",5,40,0);site("pumping_station",5,-40,0);site("research",3,0,40);data.saveOrThrow();call(resources,"refresh",new Class<?>[]{boolean.class},false);
        waitFor(()->Boolean.TRUE.equals(service.energy(DEF).get("ready"))&&((Number)service.energy(DEF).get("backupPower")).longValue()==35,()->backup(),100);
    }
    void backup()throws Exception{
        var energy=service.energy(DEF);check(((Number)energy.get("discharged")).longValue()==35&&service.powered(DEF,"foundry")&&service.powered(DEF,"research"),"real allocation reserves 35 energy before enabling all 135 demand against 100 generation");
        check(service.status(DEF,"research").contains("накопителя"),"consumer explains backup power in native status");
        new PowerMenu(owner,service).open(mayor,DEF,0,"all",null);check(top.getItem(40).getItemMeta().getLore().stream().anyMatch(s->s.contains("Накоплено")),"native network menu shows saved charge and backup budget");
        new PowerMenu(owner,service).open(mayor,DEF,0,"all","research");check(top.getItem(39).getType()==Material.REDSTONE_TORCH&&top.getItem(41).getType()==Material.LEVER,"building menu separates ordinary and emergency controls");
        command(resident,"emergency","research","0");check(repository.towns().get(DEF).emergencyPriorities().isEmpty(),"ordinary resident cannot change emergency priorities");
        command(mayor,"emergency","research","0");waitFor(()->repository.towns().get(DEF).emergencyPriorities().getOrDefault("research",100)==0,()->later(40,()->disableBackup()),100);
    }
    void disableBackup()throws Exception{
        check(repository.towns().get(DEF).emergencyPriorities().get("research")==0,"actual mayor command acknowledges durable emergency setting");
        var settings=service.settings();service.reload(new PowerSettings(settings.interval(),settings.profiles(),new Accumulator(settings.accumulator().capacities(),settings.accumulator().chargePower(),0)));
        waitFor(()->Boolean.TRUE.equals(service.energy(DEF).get("ready"))&&((Number)service.energy(DEF).get("backupPower")).longValue()==0&&service.powered(DEF,"research")&&!service.powered(DEF,"foundry"),()->later(40,()->production()),100);
    }
    void production()throws Exception{
        call(resources,"refresh",new Class<?>[]{boolean.class},true);var snapshot=resources.resources(DEF).orElseThrow();long metal=snapshot.state().income().get(Resource.METAL),stone=snapshot.state().expense().get(Resource.STONE);
        check(!snapshot.paused()&&metal>0&&metal<40000,"actual strategic cycle grants only partial metal after a short prepaid backup period");
        check(stone>0&&stone<20000&&snapshot.buildings().get("foundry").status().contains("время с питанием"),"partial production consumes proportional stone and explains earned powered time");
        call(resources,"refresh",new Class<?>[]{boolean.class},true);check(resources.resources(DEF).orElseThrow().state().income().get(Resource.METAL)==0,"second immediate resource cycle cannot reuse the previous powered delta");waitFor(()->freshPower()&&service.powered(DEF,"research")&&!service.powered(DEF,"foundry"),()->pause(),100);
    }
    void pause()throws Exception{
        check(service.powered(DEF,"pumping_station")&&service.powered(DEF,"research")&&!service.powered(DEF,"foundry"),"shortage obeys separate emergency order without minting power");
        var next=new HashMap<>(repository.towns());var state=next.get(DEF);for(String id:List.of("generator","foundry","pumping_station","research"))state=state.stop(id,true);next.put(DEF,state);
        await(repository.replaceAsync(next),unused->{try{
            var control=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"disable","Power"});
            waitFor(()->!owner.isEnabled(),()->{check(Files.readString(owner.getDataFolder().toPath().resolve("power-data.yml")).contains("schema: 2"),"controlled unload drains saved battery before plugin classloader closes");Files.writeString(proof.resolve("charge.txt"),Long.toString(repository.towns().get(DEF).charge()));control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"enable","Power"});check(!owner.isEnabled(),"Folia re-enable is deferred to full restart");finish();},150);
        }catch(Exception error){throw new java.util.concurrent.CompletionException(error);}});
    }
    void resume()throws Exception{
        long expected=Long.parseLong(Files.readString(proof.resolve("charge.txt")));check(charge()==expected&&expected>0,"confirmed battery charge survives full restart without offline generation");
        var state=repository.towns().get(DEF);check(state.stopped().containsAll(List.of("generator","foundry","pumping_station","research"))&&state.emergencyPriorities().get("research")==0,"switches and emergency priorities retain identity across restart");
        check(!service.powered(DEF,"foundry")&&!service.powered(DEF,"research"),"stopped consumers do not receive restored stale backup lease");
        later(45,()->{check(charge()==expected,"continued offline controls cannot accrue charge after restart");check(!Files.readString(owner.getDataFolder().toPath().resolve("power-data.yml")).contains("expires"),"process-local energy lease never enters persistent file");finish();});
    }
    @Override void finish()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; real Folia/Towny/Builds power allocation, native commands/menus, durable charging and emergency ordering, controlled disable and full restart; explicit registered-site and offline player/scheduler fixtures; no GUI client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
