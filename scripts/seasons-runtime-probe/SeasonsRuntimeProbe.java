package ru.neverland.runtime;
import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.plugin.*;import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.*;import org.bukkit.event.inventory.*;import org.bukkit.permissions.PermissibleBase;
import net.kyori.adventure.text.Component;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townyseasons.*;import ru.neverland.townyseasons.api.*;
import ru.neverland.townybuilds.data.DataStore;import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townyresources.service.ResourcesService;import ru.neverland.townyresources.model.Resource;
import ru.neverland.mintevents.service.*;import ru.neverland.mintevents.listener.EventGameplayListener;
import ru.neverland.mintevents.integration.TownyHook;
/** Opt-in native suite/calendar/weather fixture. Never install on a player server. */
public final class SeasonsRuntimeProbe extends JavaPlugin {
    static UUID id(String key){return UUID.nameUUIDFromBytes(("seasons-033-"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID T=id("town"),M=id("mayor");
    Path proof;boolean restart;int checks;World world;Location cell;Town town;
    SeasonService service;TownySeasonsApi api;ResourcesService resources;DataStore builds;EventService events;EventRegistry registry;
    int expected(String key)throws Exception{var p=new Properties();try(var in=getResource("expected-counts.properties")){p.load(in);}return Integer.parseInt(p.getProperty(key));}
    JavaPlugin plugin(String name){return (JavaPlugin)Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(name));}
    static <T>T field(Object owner,Class<T> type)throws Exception{for(var f:owner.getClass().getDeclaredFields())if(type.isAssignableFrom(f.getType())){f.setAccessible(true);return type.cast(f.get(owner));}throw new IllegalArgumentException(type.getName());}
    static void invoke(Object object,String method,Class<?>[] signature,Object...args)throws Exception{var m=object.getClass().getDeclaredMethod(method,signature);m.setAccessible(true);m.invoke(object,args);}
    void check(boolean b,String label)throws Exception{if(!b)throw new AssertionError(label);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+label);}
    interface Action{void run()throws Exception;}void denied(Action a,String label)throws Exception{boolean denied=false;try{a.run();}catch(Exception e){denied=true;}check(denied,label);}
    boolean permitted(){return Boolean.getBoolean("neverland.runtimeProbe")&&Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))&&"127.0.0.1".equals(getServer().getIp());}
    @Override public void onEnable(){if(!permitted()){Bukkit.getPluginManager().disablePlugin(this);return;}Bukkit.getScheduler().runTaskLater(this,this::run,10);}
    void run(){try{
        proof=getDataFolder().toPath();Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));world=Bukkit.getWorlds().getFirst();cell=new Location(world,210*16+4.5,96,210*16+4.5);
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==expected("addons"),"all exact-version addons enabled");contracts();
        api=Bukkit.getServicesManager().load(TownySeasonsApi.class);service=field(plugin("NeverLandTownySeasons"),SeasonService.class);
        resources=field(plugin("NeverLandTownyResources"),ResourcesService.class);builds=field(plugin("NeverLandTownyBuilds"),DataStore.class);
        events=field(plugin("NeverLandTownyEvents"),EventService.class);registry=field(plugin("NeverLandTownyEvents"),EventRegistry.class);
        check(api!=null&&api.healthy(),"native Seasons API registered and storage healthy");
        if(restart){town=TownyAPI.getInstance().getTown(T);check(api.calendar(world.getUID()).get("season").equals("WINTER"),"manual winter survives a real JVM restart");check(events.active(T)!=null&&events.activeEvent(T).orElseThrow().eventId().equals("flood"),"flood state reloads from existing event journal");check(events.productionMultiplier(T,"agrarian_complex")==.6,"flood production loss survives restart");events.resolve(town,true);}
        else setup();
        world.getChunkAt(210,210).load();infrastructure();scenarios();
        TownyUniverse.getInstance().getDataSource().saveAll();world.save();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions\n");Bukkit.getScheduler().runTask(this,Bukkit::shutdown);
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SEASONS PROBE FAILED",e);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("failed.txt"),e.toString());}catch(Exception ignored){}Bukkit.shutdown();}}
    void setup()throws Exception{
        var u=TownyUniverse.getInstance();u.newTownInternal("SeasonAudit",T);town=TownyAPI.getInstance().getTown(T);var resident=u.getDataSource().newResident("SeasonMayor",M);resident.setTown(town);town.setMayor(resident);resident.save();
        for(int x=205;x<=215;x++)for(int z=205;z<=215;z++){var claim=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);if(x==210&&z==210)town.setHomeBlock(claim);claim.save();}
        world.getChunkAt(210,210).load();cell.getBlock().setType(Material.AIR);cell.getBlock().getRelative(0,-1,0).setType(Material.STONE);town.setSpawn(cell.clone());town.save();town.getAccount().deposit(100000,"disposable seasonal fixture");
        var td=builds.town(T);td.setLevel("agrarian_complex",1);td.setConstructionSite(new ConstructionSite("agrarian_complex",world.getUID(),cell.getBlockX(),cell.getBlockY(),cell.getBlockZ(),org.bukkit.block.BlockFace.NORTH,1,1,1,false));builds.save();
    }
    void infrastructure()throws Exception{
        invoke(field(plugin("NeverLandTownyUpkeep"),ru.neverland.townyupkeep.service.UpkeepService.class),"scan",new Class<?>[]{});
        field(plugin("NeverLandTownyPower"),ru.neverland.townypower.service.PowerService.class).refresh();
        field(plugin("NeverLandTownyPopulation"),ru.neverland.townypopulation.service.PopulationService.class).refreshView();refresh(false);
    }
    void refresh(boolean advance)throws Exception{invoke(resources,"refresh",new Class<?>[]{boolean.class},advance);}
    long output()throws Exception{refresh(false);var view=resources.resources(T).orElseThrow();check(!view.paused(),"native resource calculation available: "+view.status());var activity=view.buildings().get("agrarian_complex");check(activity.operations()==1,"completed active farm actually operates: "+activity.status());return activity.income().get(Resource.FOOD);}
    void scenarios()throws Exception{
        check(registry.get("flood")!=null&&registry.all().size()>=6,"default flood appears even with pre-update events.yml");
        var clockFile=plugin("NeverLandTownySeasons").getDataFolder().toPath().resolve("calendar.yml");long epoch=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(clockFile.toFile()).getLong("epoch");
        if(restart)check(Files.readString(proof.resolve("epoch.txt")).equals(Long.toString(epoch)),"calendar origin unchanged after restart");else Files.writeString(proof.resolve("epoch.txt"),Long.toString(epoch));
        service.override(world.getUID(),Season.WINTER);check(api.productionMultiplier(T,"agrarian_complex")==.6,"winter farm coefficient below one");check(api.productionMultiplier(T,"forge")==1,"non-agricultural production neutral");
        long winter=output();service.override(world.getUID(),Season.SPRING);long spring=output();check(spring==winter*2,"real Resources preview uses spring and winter multipliers once");
        check(api.eventWeight(T,"FLOOD")==3&&api.eventWeight(T,"DROUGHT")==.25,"spring weighted flood risk");service.override(world.getUID(),Season.SUMMER);check(api.eventWeight(T,"DROUGHT")==3,"summer weighted drought risk");service.override(world.getUID(),Season.SPRING);
        check(events.startEvent(town,registry.get("flood")),"native flood starts and saves");check(events.productionMultiplier(T,"agrarian_complex")==.6,"unprotected flood reduces future output");check(output()==spring*6/10,"flood loss and season combine through public consumers");
        var eventPlugin=plugin("NeverLandTownyEvents");double prior=eventPlugin.getConfig().getDouble("gameplay.flood-base-crop-cancel-chance",.6);eventPlugin.getConfig().set("gameplay.flood-base-crop-cancel-chance",1d);
        var crop=cell.getBlock();crop.setType(Material.WHEAT);var grow=new org.bukkit.event.block.BlockGrowEvent(crop,crop.getState());new EventGameplayListener(eventPlugin,new TownyHook(),events).onGrow(grow);check(grow.isCancelled(),"flood cancels native crop growth inside Towny land");check(crop.getType()==Material.WHEAT,"flood never replaces crops with water or destroys blocks");eventPlugin.getConfig().set("gameplay.flood-base-crop-cancel-chance",prior);
        var before=resources.resources(T).orElseThrow().state();refresh(true);var after=resources.resources(T).orElseThrow().state();for(Resource resource:Resource.values())check(after.balances().get(resource)==before.balances().get(resource)+after.income().get(resource)-after.expense().get(resource),"committed production conservation "+resource);
        check(events.contribute(town,100000)>=0&&events.active(T)==null,"resident contribution mechanism resolves flood");check(events.productionMultiplier(T,"agrarian_complex")==1&&output()==spring,"resolved flood restores future production");
        var mayor=new Actor(M);var menu=new SeasonMenu((NeverLandTownySeasons)plugin("NeverLandTownySeasons"),service);menu.open(mayor.player,world.getUID(),null);check(mayor.top.getItem(53)==null&&mayor.top.getItem(19)!=null,"ordinary calendar menu hides staff controls");var previous=mayor.top;menu.open(mayor.player,world.getUID(),previous);menu.click(new InventoryClickEvent(mayor.view,InventoryType.SlotType.CONTAINER,45,ClickType.LEFT,InventoryAction.PICKUP_ALL));check(mayor.top==previous,"back arrow returns actual previous menu");
        var command=Bukkit.getPluginCommand("townyseasons");command.execute(mayor.player,"tseasons",new String[]{"set",world.getName(),"winter"});check(api.calendar(world.getUID()).get("season").equals("SPRING"),"ordinary resident cannot force season");check(!command.tabComplete(mayor.player,"tseasons",new String[]{""}).contains("set"),"staff commands excluded from ordinary completion");
        var permission=mayor.permissions.addAttachment(this,"neverlandtownyseasons.admin",true);command.execute(mayor.player,"tseasons",new String[]{"set",world.getName(),"winter"});check(api.calendar(world.getUID()).get("season").equals("WINTER"),"authorized set command persists season");mayor.permissions.removeAttachment(permission);
        denied(()->api.calendar(UUID.randomUUID()),"unknown world rejected");denied(()->api.townCalendar(null),"unknown town rejected");denied(()->api.calendar(world.getUID()).clear(),"API calendar immutable");denied(()->((Map<?,?>)api.calendar(world.getUID()).get("production")).clear(),"nested coefficient map immutable");
        final boolean[] denied={false};Thread thread=new Thread(()->{try{api.calendar(world.getUID());}catch(IllegalStateException e){denied[0]=true;}});thread.start();thread.join();check(denied[0],"off-thread world API access rejected");
        for(World w:Bukkit.getWorlds())if(w.getEnvironment()!=World.Environment.NORMAL)check(api.calendar(w.getUID()).get("enabled").equals(false),"Nether/End remain neutral");
        var old=service.settings();var configFile=plugin("NeverLandTownySeasons").getDataFolder().toPath().resolve("config.yml");String saved=Files.readString(configFile);Files.writeString(configFile,saved.replace("days-per-season: 7","days-per-season: 0"));denied(()->((NeverLandTownySeasons)plugin("NeverLandTownySeasons")).reloadSeasons(),"invalid calendar reload rejected");check(service.settings()==old,"invalid reload retains live settings");Files.writeString(configFile,saved);
        service.override(world.getUID(),null);var game=new SeasonSettings(SeasonSettings.Mode.MINECRAFT,old.days(),old.dayMillis(),old.first(),old.worlds(),false,old.effects());service.reload(game);var gameDate=api.calendar(world.getUID());long time=world.getFullTime();world.setTime(18000);check(api.calendar(world.getUID()).get("day").equals(gameDate.get("day")),"time-of-day changes cannot skip game calendar");world.setFullTime(time);
        service.reload(new SeasonSettings(SeasonSettings.Mode.REALISTIC_SEASONS,old.days(),old.dayMillis(),old.first(),old.worlds(),false,old.effects()));denied(()->api.calendar(world.getUID()),"explicit external mode refuses missing RealisticSeasons");check(service.providerStatus().equals("NOT_INSTALLED"),"external provider absence is diagnosed");service.reload(old);
        var sm=Bukkit.getServicesManager();var broken=(TownySeasonsApi)Proxy.newProxyInstance(TownySeasonsApi.class.getClassLoader(),new Class<?>[]{TownySeasonsApi.class},(p,m,a)->{if(m.getName().equals("productionMultiplier"))throw new IllegalStateException("fixture provider failure");return m.invoke(api,a);});sm.register(TownySeasonsApi.class,broken,this,ServicePriority.Highest);var stable=resources.resources(T).orElseThrow().state();refresh(true);check(resources.resources(T).orElseThrow().paused()&&resources.resources(T).orElseThrow().state().equals(stable),"broken installed season provider pauses cycle without stock changes");sm.unregister(TownySeasonsApi.class,broken);service.override(world.getUID(),Season.WINTER);output();
        check(events.startEvent(town,registry.get("flood")),"persist active flood for restart");mayor.body.remove();builds.save();check(api.healthy(),"calendar healthy after all recovery cases");
    }
    void contracts()throws Exception{try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getResource("contracts.tsv"),java.nio.charset.StandardCharsets.UTF_8))){var rows=reader.lines().toList();check(rows.size()==expected("contracts"),"all public contracts from source inventory");for(String line:rows){String[] row=line.split("\t");var p=plugin(row[0]);Class<?> type=Class.forName(row[1],true,p.getClass().getClassLoader());Object provider=Bukkit.getServicesManager().load(type);check(provider!=null&&p.isEnabled()&&p.getDescription().getVersion().equals(row[3])&&Integer.valueOf(1).equals(type.getMethod("apiVersion").invoke(provider))&&new HashSet<>(Arrays.asList(row[2].split(","))).equals(type.getMethod("capabilities").invoke(provider)),"provider ABI/capabilities: "+row[1]);}}}
    private final class Actor {
        Location location=cell.clone(); boolean flying; final org.bukkit.entity.Pig body=world.spawn(cell,org.bukkit.entity.Pig.class); final Player player; PermissibleBase permissions; Inventory top=Bukkit.createInventory(null,9,Component.text("Fixture")); final Inventory bottom=Bukkit.createInventory(null,36);
        InventoryView view; final List<String> messages=new ArrayList<>();
        Actor(UUID id) {
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->switch(m.getName()) {
                case "getPassengers"->List.of();case "getUniqueId"->id;case "getName"->TownyAPI.getInstance().getResident(id).getName();case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->location;case "getAttribute"->body.getAttribute((org.bukkit.attribute.Attribute)a[0]);case "isGliding"->flying;case "setGliding"->{flying=(Boolean)a[0];yield null;}case "isRiptiding"->false;
                case "isOnline"->true;case "isDead","isInsideVehicle"->false;case "getGameMode"->GameMode.SURVIVAL;case "isOp"->false;
                case "hasPermission","isPermissionSet","addAttachment","removeAttachment","recalculatePermissions","getEffectivePermissions"->m.invoke(permissions,a);
                case "sendMessage"->{messages.add(String.valueOf(a[a.length-1]));yield null;}
                case "getOpenInventory"->view;case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,9);yield null;}
                case "hashCode"->id.hashCode();case "equals"->proxy==a[0];case "toString"->"Seasons actor "+id;default->null;
            });
            permissions=new PermissibleBase(player);
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(proxy,m,a)->switch(m.getName()) {
                case "getTopInventory"->top;case "getBottomInventory"->bottom;case "getPlayer"->player;case "getType"->InventoryType.CHEST;
                case "getCursor"->new ItemStack(Material.AIR);case "countSlots"->top.getSize()+36;case "getTitle","getOriginalTitle"->"Fixture";case "title"->Component.text("Fixture");
                case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];case "getInventory"->(Integer)a[0]<top.getSize()?top:bottom;default->null;
            });
        }
    }
}
