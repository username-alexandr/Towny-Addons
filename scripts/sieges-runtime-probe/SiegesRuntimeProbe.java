package ru.neverland.runtime;
import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.plugin.*;import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.*;import org.bukkit.event.inventory.*;import org.bukkit.event.player.*;import org.bukkit.event.entity.*;import org.bukkit.event.vehicle.*;import org.bukkit.permissions.PermissibleBase;
import net.kyori.adventure.text.Component;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.*;
import com.gmail.goosius.siegewar.*;import com.gmail.goosius.siegewar.enums.*;import com.gmail.goosius.siegewar.objects.*;
import ru.neverland.townysieges.*;import ru.neverland.townysieges.api.*;import static ru.neverland.townysieges.SiegeRules.*;
import ru.neverland.townybuilds.data.DataStore;import ru.neverland.townybuilds.construction.ConstructionSite;import ru.neverland.townybuilds.api.TownyBuildsApi;
import ru.neverland.townyresources.service.ResourcesService;
/** Disposable native API/event fixture. SiegeWar owns and persists the actual siege. */
public final class SiegesRuntimeProbe extends JavaPlugin {
    static UUID id(String s){return UUID.nameUUIDFromBytes(("sieges-032-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID T=id("defender-town"),A=id("attacker-town"),N=id("attacker-nation"),M=id("defender"),S=id("attacker"),V=id("visitor");
    Path proof;boolean restart;int checks;World world;Location cell;Town town,attackingTown;SiegeService service;SiegeListener listener;TownySiegesApi api;DataStore builds;
    int expected(String key)throws Exception{var p=new Properties();try(var in=getResource("expected-counts.properties")){p.load(in);}return Integer.parseInt(p.getProperty(key));}
    JavaPlugin plugin(String name){return (JavaPlugin)Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(name));}
    static <T>T field(Object owner,Class<T> type)throws Exception{for(var f:owner.getClass().getDeclaredFields())if(type.isAssignableFrom(f.getType())){f.setAccessible(true);return type.cast(f.get(owner));}throw new IllegalArgumentException(type.getName());}
    static void invoke(Object object,String method,Class<?>[] signature,Object...args)throws Exception{var m=object.getClass().getDeclaredMethod(method,signature);m.setAccessible(true);m.invoke(object,args);}
    void check(boolean b,String label)throws Exception{if(!b)throw new AssertionError(label);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+label);}
    interface Action{void run()throws Exception;}void denied(Action a,String label)throws Exception{boolean denied=false;try{a.run();}catch(Exception e){denied=true;}check(denied,label);}
    boolean permitted(){return Boolean.getBoolean("neverland.runtimeProbe")&&Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))&&"127.0.0.1".equals(getServer().getIp());}
    @Override public void onEnable(){if(!permitted()){Bukkit.getPluginManager().disablePlugin(this);return;}Bukkit.getScheduler().runTaskLater(this,this::run,10);}
    void run(){try{proof=getDataFolder().toPath();Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));world=Bukkit.getWorlds().getFirst();cell=new Location(world,150*16+4.5,96,150*16+4.5);
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==expected("addons"),"all exact-version addons enabled");contracts();
        api=Bukkit.getServicesManager().load(TownySiegesApi.class);service=field(plugin("NeverLandTownySiegesPlus"),SiegeService.class);listener=new SiegeListener(service);builds=field(plugin("NeverLandTownyBuilds"),DataStore.class);
        check(api!=null&&api.healthy(),"healthy Sieges+ public API with real SiegeWar 3.6.2");
        if(restart){town=TownyAPI.getInstance().getTown(T);attackingTown=TownyAPI.getInstance().getTown(A);check(SiegeWarAPI.hasActiveSiege(town),"real SiegeWar siege reloaded from Towny metadata");infrastructure();}else setup();
        scenarios();
        TownyUniverse.getInstance().getDataSource().saveAll();world.save();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions\n");Bukkit.getScheduler().runTask(this,Bukkit::shutdown);
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SIEGES PROBE FAILED",e);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("failed.txt"),e.toString());}catch(Exception ignored){}Bukkit.shutdown();}}
    void setup()throws Exception{
        var u=TownyUniverse.getInstance();u.newTownInternal("SiegeAudit",T);u.newTownInternal("SiegeRaiders",A);town=TownyAPI.getInstance().getTown(T);attackingTown=TownyAPI.getInstance().getTown(A);
        for(var e:Map.of(M,"SiegeMayor",S,"SiegeRaider",V,"SiegeVisitor").entrySet()){var r=u.getDataSource().newResident(e.getValue(),e.getKey());if(!e.getKey().equals(V))r.setTown(e.getKey().equals(M)?town:attackingTown);r.save();}
        town.setMayor(TownyAPI.getInstance().getResident(M));attackingTown.setMayor(TownyAPI.getInstance().getResident(S));
        for(int x=144;x<=156;x++)for(int z=144;z<=156;z++){var claim=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);if(x==150&&z==150)town.setHomeBlock(claim);claim.save();}
        var home=new TownBlock(170,170,u.getWorld(world.getName()));u.addTownBlock(home);home.setTown(attackingTown);attackingTown.setHomeBlock(home);home.save();
        world.getChunkAt(150,150).load();cell.getBlock().setType(Material.AIR);cell.getBlock().getRelative(0,1,0).setType(Material.AIR);cell.getBlock().getRelative(0,-1,0).setType(Material.STONE);town.setSpawn(cell.clone());attackingTown.setSpawn(new Location(world,2724.5,96,2724.5));
        u.getDataSource().newNation("SiegeNation",N);var nation=TownyAPI.getInstance().getNation(N);attackingTown.setNation(nation);nation.setCapital(attackingTown);nation.save();town.save();attackingTown.save();town.getAccount().deposit(100000,"disposable Siege seed");
        var td=builds.town(T);for(Fort f:Fort.values()){td.setLevel(f.project,3);td.setConstructionSite(new ConstructionSite(f.project,world.getUID(),cell.getBlockX(),cell.getBlockY(),cell.getBlockZ(),org.bukkit.block.BlockFace.NORTH,3,3,1,false));}builds.save();infrastructure();
        SiegeController.newSiege(town);SiegeController.setSiege(town,true);var siege=SiegeWarAPI.getSiegeOrNull(town);siege.setAttacker(nation);siege.setDefender(town);siege.setFlagLocation(cell.clone().add(0,0,90));siege.setSiegeType(SiegeType.CONQUEST);siege.setStatus(SiegeStatus.IN_PROGRESS);SiegeController.saveSiege(siege);
    }
    void infrastructure()throws Exception{
        var upkeep=field(plugin("NeverLandTownyUpkeep"),ru.neverland.townyupkeep.service.UpkeepService.class);invoke(upkeep,"scan",new Class<?>[]{});
        field(plugin("NeverLandTownyPower"),ru.neverland.townypower.service.PowerService.class).refresh();
        field(plugin("NeverLandTownyPopulation"),ru.neverland.townypopulation.service.PopulationService.class).refreshView();
        invoke(field(plugin("NeverLandTownyResources"),ResourcesService.class),"refresh",new Class<?>[]{boolean.class},false);
    }
    Location middle(Zone z){return new Location(world,(z.x1()+z.x2())/2,cell.getY(),(z.z1()+z.z2())/2);}
    PlayerTeleportEvent teleport(Actor p,Location from,Location to,PlayerTeleportEvent.TeleportCause cause){var e=new PlayerTeleportEvent(p.player,from,to,cause);listener.teleport(e);return e;}
    void scenarios()throws Exception{
        var raider=new Actor(S);var mayor=new Actor(M);var visitor=new Actor(V);raider.permissions.addAttachment(this,"siegewar.nation.siege.battle.points",true);mayor.permissions.addAttachment(this,"siegewar.town.siege.battle.points",true);
        var session=BattleSession.getBattleSession();session.setActive(true);session.setScheduledEndTime(System.currentTimeMillis()+3600000);
        check(service.war().context(town,raider.player).side().equals("ATTACKERS"),"real SiegeWar resolves attacking soldier permissions and nation");
        check(service.war().context(town,mayor.player).side().equals("DEFENDERS"),"real SiegeWar resolves defending town guard");
        check(service.assess(visitor.player,cell).town()==null,"unaffiliated visitor has no siege restrictions");
        var a=service.assess(raider.player,cell);check(!a.uncertain()&&a.levels().size()==6&&a.levels().values().stream().allMatch(n->n==3),"six real operational completed Builds defenses");
        var outside=cell.clone().add(0,0,180);
        check(teleport(raider,outside,cell,PlayerTeleportEvent.TeleportCause.ENDER_PEARL).isCancelled(),"wall blocks attacking ender pearl");
        check(teleport(raider,outside,cell,PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT).isCancelled(),"wall blocks Paper 26.2 consumable teleport including chorus");
        check(!teleport(raider,cell,outside,PlayerTeleportEvent.TeleportCause.ENDER_PEARL).isCancelled(),"wall allows retreat out of town");
        check(!teleport(mayor,outside,cell,PlayerTeleportEvent.TeleportCause.ENDER_PEARL).isCancelled(),"wall leaves defenders unaffected");
        check(!teleport(visitor,outside,cell,PlayerTeleportEvent.TeleportCause.ENDER_PEARL).isCancelled(),"wall leaves visitors unaffected");
        var gate=a.zones().get(Fort.GATE);var at=middle(gate);var before=at.clone();before.setX(gate.x1()-2);
        var move=new PlayerMoveEvent(raider.player,before,at);listener.move(move);check(move.isCancelled(),"gate prevents ground entrance");
        var fast=new PlayerMoveEvent(raider.player,before,before.clone().add(gate.x2()-gate.x1()+4,0,0));listener.move(fast);check(fast.isCancelled(),"gate prevents skipping footprint in one packet");
        var exit=new PlayerMoveEvent(raider.player,at,before);listener.move(exit);check(!exit.isCancelled(),"gate allows an already enclosed attacker to exit");
        var friendly=new PlayerMoveEvent(mayor.player,before,at);listener.move(friendly);check(!friendly.isCancelled(),"gate allows defenders");
        raider.location=middle(a.zones().get(Fort.TOWER));var glide=new EntityToggleGlideEvent(raider.player,true);listener.glide(glide);check(glide.isCancelled(),"active tower blocks elytra launch");
        raider.flying=true;var tower=a.zones().get(Fort.TOWER);var air=middle(tower);air.setY(cell.getY()+60);var airFrom=air.clone();airFrom.setX(tower.x1()-3);var flight=new PlayerMoveEvent(raider.player,airFrom,air);listener.move(flight);check(flight.isCancelled(),"tower stops airborne approach above gate height");raider.flying=false;
        raider.location=middle(a.zones().get(Fort.MOAT));var sprint=new PlayerToggleSprintEvent(raider.player,true);listener.sprint(sprint);check(sprint.isCancelled(),"moat blocks sprint");var swim=new EntityToggleSwimEvent(raider.player,true);listener.swim(swim);check(swim.isCancelled(),"moat blocks fast swimming");
        var speed=raider.body.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);double base=speed.getValue();var foreign=new org.bukkit.attribute.AttributeModifier(new NamespacedKey(this,"other-speed"),.2,org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1);speed.addTransientModifier(foreign);double buffed=speed.getValue();service.slow(raider.player,.3);check(speed.getValue()<buffed&&speed.getModifier(foreign.getKey())!=null,"moat adds native transient attribute alongside another plugin modifier");service.leave(raider.player);check(Math.abs(speed.getValue()-buffed)<1e-9&&speed.getModifier(SiegeService.MOAT_SPEED)==null,"leaving removes only Siege modifier");
        boat(raider,a.zones().get(Fort.PORT));
        mayor.location=cell;raider.location=cell;double defense=service.defense(raider.player,mayor.player,cell);check(defense>0&&defense<=.4,"native wall/tower and optional Army defense share cap");check(service.defense(visitor.player,mayor.player,cell)==0,"non-attacker damage unchanged");
        session.setActive(false);check(service.assess(raider.player,cell).town()==null,"battle break suspends all new restrictions immediately");session.setActive(true);
        var siege=SiegeWarAPI.getSiegeOrNull(town);siege.setStatus(SiegeStatus.DEFENDER_CRUSHING_WIN);check(service.assess(raider.player,cell).town()==null,"finished siege removes restrictions immediately");siege.setStatus(SiegeStatus.IN_PROGRESS);
        builds.town(T).setLevel("fortress_wall",0);check(!teleport(raider,outside,cell.clone().add(40,0,0),PlayerTeleportEvent.TeleportCause.ENDER_PEARL).isCancelled(),"live level zero bypasses cached wall geometry");builds.town(T).setLevel("fortress_wall",3);
        var bypass=raider.permissions.addAttachment(this,"neverlandtownysiegesplus.bypass",true);check(service.assess(raider.player,cell).town()==null,"explicit staff bypass");raider.permissions.removeAttachment(bypass);
        failureCases(raider,visitor);
        var menu=new SiegeMenu((NeverLandTownySiegesPlus)plugin("NeverLandTownySiegesPlus"),service);menu.open(raider.player,T,null);check(raider.top.getItem(53)==null&&raider.top.getItem(19)!=null,"ordinary menu hides diagnostics and displays defense cards");var parent=raider.top;menu.open(raider.player,T,parent);menu.click(new InventoryClickEvent(raider.view,InventoryType.SlotType.CONTAINER,45,ClickType.LEFT,InventoryAction.PICKUP_ALL));check(raider.top==parent,"back arrow returns actual previous menu");
        var command=Bukkit.getPluginCommand("townysieges");check(!command.tabComplete(raider.player,"sieges",new String[]{""}).contains("reload"),"privileged commands hidden from completion");
        var old=service.settings();var config=plugin("NeverLandTownySiegesPlus").getDataFolder().toPath().resolve("config.yml");String saved=Files.readString(config);Files.writeString(config,saved.replace("maximum-reduction: 0.40","maximum-reduction: 1.0"));denied(()->((NeverLandTownySiegesPlus)plugin("NeverLandTownySiegesPlus")).reloadSieges(),"invalid reload rejected");check(service.settings()==old,"invalid reload preserves previous settings");Files.writeString(config,saved);
        check(api.defenses(null).isEmpty()&&api.restrictions(null,null,0,0,0).isEmpty(),"null public API queries safe");try{api.defenses(T).clear();throw new AssertionError("mutable API");}catch(UnsupportedOperationException expected){check(true,"public snapshots immutable");}
        final boolean[] asyncDenied={false};Thread t=new Thread(()->{try{api.warStatus();}catch(IllegalStateException expected){asyncDenied[0]=true;}});t.start();t.join();check(asyncDenied[0],"API rejects off-thread access");
        builds.town(T).setCivicLine("city_moat",new ru.neverland.townybuilds.civic.CivicLine(world.getUID(),cell.getBlockX()-40,96,cell.getBlockZ()-40,cell.getBlockX()+40,96,cell.getBlockZ()+40));service.reload(service.settings());
        var diagonal=service.assess(raider.player,cell);check(diagonal.near(Fort.MOAT,SiegeService.point(cell.clone().add(30,0,30)))>0,"Builds public selected-line API extends actual diagonal moat coverage");check(diagonal.near(Fort.MOAT,SiegeService.point(cell.clone().add(-30,0,30)))==0,"diagonal moat leaves unrelated ground clear");
        long started=System.nanoTime();for(int i=0;i<100;i++)service.assess(raider.player,cell);Files.writeString(proof.resolve((restart?"restart":"first")+"-100-queries-ms.txt"),Double.toString((System.nanoTime()-started)/1e6));
        SiegeController.saveSiege(siege);builds.save();raider.body.remove();mayor.body.remove();visitor.body.remove();check(api.healthy(),"providers healthy after recovery checks");
    }
    void failureCases(Actor raider,Actor visitor)throws Exception{
        var sm=Bukkit.getServicesManager();var diplomacy=sm.load(ru.neverland.townydiplomacy.api.TownyDiplomacyApi.class);var treaty=(ru.neverland.townydiplomacy.api.TownyDiplomacyApi)Proxy.newProxyInstance(diplomacy.getClass().getClassLoader(),new Class<?>[]{ru.neverland.townydiplomacy.api.TownyDiplomacyApi.class},(p,m,args)->m.getName().equals("hostileBlocked")?true:m.invoke(diplomacy,args));sm.register(ru.neverland.townydiplomacy.api.TownyDiplomacyApi.class,treaty,this,ServicePriority.Highest);check(service.assess(raider.player,cell).town()==null,"Diplomacy nonaggression veto excludes treaty partner from attacker restrictions");sm.unregister(ru.neverland.townydiplomacy.api.TownyDiplomacyApi.class,treaty);

        var manager=Bukkit.getServicesManager();var real=manager.load(TownyBuildsApi.class);var broken=(TownyBuildsApi)Proxy.newProxyInstance(TownyBuildsApi.class.getClassLoader(),new Class<?>[]{TownyBuildsApi.class},(p,m,args)->{if(m.getName().equals("operationalLevel"))throw new IllegalStateException("fixture Builds read failure");return m.invoke(real,args);});
        manager.register(TownyBuildsApi.class,broken,this,ServicePriority.Highest);check(service.assess(raider.player,cell).uncertain(),"known attacker gets explicit unavailable-defense state");check(service.assess(visitor.player,cell).town()==null,"dependency failure never classifies visitor as attacker");var guard=new Actor(M);check(service.defense(raider.player,guard.player,cell)<0,"unavailable known defense rejects combat evaluation");guard.body.remove();manager.unregister(TownyBuildsApi.class,broken);check(!service.assess(raider.player,cell).uncertain(),"Builds recovery reconnects without restart");
    }
    void boat(Actor raider,Zone port)throws Exception{
        var inside=middle(port);inside.setX(port.x1()+2);var from=inside.clone();from.setX(port.x1()-3);
        inside.getChunk().load();from.getChunk().load();var real=(Boat)world.spawnEntity(inside,EntityType.OAK_BOAT);var cargo=world.spawn(inside,org.bukkit.entity.Pig.class);real.addPassenger(cargo);
        var wrapper=(Boat)Proxy.newProxyInstance(Boat.class.getClassLoader(),new Class<?>[]{Boat.class},(p,m,args)->m.getName().equals("getPassengers")?List.of(raider.player):m.invoke(real,args));
        listener.boat(new VehicleMoveEvent(wrapper,from,inside));check(real.getLocation().distanceSquared(from)<.1,"native boat rolls back at port boundary");check(real.getPassengers().contains(cargo),"Paper 26.2 teleport retains actual passenger");
        var parked=(Boat)world.spawnEntity(inside,EntityType.OAK_BOAT);var board=new VehicleEnterEvent(parked,raider.player);listener.board(board);check(board.isCancelled(),"attacker cannot board a boat inside port zone");parked.remove();cargo.remove();real.remove();
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
                case "hashCode"->id.hashCode();case "equals"->proxy==a[0];case "toString"->"Sieges actor "+id;default->null;
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
