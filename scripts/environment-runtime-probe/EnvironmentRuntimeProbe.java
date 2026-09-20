package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townyenvironment.api.TownyEnvironmentApi;
import ru.neverland.townyenvironment.service.EnvironmentService;
import ru.neverland.townyenvironment.data.EnvironmentRepository;
import ru.neverland.townyenvironment.model.EnvironmentState;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townypopulation.api.TownyPopulationApi;
import ru.neverland.townypopulation.service.PopulationService;
import ru.neverland.townyresources.api.*;
import ru.neverland.townyresources.service.ResourcesService;
import ru.neverland.townyresources.model.Resource;
import ru.neverland.townyupkeep.api.TownyUpkeepApi;
import ru.neverland.townypower.api.TownyPowerApi;
import ru.neverland.townyspecialization.api.TownySpecializationApi;

/** Disposable native integration fixture. Controlled provider data and explicit scheduler pulses. */
public final class EnvironmentRuntimeProbe extends JavaPlugin {
    static final UUID T=UUID.nameUUIDFromBytes("environment-city-040".getBytes()), M=UUID.nameUUIDFromBytes("environment-mayor-040".getBytes());
    Town town; World world; EnvironmentService ecology; ResourcesService resources; PopulationService population;
    EnvironmentRepository repository; int checks; boolean outage, parkPowered=true, foreign; Path proof; Actor mayor;
    JavaPlugin plugin(String name){return (JavaPlugin)Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(name));}
    void check(boolean value,String label)throws Exception{if(!value)throw new AssertionError(label);checks++;Files.writeString(proof.resolve("checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    void near(double actual,double expected,String label)throws Exception{check(Math.abs(actual-expected)<1e-8,label+" ("+actual+")");}
    interface Step{void run()throws Exception;}
    void rejected(Step step,String label)throws Exception{try{step.run();}catch(Exception expected){check(true,label);return;}throw new AssertionError(label);}
    @Override public void onEnable(){
        if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}
        Bukkit.getScheduler().runTaskLater(this,()->guard(this::run),40);
    }
    void guard(Step action){try{action.run();}catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"ENVIRONMENT PROBE FAILED",ex);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("failed.txt"),ex.toString());}catch(Exception ignored){}Bukkit.shutdown();}}
    void refreshResources()throws Exception{var m=ResourcesService.class.getDeclaredMethod("refresh",boolean.class);m.setAccessible(true);m.invoke(resources,false);}
    void pulse()throws Exception{var m=EnvironmentService.class.getDeclaredMethod("refresh",boolean.class);m.setAccessible(true);m.invoke(ecology,true);}
    void run()throws Exception{
        proof=getDataFolder().toPath();Files.createDirectories(proof);world=Bukkit.getWorlds().getFirst();
        ecology=(EnvironmentService)Bukkit.getServicesManager().load(TownyEnvironmentApi.class);
        resources=(ResourcesService)Bukkit.getServicesManager().load(TownyResourcesApi.class);
        population=(PopulationService)Bukkit.getServicesManager().load(TownyPopulationApi.class);
        check(ecology!=null&&resources!=null&&population!=null&&plugin("NeverLandTownyControl").isEnabled(),"Environment, Resources, Population and Control load together");
        var field=EnvironmentService.class.getDeclaredField("repository");field.setAccessible(true);repository=(EnvironmentRepository)field.get(ecology);
        if(Files.exists(proof.resolve("first-passed.txt"))){restart();return;}
        var universe=TownyUniverse.getInstance();universe.newTownInternal("EnvironmentCity",T);town=TownyAPI.getInstance().getTown(T);
        var resident=universe.getDataSource().newResident("EnvironmentMayor",M);resident.setTown(town);town.setMayor(resident);resident.save();
        var block=new TownBlock(0,0,universe.getWorld(world.getName()));universe.addTownBlock(block);block.setTown(town);town.setHomeBlock(block);block.save();town.setSpawn(new Location(world,8,90,8));town.save();
        mayor=new Actor(M,"EnvironmentMayor");installProviders();population.refreshView();refreshResources();ecology.refreshView();refreshResources();population.refreshView();
        check(ecology.observation(T).ready(),"ecology resolves native provider services without startup deadlock");
        check(ecology.observation(T).levels().get("foundry")==5&&ecology.observation(T).levels().get("park")==5,"completed operational owned levels are counted");
        near(ecology.observation(T).pressure().emissions(),1.5,"industry creates pressure");near(ecology.observation(T).pressure().cleaning(),1.7,"park and natural recovery clean city");
        check(!resources.resources(T).orElseThrow().paused(),"real resource calculation remains available");
        var cleanResource=resources.resources(T).orElseThrow();long food=cleanResource.buildings().get("agrarian_complex").income().get(Resource.FOOD);
        check(food>0,"native agrarian production preview has food");double happiness=population.population(T).orElseThrow().capacity().happiness();
        repository.put(T,new EnvironmentState(70,0,false));ecology.refreshView();refreshResources();population.refreshView();
        near(ecology.happiness(T),-10,"pollution applies bounded happiness penalty");near(ecology.agricultureMultiplier(T,"agrarian_complex"),.8,"agricultural coefficient at pollution 70");
        near(ecology.agricultureMultiplier(T,"foundry"),1,"non-agricultural industry keeps output");
        near(population.population(T).orElseThrow().capacity().happiness(),happiness-10,"actual Population consumes ecology penalty");
        check(resources.resources(T).orElseThrow().buildings().get("agrarian_complex").income().get(Resource.FOOD)==food*8/10,"actual Resources reduces only future farm food");
        check(resources.resources(T).orElseThrow().state().balances().equals(cleanResource.state().balances()),"ecology cannot confiscate existing resource balances");
        ecology.control(T,"pause");near(ecology.happiness(T),0,"administrative pause removes happiness penalty");near(ecology.agricultureMultiplier(T,"agrarian_complex"),1,"administrative pause restores agriculture");
        for(int i=0;i<12;i++)pulse();check(ecology.state(T).pollution()==70&&ecology.state(T).cycles()==0,"paused pulses cause no pollution or cycle debt");
        ecology.control(T,"resume");resources.pause(T,"foundry",true);ecology.refreshView();near(ecology.observation(T).pressure().emissions(),0,"manual resource stop removes industrial emissions");
        resources.pause(T,"foundry",false);parkPowered=false;ecology.refreshView();near(ecology.observation(T).pressure().cleaning(),.2,"inactive park cannot grant cleaning");
        ecology.control(T,"restart");for(int i=0;i<6;i++)pulse();near(ecology.state(T).pollution(),71.3,"one full online cycle changes pollution exactly once");
        check(ecology.state(T).cycles()==1,"one durable ecological cycle");
        outage=true;for(int i=0;i<12;i++)pulse();check(!ecology.observation(T).ready()&&ecology.state(T).cycles()==1,"provider outage freezes online timer without backlog");
        near(ecology.happiness(T),0,"unavailable building data disables penalties");outage=false;ecology.refreshView();
        for(int i=0;i<5;i++)pulse();check(ecology.state(T).cycles()==1,"recovery waits a new full interval");pulse();check(ecology.state(T).cycles()==2,"recovery resumes ordinary cycles");
        foreign=true;ecology.refreshView();near(ecology.observation(T).pressure().emissions(),0,"unowned footprint cannot create pollution");foreign=false;ecology.refreshView();
        var command=Bukkit.getPluginCommand("townyenvironment");command.execute(mayor.player,"townyenvironment",new String[0]);check(mayor.top.getSize()==54,"native ecology menu opens");
        var click=new InventoryClickEvent(mayor.view,InventoryType.SlotType.CONTAINER,20,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);Bukkit.getPluginManager().callEvent(click);check(click.isCancelled(),"ecology GUI blocks item extraction");
        var executor=command.getExecutor();var targetMethod=executor.getClass().getMethod("adminMenuTargets",org.bukkit.command.CommandSender.class);
        rejected(()->targetMethod.invoke(executor,mayor.player),"ordinary mayor lacks administrative access");
        mayor.permissions.addAttachment(this,"neverlandtownycontrol.admin",true);mayor.permissions.recalculatePermissions();
        check(!((List<?>)targetMethod.invoke(executor,mayor.player)).isEmpty(),"root administrative permission reaches ecology controls");
        Bukkit.getPluginCommand("nltadmin").execute(mayor.player,"nltadmin",new String[]{"logs"});
        check(Arrays.stream(mayor.top.getContents()).filter(Objects::nonNull).anyMatch(i->i.hasItemMeta()&&ChatColor.stripColor(i.getItemMeta().getDisplayName()).contains("Экология")),"administrator sees separate ecology log category");
        ecology.control(T,"pause");String saved=Files.readString(plugin("NeverLandTownyEnvironment").getDataFolder().toPath().resolve("environment-data.yml"));
        Files.writeString(proof.resolve("saved-environment.yml"),saved);
        Bukkit.getPluginManager().disablePlugin(plugin("NeverLandTownyEnvironment"));near(ru.neverland.core.EnvironmentAccess.happiness(T),0,"disabled ecology has neutral happiness bridge");
        near(ru.neverland.core.EnvironmentAccess.agriculture(T,"agrarian_complex"),1,"disabled ecology has neutral food bridge");
        check(Files.readString(plugin("NeverLandTownyEnvironment").getDataFolder().toPath().resolve("environment-data.yml")).equals(saved),"disable preserves all city ecology data");
        Bukkit.getPluginCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"enable","all"});
        Files.writeString(proof.resolve("first-passed.txt"),"PASS "+checks+" native assertions\n");Bukkit.shutdown();
    }
    void restart()throws Exception{
        check(TownyAPI.getInstance().getTown(T)!=null,"city survives another JVM");check(ecology.state(T).paused(),"ecology administrative pause persists");
        check(ecology.state(T).cycles()==2,"completed cycle count persists");near(ecology.state(T).pollution(),72.6,"pollution preserved without offline catchup");
        near(ecology.happiness(T),0,"paused restart remains harmless");near(ecology.agricultureMultiplier(T,"agrarian_complex"),1,"paused restart has full agricultural efficiency");
        check(ecology.state(UUID.randomUUID()).equals(EnvironmentState.clean()),"new town UUID starts clean");
        check(Files.readString(plugin("NeverLandTownyEnvironment").getDataFolder().toPath().resolve("environment-data.yml")).equals(Files.readString(proof.resolve("saved-environment.yml"))),"restart does not rewrite saved pollution");
        Files.writeString(proof.resolve("restart-passed.txt"),"PASS "+checks+" restart assertions\n");Bukkit.shutdown();
    }
    static final Object UNHANDLED=new Object();
    <T> void provider(Class<T> type,InvocationHandler handler){
        Object original=Bukkit.getServicesManager().load(type);
        T proxy=type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            if(m.getName().equals("apiVersion")||m.getName().equals("capabilities"))return m.invoke(original,a);
            Object result=handler.invoke(p,m,a);return result==UNHANDLED?m.invoke(original,a):result;
        }));Bukkit.getServicesManager().register(type,proxy,this,ServicePriority.Highest);
    }
    void installProviders(){
        provider(TownyBuildsApi.class,(p,m,a)->{
            if(m.getName().equals("buildingFootprints")&&T.equals(a[0])){
                if(outage)throw new IllegalStateException("fixture provider outage");int x=foreign?32:0;
                return Map.of("foundry",new BuildingFootprint(world.getUID(),x,0,x+15,15,5,5),"park",new BuildingFootprint(world.getUID(),0,0,15,15,5,5),"agrarian_complex",new BuildingFootprint(world.getUID(),0,0,15,15,1,1));
            }
            return m.getName().equals("operationalLevel")&&T.equals(a[0])?0:UNHANDLED;
        });
        provider(TownyUpkeepApi.class,(p,m,a)->m.getName().equals("active")&&T.equals(a[0])?true:UNHANDLED);
        provider(TownyPowerApi.class,(p,m,a)->m.getName().equals("powered")&&T.equals(a[0])?(!"park".equals(a[1])||parkPowered):UNHANDLED);
        provider(TownySpecializationApi.class,(p,m,a)->m.getName().equals("canUseBuilding")&&T.equals(a[0])?true:UNHANDLED);
    }
    final class Actor {
        final Player player; PermissibleBase permissions; Inventory top = Bukkit.createInventory(null, 9), bottom = Bukkit.createInventory(null, 36); InventoryView view;
        ItemStack held = new ItemStack(Material.AIR); PlayerInventory inventory;
        Actor(UUID id, String name) {
            inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class}, (p,m,a) -> switch(m.getName()) { case "getItemInMainHand" -> held; case "setItemInMainHand" -> { held = (ItemStack)a[0]; yield null; } default -> null; });
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id; case "getName" -> name; case "getServer" -> Bukkit.getServer(); case "getWorld" -> world;
                case "getLocation" -> new Location(world, 8, 90, 8); case "isOnline" -> true; case "isOp", "isDead" -> false;
                case "hasPermission", "isPermissionSet", "addAttachment", "removeAttachment", "recalculatePermissions", "getEffectivePermissions" -> m.invoke(permissions, a);
                case "getInventory" -> inventory; case "getOpenInventory" -> view; case "openInventory" -> { top = (Inventory) a[0]; yield view; } case "closeInventory" -> { top = Bukkit.createInventory(null, 9); yield null; }
                case "hashCode" -> id.hashCode(); case "equals" -> p == a[0]; case "toString" -> "Environment fixture actor"; default -> null;
            });
            permissions = new PermissibleBase(player);
            view = (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(), new Class<?>[]{InventoryView.class}, (p, m, a) -> switch (m.getName()) {
                case "getTopInventory" -> top; case "getBottomInventory" -> bottom; case "getPlayer" -> player; case "getType" -> InventoryType.CHEST;
                case "getCursor" -> new ItemStack(Material.AIR); case "countSlots" -> top.getSize() + 36; case "getTitle", "getOriginalTitle" -> "Fixture";
                case "getSlotType" -> InventoryType.SlotType.CONTAINER; case "convertSlot" -> a[0]; case "getInventory" -> (Integer) a[0] < top.getSize() ? top : bottom; default -> null;
            });
        }
    }
}
