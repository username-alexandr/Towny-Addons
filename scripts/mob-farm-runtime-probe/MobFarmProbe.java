package ru.neverland.probe;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.tasks.MobRemovalTimerTask;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.SuiteState;
import ru.neverland.townydistricts.api.TownyDistrictsApi;
import ru.neverland.townydistricts.gui.MobFarmMenu;
import ru.neverland.townydistricts.service.MobFarmService;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Real Towny persistence, event handlers and entities; player identity/menus use a strict proxy. */
public final class MobFarmProbe extends JavaPlugin {
    static final UUID TOWN=UUID.fromString("00000000-0000-0000-0000-00000000fa01"),MAYOR=UUID.fromString("00000000-0000-0000-0000-00000000fa02");
    int checks;List<String> results=new ArrayList<>();Inventory opened;boolean op=true,cancelSpawns;
    void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);results.add("PASS "+label);}
    void deny(Runnable action,String label){try{action.run();throw new AssertionError(label);}catch(IllegalStateException expected){check(true,label);}}
    Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    @Override public void onEnable(){
        if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_MOB_FARM_PROBE"))){Bukkit.getPluginManager().disablePlugin(this);return;}
        Bukkit.getPluginManager().registerEvents(new Listener(){@EventHandler(priority=EventPriority.HIGHEST) public void spawn(CreatureSpawnEvent e){if(cancelSpawns)e.setCancelled(true);}},this);
        Bukkit.getPluginManager().registerEvents(new Listener(){@EventHandler(priority=EventPriority.MONITOR) public void trace(CreatureSpawnEvent e){if(e.getEntityType()==EntityType.ZOMBIE)getLogger().info("Spawn check: "+e.getLocation()+" cancelled="+e.isCancelled());}},this);
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{var w=Bukkit.getWorlds().get(0);
            w.getChunkAtAsync(0,0).thenCompose(c->w.getChunkAtAsync(1,0)).whenComplete((c,error)->{
                if(error!=null){finish(error);return;}
                Bukkit.getRegionScheduler().execute(this,w,0,0,()->{synchronized(SuiteState.lock()){try{if(run(w))finish(null);}catch(Throwable e){finish(e);}}});
            });
        },40);
    }
    Player player(World w){return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
        case "getUniqueId"->MAYOR;case "getName"->"FarmProbeMayor";case "isOp"->op;
        case "hasPermission"->args[0].equals("neverlandtownydistricts.use");case "getWorld"->w;
        case "getLocation"->new Location(w,1,90,1);case "sendMessage","sendActionBar"->null;
        case "openInventory"->{opened=(Inventory)args[0];yield null;}case "isOnline"->true;
        case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];case "toString"->"FarmProbePlayer";
        default->throw new UnsupportedOperationException("Unexpected Player call "+method);
    });}
    boolean run(World w)throws Exception{
        var owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyDistricts");check(owner!=null&&owner.isEnabled(),"Districts enabled on Folia");
        var farms=(MobFarmService)field(owner,"farms");var api=Bukkit.getServicesManager().load(TownyDistrictsApi.class);
        var u=TownyUniverse.getInstance();var tw=TownyAPI.getInstance().getTownyWorld(w);
        if(Files.exists(getDataFolder().toPath().resolve("phase1"))){
            var b=farms.block(w.getUID(),0,0);var next=farms.block(w.getUID(),1,0);
            check(b!=null&&b.getPermissions().mobs&&b.isChanged(),"local farm flag survives Towny restart");
            check(!next.getPermissions().mobs,"neighbor protection survives restart");
            check(!b.getPermissions().pvp&&!b.getPermissions().fire&&!b.getPermissions().explosion,"other protection flags survive restart");
            check(api.mobFarmPlots(TOWN).size()==1&&api.mobProtectionAt(w.getUID(),1,1).orElseThrow().protectionDisabled(),"map API reports persisted zone");
            check(api.mobProtectionAt(w.getUID(),17,1).orElseThrow().status().equals("PROTECTED"),"map API reports protected neighbor");return true;
        }
        tw.setUsingTowny(true);tw.setWorldMobs(true);tw.setForceTownMobs(false);tw.save();
        check(u.getTown(TOWN)==null,"fresh disposable town");u.newTownInternal("FarmProbeTown",TOWN);var town=u.getTown(TOWN);
        var resident=u.getDataSource().newResident("FarmProbeMayor",MAYOR);resident.setTown(town);town.setMayor(resident);
        for(int x=0;x<=1;x++){var b=new TownBlock(x,0,tw);u.addTownBlock(b);b.setTown(town);b.getPermissions().mobs=false;
            b.getPermissions().pvp=false;b.getPermissions().fire=false;b.getPermissions().explosion=false;b.setChanged(true);if(x==0)town.setHomeBlock(b);b.save();}
        town.setSpawn(new Location(w,1,90,1));resident.save();town.save();
        w.addPluginChunkTicket(0,0,this);w.addPluginChunkTicket(1,0,this);
        var p=player(w);var b=farms.block(w.getUID(),0,0);var neighbor=farms.block(w.getUID(),1,0);
        check(api.mobProtectionAt(w.getUID(),1,1).orElseThrow().status().equals("PROTECTED"),"initial protected state");
        String before=b.getPermissions().toString();var c=farms.prepare(p,b,true);farms.apply(p,c);
        check(b.getPermissions().mobs&&!neighbor.getPermissions().mobs,"only selected plot allows mobs");
        var clone=new TownyPermission();clone.load(b.getPermissions().toString());clone.mobs=false;
        check(before.equals(clone.toString()),"build destroy switch item-use PvP fire explosion flags preserved");
        deny(()->farms.apply(p,c),"stale repeat cannot toggle flag back");
        var protectedEntity=w.spawnEntity(new Location(w,17,90,1),EntityType.ZOMBIE,CreatureSpawnEvent.SpawnReason.NATURAL,e->{});
        check(!protectedEntity.isInWorld(),"real Towny rejects natural zombie in protected adjacent plot");
        var entity=w.spawnEntity(new Location(w,1,90,1),EntityType.ZOMBIE,CreatureSpawnEvent.SpawnReason.NATURAL,e->((LivingEntity)e).setRemoveWhenFarAway(false));
        Bukkit.getRegionScheduler().runDelayed(this,new Location(w,1,90,1),t->{synchronized(SuiteState.lock()){try{continueFirst(w,entity);finish(null);}catch(Throwable error){finish(error);}}},10);
        return false;
    }
    void continueFirst(World w,Entity entity)throws Exception{
        var owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyDistricts");var farms=(MobFarmService)field(owner,"farms");
        var api=Bukkit.getServicesManager().load(TownyDistrictsApi.class);var u=TownyUniverse.getInstance();var tw=TownyAPI.getInstance().getTownyWorld(w);
        var town=u.getTown(TOWN);var resident=TownyAPI.getInstance().getResident(MAYOR);var p=player(w);var b=farms.block(w.getUID(),0,0);var neighbor=farms.block(w.getUID(),1,0);
        getLogger().info("Farm spawn: valid="+entity.isValid()+" inWorld="+entity.isInWorld()+" dead="+entity.isDead()+" difficulty="+w.getDifficulty()+" allowMonsters="+w.getAllowMonsters()+" rule="+TownyAPI.getInstance().getTownBlock(entity.getLocation()).getPermissions().mobs);
        check(entity.isValid(),"real Towny allows natural zombie in farm plot");
        MobRemovalTimerTask.checkEntity((Towny)Bukkit.getPluginManager().getPlugin("Towny"),tw,entity);
        check(entity.isValid(),"Towny periodic removal preserves farm mob");
        cancelSpawns=true;var denied=w.spawnEntity(new Location(w,2,90,1),EntityType.ZOMBIE,CreatureSpawnEvent.SpawnReason.NATURAL,e->{});cancelSpawns=false;
        check(!denied.isInWorld(),"another plugin's spawn cancellation remains effective");
        farms.apply(p,farms.prepare(p,b,false));MobRemovalTimerTask.checkEntity((Towny)Bukkit.getPluginManager().getPlugin("Towny"),tw,entity);
        check(!entity.isValid()&&!b.getPermissions().mobs,"re-enabled protection removes farm mob through native Towny");
        tw.setWorldMobs(false);deny(()->farms.prepare(p,b,true),"world ban cannot be overridden");
        check(api.mobProtectionAt(w.getUID(),1,1).orElseThrow().status().equals("WORLD_RESTRICTED"),"world ban visible in API");tw.setWorldMobs(true);
        tw.setForceTownMobs(true);deny(()->farms.prepare(p,b,false),"forced world allowance cannot be locally overridden");tw.setForceTownMobs(false);
        var stale=farms.prepare(p,b,true);b.getPermissions().fire=true;deny(()->farms.apply(p,stale),"changed permissions invalidate confirmation");b.getPermissions().fire=false;
        var foreign=new Town("FarmOther",UUID.randomUUID());b.setTown(foreign);deny(()->farms.apply(p,stale),"changed town invalidates confirmation");b.setTown(town);
        op=false;check(farms.manager(p,town),"mayor allowed without OP");
        var citizen=u.getDataSource().newResident("FarmCitizen",UUID.randomUUID());citizen.setTown(town);town.setMayor(citizen);
        deny(()->farms.prepare(p,b,true),"ordinary resident cannot change farm settings");town.setMayor(resident);op=true;
        farms.apply(p,farms.prepare(p,b,true));
        var menu=(MobFarmMenu)field(owner,"farmMenu");menu.open(p,false,0);
        check(opened.getItem(22).getType()==Material.ORANGE_STAINED_GLASS_PANE,"map marks current farm orange");
        check(opened.getItem(23).getType()==Material.LIME_STAINED_GLASS_PANE,"map marks neighboring protected chunk green");
        menu.open(p,true,0);check(opened.getItem(0).getType()==Material.ORANGE_STAINED_GLASS_PANE&&opened.getItem(1)==null,"farm list shows allowed zone only");
        check(api.mobFarmPlots(TOWN).size()==1,"future client map API contains one explicit farm");
        b.save();neighbor.save();tw.save();resident.save();citizen.save();town.save();u.getDataSource().saveAll();u.getDataSource().saveQueues();
        getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("phase1"),"ready");
    }
    void finish(Throwable error){try{getDataFolder().mkdirs();if(error!=null){getLogger().log(java.util.logging.Level.SEVERE,"Mob farm probe failed",error);results.add("ERROR "+error);}
        results.add("checks="+checks);results.add("failed="+(error==null?0:1));Files.writeString(getDataFolder().toPath().resolve("result.txt"),String.join("\n",results)+"\n");
    }catch(Exception ex){getLogger().severe(ex.toString());}}
}
