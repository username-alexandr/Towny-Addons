package ru.neverland.runtime;
import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.plugin.*;import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.*;import org.bukkit.event.inventory.*;import org.bukkit.permissions.PermissibleBase;
import net.kyori.adventure.text.Component;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.*;
import ru.neverland.mintevents.service.*;import ru.neverland.mintevents.model.*;import ru.neverland.mintevents.command.*;
import ru.neverland.mintevents.gui.*;import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.core.*;import ru.neverland.townycontrol.*;
/** Disposable-only test: active -> disabled JVM -> resumed JVM. */
public final class SafetyRuntimeProbe extends JavaPlugin {
    static UUID id(String key){return UUID.nameUUIDFromBytes(("safety-035-"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID T=id("town"),M=id("mayor");
    Path proof;String phase;int checks;World world;Location cell;Town town;EventService events;EventRepository repository;EventRegistry registry;
    int expected(String key)throws Exception{var p=new Properties();try(var in=getResource("expected-counts.properties")){p.load(in);}return Integer.parseInt(p.getProperty(key));}
    JavaPlugin plugin(String name){return (JavaPlugin)Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(name));}
    static <T>T field(Object owner,Class<T> type)throws Exception{for(var f:owner.getClass().getDeclaredFields())if(type.isAssignableFrom(f.getType())){f.setAccessible(true);return type.cast(f.get(owner));}throw new IllegalArgumentException(type.getName());}
    void check(boolean b,String label)throws Exception{if(!b)throw new AssertionError(label);checks++;Files.writeString(proof.resolve(phase+"-checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+label);}
    boolean permitted(){return Boolean.getBoolean("neverland.runtimeProbe")&&Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))&&"127.0.0.1".equals(getServer().getIp());}
    @Override public void onEnable(){if(!permitted()){Bukkit.getPluginManager().disablePlugin(this);return;}Bukkit.getScheduler().runTaskLater(this,this::run,10);}
    void run(){try{
        proof=getDataFolder().toPath();Files.createDirectories(proof);phase=Files.exists(proof.resolve("disabled-passed.txt"))?"resumed":Files.exists(proof.resolve("first-passed.txt"))?"disabled":"first";
        world=Bukkit.getWorlds().getFirst();cell=new Location(world,220*16+4.5,96,220*16+4.5);
        if(phase.equals("disabled")) {
            long wait=SafeYaml.load(eventFile()).getLong("active."+T+".ends-at")-System.currentTimeMillis();
            if(wait>0 && wait<20000) { Bukkit.getScheduler().runTaskLater(this,this::run,(wait+999)/50+20);return; }
            disabled();
        }else{
            check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==expected("addons"),"all 37 addons enabled");contracts();
            events=field(plugin("NeverLandTownyEvents"),EventService.class);repository=field(plugin("NeverLandTownyEvents"),EventRepository.class);registry=field(plugin("NeverLandTownyEvents"),EventRegistry.class);
            if(phase.equals("first")){setup();first();}else resumed();
        }
        TownyUniverse.getInstance().getDataSource().saveAll();world.save();Files.writeString(proof.resolve(phase+"-passed.txt"),"PASS "+checks+" assertions\n");Bukkit.getScheduler().runTask(this,Bukkit::shutdown);
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SAFETY PROBE FAILED",e);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("failed.txt"),e.toString());}catch(Exception ignored){}Bukkit.shutdown();}}
    void setup()throws Exception{
        var u=TownyUniverse.getInstance();u.newTownInternal("SafetyAudit",T);town=TownyAPI.getInstance().getTown(T);var resident=u.getDataSource().newResident("SafetyMayor",M);resident.setTown(town);town.setMayor(resident);resident.save();
        var claim=new TownBlock(220,220,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();
        world.getChunkAt(220,220).load();cell.getBlock().setType(Material.AIR);cell.getBlock().getRelative(0,-1,0).setType(Material.STONE);town.setSpawn(cell.clone());town.save();town.getAccount().deposit(10000,"disposable safety fixture");Bukkit.getPluginManager().callEvent(new com.palmergames.bukkit.towny.event.NewTownEvent(town));
    }
    void command(String... args){Bukkit.getPluginCommand("townyevents").execute(Bukkit.getConsoleSender(),"townyevents",args);}
    void control(String... args){Bukkit.getPluginCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",args);}
    Path eventFile(){return plugin("NeverLandTownyEvents").getDataFolder().toPath().resolve("events-data.yml");}
    Path stateFile(){return ModulePauseStore.file(plugin("NeverLandTownyEvents").getDataFolder().toPath().getParent());}
    void first()throws Exception{
        adminCoverage();
        check(events.shieldRemainingMillis(T)>86_300_000&&events.shieldRemainingMillis(T)<=86_400_000,"new Towny town receives 24h shield from original creation date");
        check(!events.startEvent(town,registry.get("drought")),"hostile event respects newbie shield");
        check(events.startEvent(town,registry.get("festival")),"peaceful festival remains available under shield");events.cancel(town);
        command("start","drought",town.getName());check(events.active(T)==null,"admin normal start also respects shield");
        command("start","drought",town.getName(),"--force");check(events.active(T)!=null,"explicit admin force bypasses shield");
        int history=events.history(T).size();long started=events.active(T).startedAt();events.contribute(town,7);
        var actor=new Actor(M);var admin=Bukkit.getPluginCommand("townyevents");admin.execute(actor.player,"townyevents",new String[]{"stop",town.getName()});
        check(events.active(T)!=null,"ordinary player cannot cancel");check(admin.getTabCompleter().onTabComplete(actor.player,admin,"townyevents",new String[]{""}).isEmpty(),"admin completions hidden from players");
        var control=Bukkit.getPluginCommand("nltmodules");control.execute(actor.player,"nltmodules",new String[]{"disable","Events"});check(plugin("NeverLandTownyEvents").isEnabled(),"ordinary player cannot disable addons");
        command("restart",town.getName());check(events.active(T).progress()==7&&events.active(T).startedAt()==started,"restart keeps contributions and incident identity");
        command("pause",town.getName());check(events.paused(T)&&events.activeEvent(T).isEmpty()&&events.productionMultiplier(T,"agrarian_complex")==1,"paused event is neutral through public APIs");
        check(events.contribute(town,10)<0&&events.active(T).progress()==7,"paused event refuses contributions");
        var menus=new EventMenuManager(plugin("NeverLandTownyEvents"),new TownyHook(),events,field(plugin("NeverLandTownyEvents"),MessageService.class));menus.open(actor.player);
        int slot=((EventMenuHolder)actor.top.getHolder()).rules().keySet().stream().findFirst().orElseThrow();
        menus.onClick(new InventoryClickEvent(actor.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL));
        check(events.active(T).progress()==7&&actor.messages.stream().anyMatch(m->m.contains("паузе")),"native paused menu refuses contribution before inventory access");
        long remaining=events.active(T).secondsLeft(System.currentTimeMillis());command("extend","2",town.getName());check(events.active(T).secondsLeft(System.currentTimeMillis())==remaining+120,"admin extension while paused");
        command("resume",town.getName());check(!events.paused(T)&&events.active(T).progress()==7,"resume keeps progress");
        command("stop",town.getName());check(events.active(T)==null&&events.history(T).size()==history,"default stop never adds a failed history entry");
        command("start","drought",town.getName(),"--force");command("fail",town.getName());check(events.history(T).size()==history+1&&!events.history(T).get(0).success(),"failure requires explicit administrative command");
        command("start","drought",town.getName(),"--force");command("stop",town.getName(),"success");check(events.history(T).get(0).success(),"legacy explicit success option remains compatible");
        command("shield","0",town.getName());check(events.shieldRemainingMillis(T)==0,"admin can remove shield");command("shield","24",town.getName());check(events.shieldRemainingMillis(T)>86_300_000,"admin can restore shield");
        // No spawning required: a saved raid can retain unfinished enemies without duplicating defeated ones.
        var raid=new ActiveEvent(T,"raid",System.currentTimeMillis(),System.currentTimeMillis()+60000,23,100,.1,0);raid.raid(new RaidState());repository.put(raid);repository.save();UUID generation=raid.raid().generation();
        command("restart",town.getName());check(events.active(T).progress()==23&&!events.active(T).raid().generation().equals(generation),"raid restart invalidates old bodies without erasing kill progress");
        command("cancel",town.getName());check(!Files.readString(eventFile()).contains("RAID_DEFEAT"),"neutral raid cancellation emits no defeat receipt");
        // Independent leaf stops without taking the economy down; enable is deferred to server restart.
        control("disable","Stick");check(!plugin("NeverLandTownyStick").isEnabled()&&plugin("NeverLandTownyEvents").isEnabled(),"independent module can be disabled alone");
        control("enable","Stick");check(!plugin("NeverLandTownyStick").isEnabled(),"enable never attempts unsafe hot plugin reload");
        command("start","drought",town.getName(),"--force");events.contribute(town,11);ActiveEvent live=events.active(T);repository.replace(live,live.reschedule(System.currentTimeMillis()+15000,0),0);
        Files.writeString(proof.resolve("identity.txt"),Long.toString(live.startedAt()));Files.writeString(proof.resolve("history.txt"),Integer.toString(events.history(T).size()));Files.writeString(proof.resolve("balance.txt"),Double.toString(town.getAccount().getHoldingBalance()));
        actor.body.remove();control("disable","Events");
        var state=ModulePauseStore.load(stateFile());check(state.requests().equals(Set.of("NeverLandTownyEvents")),"explicit pause root recorded atomically");
        for(var e:state.modules().entrySet())if(e.getValue().disabled())check(!plugin(e.getKey()).isEnabled(),"dependent stopped: "+e.getKey());
        check(!plugin("NeverLandTownyResources").isEnabled(),"dependent production cannot run on missing events API");
        Files.writeString(proof.resolve("disabled-events.yml"),Files.readString(eventFile()));
    }
    void disabled()throws Exception{
        var state=ModulePauseStore.load(stateFile());
        for(var e:state.modules().entrySet())if(e.getValue().disabled())check(!plugin(e.getKey()).isEnabled(),"disabled before repository startup: "+e.getKey());
        check(plugin("NeverLandTownyStick").isEnabled(),"independent module restored on restart");
        check(Files.readString(eventFile()).equals(Files.readString(proof.resolve("disabled-events.yml"))),"disabled startup never mutates progress or failure history");
        var y=SafeYaml.load(eventFile());check(y.getLong("active."+T+".ends-at")<System.currentTimeMillis(),"fixture deadline really expired during module downtime");
        var zombie=world.spawn(cell,Zombie.class);zombie.getPersistentDataContainer().set(new NamespacedKey("neverlandtownyexpeditions","expedition_id"),org.bukkit.persistence.PersistentDataType.STRING,id("expedition").toString());
        var victim=world.spawn(cell,Pig.class);var damage=new org.bukkit.event.entity.EntityDamageByEntityEvent(zombie,victim,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);Bukkit.getPluginManager().callEvent(damage);
        // Expeditions may be independent of Events; explicitly pause it to exercise dormant bodies.
        control("disable","Expeditions");damage=new org.bukkit.event.entity.EntityDamageByEntityEvent(zombie,victim,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);Bukkit.getPluginManager().callEvent(damage);check(damage.isCancelled(),"dormant expedition enemies cannot damage players or animals");zombie.remove();victim.remove();
        control("enable","all");check(!plugin("NeverLandTownyEvents").isEnabled(),"resume scheduled, inactive until next JVM");
    }
    void resumed()throws Exception{
        adminResumed();
        town=TownyAPI.getInstance().getTown(T);var live=events.active(T);
        check(live!=null&&live.progress()==11&&live.startedAt()==Long.parseLong(Files.readString(proof.resolve("identity.txt"))),"active event and progress survive disable and two JVM restarts");
        check(live.endsAt()>System.currentTimeMillis()&&live.endsAt()-System.currentTimeMillis()<=15000,"expired real deadline restored with remaining playable time");
        check(events.history(T).size()==Integer.parseInt(Files.readString(proof.resolve("history.txt"))),"no downtime defeat in history");
        check(town.getAccount().getHoldingBalance()==Double.parseDouble(Files.readString(proof.resolve("balance.txt"))),"Towny money unchanged by disabling and resuming");
        check(events.shieldRemainingMillis(T)>86_300_000,"newbie shield retains time through module pause");
        check(ModulePauseStore.load(stateFile()).modules().values().stream().allMatch(e->!e.disabled()&&e.pausedAt()==0),"all resume receipts acknowledged once");
        command("cancel",town.getName());check(events.active(T)==null,"resumed event still supports neutral cancellation");
    }
    Object invoke(Object object,String method)throws Exception{return object.getClass().getMethod(method).invoke(object);}
    long left(Object object)throws Exception{Object t=invoke(object,"timer");return (Long)t.getClass().getMethod("remaining",long.class).invoke(t,System.currentTimeMillis());}
    boolean paused(Object object)throws Exception{return (Boolean)invoke(invoke(object,"timer"),"paused");}
    void admin(String... args){Bukkit.getPluginCommand("nltadmin").execute(Bukkit.getConsoleSender(),"nltadmin",args);}
    void timed(String module,Object object,UUID identity)throws Exception {
        String id=identity.toString();admin(module,"pause",id);check(paused(object),module+" native pause");long frozen=left(object);
        admin(module,"extend","2",id);check(left(object)==frozen+120000,module+" native extension while paused");
        admin(module,"restart",id);check(paused(object)&&left(object)==60000,module+" restart keeps paused state and original duration");
        admin(module,"resume",id);check(!paused(object)&&left(object)>59000,module+" native resume");
        admin(module,"pause",id);check(paused(object),module+" paused again for multi-JVM recovery");
        var y=Files.exists(proof.resolve("admin-timers.yml"))?SafeYaml.load(proof.resolve("admin-timers.yml")):new org.bukkit.configuration.file.YamlConfiguration();
        y.set(module,left(object));y.save(proof.resolve("admin-timers.yml").toFile());
    }
    void adminCoverage()throws Exception {
        long now=System.currentTimeMillis();var actor=new Actor(M);
        var router=Bukkit.getPluginCommand("nltadmin");router.execute(actor.player,"nltadmin",new String[]{"Events","module","disable"});check(plugin("NeverLandTownyEvents").isEnabled(),"router cannot bypass admin permission");
        check(router.tabComplete(actor.player,"nltadmin",new String[]{""}).isEmpty(),"router completions hidden without permission");
        var researchCommand=Bukkit.getPluginCommand("townyresearch");actor.permissions.addAttachment(this,"neverlandtownyresearch.use",true);
        check(researchCommand.tabComplete(actor.player,"townyresearch",new String[]{""}).contains("start")&&!researchCommand.tabComplete(actor.player,"townyresearch",new String[]{""}).contains("admin"),"player completions preserved without exposing admin namespace");
        check(NewcomerProtection.remaining(plugin("NeverLandTownyCrime"),T)>86300000,"Crime shares Events newcomer shield");
        check(NewcomerProtection.remaining(plugin("NeverLandTownyEspionage"),T)>86300000,"Espionage shares Events newcomer shield");
        TownyUniverse.getInstance().newTownInternal("SafetyTarget",id("target"));var targetTown=TownyAPI.getInstance().getTown(id("target"));targetTown.save();
        var spyService=field(plugin("NeverLandTownyEspionage"),ru.neverland.mintespionage.service.EspionageService.class);
        var attempt=spyService.start(actor.player,targetTown,spyService.registry().get("reconnaissance"));
        check(attempt.status().name().equals("NEWCOMER_PROTECTED")&&town.getAccount().getHoldingBalance()==10000,"protected spy start is rejected before charging the town");
        var crime=field(plugin("NeverLandTownyCrime"),ru.neverland.townycrime.CrimeService.class);var pulse=crime.getClass().getDeclaredMethod("pulse");pulse.setAccessible(true);pulse.invoke(crime);var state=crime.crime(T).orElseThrow();
        check(Boolean.TRUE.equals(state.get("newcomerProtected"))&&Integer.valueOf(10000).equals(state.get("incomeBasisPoints"))&&((Map<?,?>)state.get("incident")).isEmpty(),"protected Crime creates no incident and applies no shop income penalty");
        var spyRepo=field(plugin("NeverLandTownyEspionage"),ru.neverland.mintespionage.service.EspionageRepository.class);
        var spy=new ru.neverland.mintespionage.model.SpyOperation(id("spy"),T,town.getName(),id("target"),"Target",M,"SafetyMayor","reconnaissance",now,now+60000,.5,.5,0,ru.neverland.mintespionage.model.OperationStatus.ACTIVE,false);spyRepo.add(spy);spyRepo.save();timed("Espionage",spy,spy.id());
        var expRepo=field(plugin("NeverLandTownyExpeditions"),ru.neverland.mintexpeditions.service.ExpeditionRepository.class);
        var pos=new ru.neverland.mintexpeditions.model.BlockPos(cell.getBlockX(),cell.getBlockY(),cell.getBlockZ());
        var expedition=new ru.neverland.mintexpeditions.model.ActiveExpedition(id("admin-expedition"),M,"admin-fixture",world.getUID(),world.getName(),pos,pos,now,now+60000);expedition.participants().add(M);expedition.kills(3);expRepo.put(expedition);expRepo.save();timed("Expeditions",expedition,expedition.id());
        var mob=world.spawn(cell,Zombie.class);mob.getPersistentDataContainer().set(new NamespacedKey(plugin("NeverLandTownyExpeditions"),"expedition_id"),org.bukkit.persistence.PersistentDataType.STRING,expedition.id().toString());expedition.spawnedMobs().add(mob.getUniqueId());
        var damage=new org.bukkit.event.entity.EntityDamageEvent(mob,org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE,4);Bukkit.getPluginManager().callEvent(damage);check(damage.isCancelled(),"paused expedition enemies are invulnerable");mob.remove();expedition.spawnedMobs().clear();expRepo.save();
        var govRepo=field(plugin("NeverLandTownyGovernance"),ru.neverland.governance.service.GovernanceRepository.class);
        var proposal=new ru.neverland.governance.model.Proposal(id("proposal"),T,town.getName(),"admin-fixture",ru.neverland.governance.model.ProposalAction.ENACT,M,"SafetyMayor",now,now+60000,ru.neverland.governance.model.ProposalStatus.OPEN);proposal.vote(M,ru.neverland.governance.model.VoteChoice.YES);govRepo.add(proposal);govRepo.save();timed("Governance",proposal,proposal.id());
        var contractRepo=field(plugin("NeverLandTownyContracts"),ru.neverland.mintcontracts.service.ContractRepository.class);
        var contract=new ru.neverland.mintcontracts.model.ActiveContract(id("contract"),T,"admin-fixture",now,now+60000,2,10,0,Map.of(M,2));contract.snapshot(new ru.neverland.mintcontracts.model.ContractDefinition("admin-fixture","Fixture",ru.neverland.mintcontracts.model.ContractType.MOB_KILL,Material.STONE,0,List.of(),"ANY",null,10,0,60));contractRepo.add(contract);contractRepo.saveOrThrow();timed("Contracts",contract,contract.id());check(contract.progress()==2&&contract.contributions().get(M)==2&&contract.escrow()==0,"contract restart preserves contributions and escrow");
        var tradeRepo=field(plugin("NeverLandTownyTrade"),ru.neverland.minttrade.service.TradeRepository.class);
        var caravan=new ru.neverland.minttrade.model.Caravan(id("caravan"),T,id("target"),"fixture",new ItemStack(Material.STONE),5,5,0,0,Map.of(),List.of(),now,now+60000,now+30000,false,true,ru.neverland.minttrade.model.CaravanStatus.ACTIVE);caravan.settlement("ACTIVE");tradeRepo.add(caravan);tradeRepo.save();UUID receipt=caravan.operation("debit");timed("Trade",caravan,caravan.id());check(caravan.operation("debit").equals(receipt)&&caravan.remainingCargo()==5&&caravan.incidentHandled(),"caravan restart keeps cargo, debit identity and incident receipt");
        var studyRepo=field(field(plugin("NeverLandTownyResearch"),ru.neverland.townyresearch.service.ResearchService.class),ru.neverland.townyresearch.data.ResearchRepository.class);
        var study=new ru.neverland.townyresearch.model.CityStudy.Study(id("study"),"irrigation",1,1000,60,37,Map.of("great_library",1),ru.neverland.townyresearch.model.CityStudy.Phase.PREPARED);studyRepo.put(T,ru.neverland.townyresearch.model.CityStudy.empty().study(study));
        admin("Research","pause",study.invoice().toString());check(studyRepo.get(T).active().phase().name().equals("PAUSED")&&studyRepo.get(T).active().remaining()==37,"research native pause preserves invoice and progress");admin("Research","restart",study.invoice().toString());check(studyRepo.get(T).active().remaining()==37,"research refuses unsupported restart");
        var builds=field(plugin("NeverLandTownyBuilds"),ru.neverland.townybuilds.data.DataStore.class);
        var site=new ru.neverland.townybuilds.construction.ConstructionSite("warehouse",world.getUID(),cell.getBlockX()+5,96,cell.getBlockZ(),org.bukkit.block.BlockFace.NORTH,0,1,1,true);builds.town(T).setConstructionSite(site);builds.markDirty();builds.save();admin("Builds","pause",T+"/warehouse");check(site.adminPaused()&&site.targetStage()==1,"construction pauses without resetting the paid stage");
        var logistics=field(plugin("NeverLandTownyLogistics"),ru.neverland.townylogistics.service.LogisticsService.class);
        var route=new ru.neverland.townylogistics.model.Network.Route("fixture","hub","from","to","",64,0,true);var nodes=new HashMap<String,ru.neverland.townylogistics.model.Network.Node>();for(String name:List.of("hub","from","to"))nodes.put(name,new ru.neverland.townylogistics.model.Network.Node(name,"warehouse",ru.neverland.townylogistics.model.Network.Kind.BUILDING,new ru.neverland.townylogistics.model.Position(world.getUID(),cell.getX(),cell.getY(),cell.getZ())));logistics.change(new ru.neverland.townylogistics.model.Network(T,nodes,Set.of(),Map.of("fixture",route)),false);admin("Logistics","pause",T+"/fixture");check(!logistics.network(T).route("fixture").enabled(),"logistics pauses new dispatches without cancelling cargo");
        var elections=field(plugin("NeverLandTownyElections"),ru.neverland.townyelections.ElectionsService.class);elections.start(town,now);var snapshot=elections.snapshot(T);check(snapshot.get("phase").equals("NOMINATION"),"election fixture starts through native service");admin("Elections","pause",snapshot.get("id"));check(elections.snapshot(T).get("detail").contains("Пауза"),"election native pause is exposed by public API");
        check(town.getAccount().getHoldingBalance()==10000,"administrative time controls never charge Towny money");
    }
    void adminResumed()throws Exception {
        var saved=SafeYaml.load(proof.resolve("admin-timers.yml"));
        var spyRepo=field(plugin("NeverLandTownyEspionage"),ru.neverland.mintespionage.service.EspionageRepository.class);var spy=spyRepo.operation(id("spy"));
        var expRepo=field(plugin("NeverLandTownyExpeditions"),ru.neverland.mintexpeditions.service.ExpeditionRepository.class);var exp=expRepo.active().stream().filter(v->v.id().equals(id("admin-expedition"))).findFirst().orElseThrow();
        var govRepo=field(plugin("NeverLandTownyGovernance"),ru.neverland.governance.service.GovernanceRepository.class);var gov=govRepo.open().stream().filter(v->v.id().equals(id("proposal"))).findFirst().orElseThrow();
        var conRepo=field(plugin("NeverLandTownyContracts"),ru.neverland.mintcontracts.service.ContractRepository.class);var con=conRepo.allActive().stream().filter(v->v.id().equals(id("contract"))).findFirst().orElseThrow();
        var tradeRepo=field(plugin("NeverLandTownyTrade"),ru.neverland.minttrade.service.TradeRepository.class);var caravan=tradeRepo.caravans().stream().filter(v->v.id().equals(id("caravan"))).findFirst().orElseThrow();
        for(var e:Map.of("Espionage",(Object)spy,"Expeditions",exp,"Governance",gov,"Contracts",con,"Trade",caravan).entrySet())check(paused(e.getValue())&&left(e.getValue())==saved.getLong(e.getKey()),e.getKey()+" paused duration survives module shutdown and three JVMs");
        check(exp.kills()==3&&gov.votes().size()==1&&con.progress()==2&&caravan.remainingCargo()==5,"all resumed objectives, votes, contributions and cargo unchanged");
        admin("Espionage","cancel",spy.id().toString());check(spy.status()==ru.neverland.mintespionage.model.OperationStatus.CANCELLED&&!spy.detected(),"spy neutral cancel does not report failure or detection");
        admin("Contracts","cancel",con.id().toString());check(con.administrativeCancellation()&&con.settlementStatus()==ru.neverland.mintcontracts.model.ContractStatus.CANCELLED,"municipal admin cancel records neutral durable settlement");
        admin("Governance","cancel",gov.id().toString());check(govRepo.open().stream().noneMatch(v->v.id().equals(gov.id())),"proposal neutral cancel enacts no law");
        var studies=field(field(plugin("NeverLandTownyResearch"),ru.neverland.townyresearch.service.ResearchService.class),ru.neverland.townyresearch.data.ResearchRepository.class);check(studies.get(T).active().phase().name().equals("PAUSED")&&studies.get(T).active().remaining()==37,"research pause survives JVM restarts");admin("Research","cancel",id("study").toString());check(studies.get(T).active()==null,"unfunded paused research cancels without a debit");
        var builds=field(plugin("NeverLandTownyBuilds"),ru.neverland.townybuilds.data.DataStore.class);check(builds.town(T).constructionSite("warehouse").adminPaused(),"construction pause persists");
        var logistics=field(plugin("NeverLandTownyLogistics"),ru.neverland.townylogistics.service.LogisticsService.class);check(!logistics.network(T).route("fixture").enabled(),"route pause persists");
        var elections=field(plugin("NeverLandTownyElections"),ru.neverland.townyelections.ElectionsService.class);var e=elections.snapshot(T);check(e.get("detail").contains("Пауза"),"election remains paused after restarts");admin("Elections","cancel",e.get("id"));check(TownyAPI.getInstance().getTown(T).getMayor().getUUID().equals(M),"election neutral cancel preserves mayor");
    }
    void contracts()throws Exception{try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getResource("contracts.tsv"),java.nio.charset.StandardCharsets.UTF_8))){var rows=reader.lines().toList();check(rows.size()==expected("contracts"),"all API contracts inventoried");for(String line:rows){String[] row=line.split("\t");var p=plugin(row[0]);Class<?> type=Class.forName(row[1],true,p.getClass().getClassLoader());Object provider=Bukkit.getServicesManager().load(type);check(provider!=null&&p.isEnabled()&&p.getDescription().getVersion().equals(row[3])&&Integer.valueOf(1).equals(type.getMethod("apiVersion").invoke(provider))&&new HashSet<>(Arrays.asList(row[2].split(","))).equals(type.getMethod("capabilities").invoke(provider)),"provider ABI/capabilities: "+row[1]);}}}
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
