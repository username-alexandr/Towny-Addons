package ru.neverland.runtime;
import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.plugin.*;import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.*;import org.bukkit.event.inventory.*;import org.bukkit.permissions.PermissibleBase;
import net.kyori.adventure.text.Component;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townyarmy.*;import ru.neverland.townyarmy.api.*;import static ru.neverland.townyarmy.ArmyModel.*;
import ru.neverland.townybuilds.api.TownArmyApi;import ru.neverland.townybuilds.data.DataStore;import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townyresources.api.TownyResourcesApi;import ru.neverland.townyresources.service.ResourcesService;import ru.neverland.townyresources.model.Resource;
import ru.neverland.townycitizens.model.CitizenshipStatus;import ru.neverland.townycouncil.*;import ru.neverland.townycitizens.*;
/** Opt-in native provider/command fixture. No network clients or physical military units. */
public final class ArmyRuntimeProbe extends JavaPlugin {
    static UUID id(String s){return UUID.nameUUIDFromBytes(("army-031-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID T=id("town"),M=id("mayor"),S=id("soldier"),G=id("general"),D=id("minister"),L=id("legacy"),Y=id("young"),P=id("pending");
    Path proof;boolean restart;int checks;World world;Location cell;Town town;ArmyService army;ArmyRepository ledger;TownyArmyApi api;ResourcesService resources;
    int expected(String key)throws Exception{var p=new Properties();try(var in=getResource("expected-counts.properties")){p.load(in);}return Integer.parseInt(p.getProperty(key));}
    JavaPlugin plugin(String name){return (JavaPlugin)Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(name));}
    static <T>T field(Object owner,Class<T> type)throws Exception{for(var f:owner.getClass().getDeclaredFields())if(type.isAssignableFrom(f.getType())){f.setAccessible(true);return type.cast(f.get(owner));}throw new IllegalArgumentException(type.getName());}
    static void invoke(Object object,String method,Class<?>[] signature,Object...args)throws Exception{var m=object.getClass().getDeclaredMethod(method,signature);m.setAccessible(true);m.invoke(object,args);}
    void check(boolean b,String label)throws Exception{if(!b)throw new AssertionError(label);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+label);}
    interface Action{void run()throws Exception;}void denied(Action a,String label)throws Exception{boolean denied=false;try{a.run();}catch(Exception e){denied=true;}check(denied,label);}
    boolean permitted(){return Boolean.getBoolean("neverland.runtimeProbe")&&Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))&&"127.0.0.1".equals(getServer().getIp());}
    @Override public void onLoad(){if(!permitted()||Files.exists(getDataFolder().toPath().resolve("first-passed.txt")))return;
        try{var p=Path.of("plugins/NeverLandTownyBuilds/army-data.yml");var y=new org.bukkit.configuration.file.YamlConfiguration();for(UUID id:List.of(M,S,G,D,L))y.set("ages."+id,25);y.set("ages."+Y,17);y.set("soldiers."+L,T.toString());y.set("retired",false);y.save(p.toFile());}catch(Exception e){throw new IllegalStateException(e);}}
    @Override public void onEnable(){if(!permitted()){Bukkit.getPluginManager().disablePlugin(this);return;}Bukkit.getScheduler().runTaskLater(this,this::run,10);}
    void run(){try{proof=getDataFolder().toPath();Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));world=Bukkit.getWorlds().getFirst();cell=new Location(world,150*16+4.5,96,150*16+4.5);
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==expected("addons"),"all exact-version addons enabled");contracts();
        api=Bukkit.getServicesManager().load(TownyArmyApi.class);army=field(plugin("NeverLandTownyArmy"),ArmyService.class);ledger=army.repository();resources=field(plugin("NeverLandTownyResources"),ResourcesService.class);check(api!=null&&api.healthy(),"healthy Army API after migration acknowledgement");
        if(restart){town=TownyAPI.getInstance().getTown(T);infrastructure();restartChecks();}else{setup();scenarios();}
        TownyUniverse.getInstance().getDataSource().saveAll();world.save();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions\n");Bukkit.getScheduler().runTask(this,Bukkit::shutdown);
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"ARMY PROBE FAILED",e);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("failed.txt"),e.toString());}catch(Exception ignored){}Bukkit.shutdown();}}
    void setup()throws Exception{check(TownyAPI.getInstance().getTown(T)==null,"fresh disposable army town");var u=TownyUniverse.getInstance();u.newTownInternal("ArmyAudit",T);town=TownyAPI.getInstance().getTown(T);
        for(var e:Map.of(M,"ArmyMayor",S,"ArmySoldier",G,"ArmyGeneral",D,"ArmyMinister",L,"ArmyLegacy",Y,"ArmyYoung").entrySet()){var resident=u.getDataSource().newResident(e.getValue(),e.getKey());resident.setTown(town);resident.save();}
        town.setMayor(TownyAPI.getInstance().getResident(M));
        for(int x=147;x<=154;x++)for(int z=147;z<=154;z++){var claim=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);if(x==150&&z==150)town.setHomeBlock(claim);claim.save();}
        world.getChunkAt(150,150).load();cell.getBlock().setType(Material.AIR);cell.getBlock().getRelative(0,1,0).setType(Material.AIR);cell.getBlock().getRelative(0,-1,0).setType(Material.STONE);town.setSpawn(cell.clone());town.save();town.getAccount().deposit(100000,"disposable Army seed");
        var data=field(plugin("NeverLandTownyBuilds"),DataStore.class);var td=data.town(T);
        for(String project:List.of("army","barracks","stables")){td.setLevel(project,3);td.setConstructionSite(new ConstructionSite(project,world.getUID(),cell.getBlockX(),cell.getBlockY(),cell.getBlockZ(),org.bukkit.block.BlockFace.NORTH,3,3,1,false));}data.save();infrastructure();
        for(Resource r:Resource.values())resources.adjust(T,r,Math.min(100000L,resources.resources(T).orElseThrow().capacity().get(r)),"set");
        check(new ArmyBridge().levels(T,army.settings()).get("army")==3,"Army reads actual operational Builds headquarters");
    }
    void infrastructure()throws Exception{
        var upkeep=field(plugin("NeverLandTownyUpkeep"),ru.neverland.townyupkeep.service.UpkeepService.class);invoke(upkeep,"scan",new Class<?>[]{});
        field(plugin("NeverLandTownyPower"),ru.neverland.townypower.service.PowerService.class).refresh();
        field(plugin("NeverLandTownyPopulation"),ru.neverland.townypopulation.service.PopulationService.class).refreshView();
        invoke(resources,"refresh",new Class<?>[]{boolean.class},false);
    }
    long city(Resource r){return resources.resources(T).orElseThrow().state().balances().get(r);}
    long stock(String r){return api.reserves(T).getOrDefault(r,0L);}
    void scenarios()throws Exception{
        var mayor=new Actor(M);var soldier=new Actor(S);var general=new Actor(G);var minister=new Actor(D);var young=new Actor(Y);
        var old=Bukkit.getServicesManager().load(TownArmyApi.class);
        check(ledger.state().imported()&&ledger.state().handoff()&&ledger.state().soldiers().get(L).status()==Status.ACTIVE,"legacy roster imported before old owner retirement");
        check(old.legacyRoster().isEmpty()&&old.isMobilized(L)&&api.isMobilized(L),"old and new mobilization APIs share one live roster");
        check(old.characterAge(L).orElse(-1)==25,"legacy verified character ages remain available");
        denied(()->army.apply(young.player,Unit.INFANTRY),"unverified or underage RP character cannot enlist");
        army.apply(soldier.player,Unit.INFANTRY);denied(()->army.apply(soldier.player,Unit.INFANTRY),"duplicate application rejected");
        denied(()->army.approve(soldier.player,S),"ordinary applicant cannot approve own admission");
        army.approve(mayor.player,S);army.base(mayor.player);army.oath(soldier.player);check(ledger.state().soldiers().get(S).status()==Status.RESERVE,"admission and base oath create a private in reserve");
        army.apply(general.player,Unit.INFANTRY);army.approve(mayor.player,G);army.oath(general.player);army.commission(mayor.player,G,"Fixture command appointment");
        check(army.officer(general.player,T,Rank.GENERAL)&&!army.executive(general.player,T),"appointed general has military authority, not mayor authority");
        denied(()->army.commission(general.player,S,"Forbidden appointment"),"general cannot appoint another general");
        denied(()->army.promote(general.player,G,"Forbidden self promotion"),"self promotion rejected");
        denied(()->army.promote(general.player,S,"Insufficient preparation"),"promotion requires actual training and duty time");
        var d=ledger.draft();var s=d.soldiers.get(S);d.soldier(s.trained(10,System.currentTimeMillis()-army.settings().trainingCooldown()-1).duty(600000));ledger.commit(d.freeze());army.promote(general.player,S,"Fixture earned promotion");check(ledger.state().soldiers().get(S).rank()==Rank.CORPORAL,"earned promotion advances one rank");
        var council=((NeverLandTownyCouncil)plugin("NeverLandTownyCouncil")).council();council.appoint(mayor.player,town,MinisterRole.DEFENSE,D);council.refresh(minister.player);check(army.executive(minister.player,T)&&!army.executive(minister.player,id("other-town")),"Council defense minister authority is live and town scoped");council.dismiss(mayor.player,town,MinisterRole.DEFENSE);council.refresh(minister.player);check(!army.executive(minister.player,T),"dismissed defense minister loses Army authority");
        long before=city(Resource.METAL);UUID transfer=army.supply(general.player,"metal",10000);check(ledger.transfer(transfer).phase()==Phase.CLOSED&&stock("metal")==10000&&city(Resource.METAL)==before-10000,"native Resources supply debits and credits exactly once");army.process(transfer);check(stock("metal")==10000&&city(Resource.METAL)==before-10000,"completed supply replay has no effect");
        for(String r:List.of("materials","food","water","knowledge"))army.supply(general.player,r,10000);
        army.equip(general.player,S);check(ledger.state().soldiers().get(S).equipment()==100&&stock("metal")==8000&&stock("materials")==9000,"equipment consumes the configured unit kit atomically");
        army.mobilize(general.player,S,true);army.pulse();check(api.isMobilized(S)&&old.soldiers(T).contains(S)&&((Number)api.garrison(T).orElseThrow().get("readiness")).doubleValue()>0,"mobilization, upkeep and legacy API use the same supplied garrison");
        double defense=ru.neverland.mintespionage.integration.ArmyAccess.defense(T);check(defense>0,"Espionage reads actual Army defense bonus");
        army.duty(soldier.player,true);check(army.onDuty(S),"base duty starts explicitly");army.train(soldier.player);army.teleported(S);army.leave(S);check(!army.onDuty(S),"leaving duty removes transient training session");
        var citizens=((NeverLandTownyCitizens)plugin("NeverLandTownyCitizens")).citizens();citizens.assign(mayor.player,town,S,CitizenshipStatus.FOREIGNER,0,"Fixture restricted citizenship");check(!api.isMobilized(S),"citizenship loss immediately removes effective mobilization");citizens.assign(mayor.player,town,S,CitizenshipStatus.CITIZEN,0,"Fixture restored citizenship");check(api.isMobilized(S),"restored citizenship revalidates stored roster");
        for(int i=0;i<3;i++)army.personnel(general.player,S,"warn","Fixture repeated misconduct");check(!api.isMobilized(S)&&ledger.state().soldiers().get(S).suspendedUntil()>System.currentTimeMillis(),"three warnings suspend service and move soldier to reserve");army.personnel(general.player,S,"pardon","Fixture cleared misconduct");army.mobilize(general.player,S,true);
        var menu=new ArmyMenu((NeverLandTownyArmy)plugin("NeverLandTownyArmy"),army);menu.open(soldier.player,"menu",0,null);check(soldier.top.getItem(49)==null&&soldier.top.getItem(19)!=null,"ordinary soldier menu hides command journal");var parent=soldier.top;menu.open(soldier.player,"units",0,parent);menu.click(new InventoryClickEvent(soldier.view,InventoryType.SlotType.CONTAINER,45,ClickType.LEFT,InventoryAction.PICKUP_ALL));check(soldier.top==parent,"menu back arrow returns to actual previous inventory");denied(()->menu.open(soldier.player,"history",0,null),"direct history page rejects non-command user");
        var command=Bukkit.getPluginCommand("townymilitary");var args=command.tabComplete(soldier.player,"army",new String[]{""});check(!args.contains("commission")&&!args.contains("supply"),"ordinary command completion hides privileged actions");
        // Lose a reply after a real provider reservation, then resume the same persisted invoice.
        var services=Bukkit.getServicesManager();var real=services.load(TownyResourcesApi.class);var lost=(TownyResourcesApi)Proxy.newProxyInstance(TownyResourcesApi.class.getClassLoader(),new Class<?>[]{TownyResourcesApi.class},(p,m,a)->{Object v=m.invoke(real,a);if(m.getName().equals("reserveResources")&&Boolean.TRUE.equals(v))throw new IllegalStateException("Lost real reserve reply");return v;});
        long cityBefore=city(Resource.METAL),armyBefore=stock("metal");services.register(TownyResourcesApi.class,lost,plugin("NeverLandTownyResources"),ServicePriority.Highest);
        try{denied(()->army.supply(general.player,"metal",5000),"lost Resources reply leaves recoverable supply intent");}finally{services.unregister(TownyResourcesApi.class,lost);}
        var pending=ledger.state().transfers().values().stream().filter(t->t.phase()==Phase.PLANNED).findFirst().orElseThrow();army.process(pending.id());check(city(Resource.METAL)==cityBefore-5000&&stock("metal")==armyBefore+5000,"native lost-reply recovery never repeats city debit");
        // A consumed external receipt remains held for a real process restart before Army credit.
        var next=ledger.draft();next.transfers.put(P,new Transfer(P,T,G,Map.of("metal",1000L),Phase.PLANNED,System.currentTimeMillis()));ledger.commit(next.freeze());check(real.reserveResources(P,T,Map.of("metal",1000L)),"pending restart fixture reserves real resources");real.settleResources(P,true);
        Files.writeString(proof.resolve("city-metal.txt"),Long.toString(city(Resource.METAL)));Files.writeString(proof.resolve("army-metal.txt"),Long.toString(stock("metal")));
        check(ledger.transfer(P).phase()==Phase.PLANNED&&real.reservationStatus(P).equals("CONSUMED"),"consumed supply awaiting credit is durable across shutdown");
        var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();var worker=new Thread(()->{try{api.garrison(T);}catch(Throwable e){error.set(e);}});worker.start();worker.join(2000);check(error.get() instanceof IllegalStateException,"Army API rejects worker-thread access");denied(()->api.garrison(T).orElseThrow().put("active",999),"garrison snapshot is immutable");denied(()->api.reserves(T).put("metal",0L),"reserve snapshot is immutable");
        check(Bukkit.getPluginCommand("townyarmy").getPlugin().getName().equals("NeverLandTownyBuilds")&&command.getPlugin().getName().equals("NeverLandTownyArmy"),"legacy and new commands have distinct owners without load cycle");
        var papi=plugin("PlaceholderAPI");check(papi.isEnabled(),"PlaceholderAPI enabled beside all addons");
    }
    void restartChecks()throws Exception{
        check(!army.onDuty(S),"duty never restarts automatically");check(ledger.state().soldiers().get(S).rank()==Rank.CORPORAL&&ledger.state().soldiers().get(G).rank()==Rank.GENERAL,"rank and appointed general survive restart");
        long cityBefore=Long.parseLong(Files.readString(proof.resolve("city-metal.txt"))),armyBefore=Long.parseLong(Files.readString(proof.resolve("army-metal.txt")));
        army.process(P);check(ledger.transfer(P).phase()==Phase.CLOSED&&city(Resource.METAL)==cityBefore&&stock("metal")==armyBefore+1000,"real restart recovers consumed supply with one Army credit and no second debit");army.process(P);check(stock("metal")==armyBefore+1000,"recovered supply replay cannot credit twice");
        check(Bukkit.getServicesManager().load(TownArmyApi.class).legacyRoster().isEmpty()&&ledger.state().soldiers().size()==3,"legacy roster retirement persists without duplicate import");
        check(ledger.state().cities().get(T).base()!=null&&api.history(T).size()>10,"base, reserves and audit survive restart");
        var general=new Actor(G);army.assign(general.player,S,Unit.CAVALRY,"Fixture transfer after restart");check(ledger.state().soldiers().get(S).unit()==Unit.CAVALRY&&ledger.state().soldiers().get(S).equipment()==0&&!api.isMobilized(S),"unit transfer resets kit and returns soldier to reserve");
        army.personnel(general.player,S,"dismiss","Fixture service termination");check(!api.isMobilized(S)&&ledger.state().soldiers().get(S).status()==Status.DISCHARGED,"dismissal revokes live mobilization");
        long before=System.nanoTime();for(int i=0;i<100;i++)api.garrison(T);Files.writeString(proof.resolve("garrison-100-ms.txt"),Double.toString((System.nanoTime()-before)/1e6));check(api.healthy(),"100 repeated native garrison queries preserve healthy state");
    }
    void contracts()throws Exception{try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getResource("contracts.tsv"),java.nio.charset.StandardCharsets.UTF_8))){var rows=reader.lines().toList();check(rows.size()==expected("contracts"),"all public contracts from source inventory");for(String line:rows){String[] row=line.split("\t");var p=plugin(row[0]);Class<?> type=Class.forName(row[1],true,p.getClass().getClassLoader());Object provider=Bukkit.getServicesManager().load(type);check(provider!=null&&p.isEnabled()&&p.getDescription().getVersion().equals(row[3])&&Integer.valueOf(1).equals(type.getMethod("apiVersion").invoke(provider))&&new HashSet<>(Arrays.asList(row[2].split(","))).equals(type.getMethod("capabilities").invoke(provider)),"provider ABI/capabilities: "+row[1]);}}}
    private final class Actor {
        final Player player; PermissibleBase permissions; Inventory top=Bukkit.createInventory(null,9,Component.text("Fixture")); final Inventory bottom=Bukkit.createInventory(null,36);
        InventoryView view; final List<String> messages=new ArrayList<>();
        Actor(UUID id) {
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->switch(m.getName()) {
                case "getUniqueId"->id;case "getName"->TownyAPI.getInstance().getResident(id).getName();case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->cell;
                case "isOnline"->true;case "isDead","isInsideVehicle"->false;case "getGameMode"->GameMode.SURVIVAL;case "isOp"->false;
                case "hasPermission","isPermissionSet","addAttachment","removeAttachment","recalculatePermissions","getEffectivePermissions"->m.invoke(permissions,a);
                case "sendMessage"->{messages.add(String.valueOf(a[a.length-1]));yield null;}
                case "getOpenInventory"->view;case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,9);yield null;}
                case "hashCode"->id.hashCode();case "equals"->proxy==a[0];case "toString"->"Army actor "+id;default->null;
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
