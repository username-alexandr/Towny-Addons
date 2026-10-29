package ru.neverland.runtime;
import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.locks.*;import java.util.function.*;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.inventory.*;import org.bukkit.event.inventory.*;import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.*;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;import ru.neverland.townyarmy.*;import ru.neverland.townyarmy.api.*;import static ru.neverland.townyarmy.ArmyModel.*;
import ru.neverland.townybuilds.api.TownArmyApi;import ru.neverland.townybuilds.data.DataStore;import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townyresources.api.TownyResourcesApi;import ru.neverland.townyresources.service.ResourcesService;import ru.neverland.townyresources.model.Resource;
import ru.neverland.townysieges.*;
/** Native services and file writers. Only online actors/schedulers and accepted building/war fixtures are substituted. */
public final class ArmyAsyncProbe extends JavaPlugin {
 static UUID id(String name){return UUID.nameUUIDFromBytes(("army-async-052-"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static final UUID T=id("town"),M=id("mayor"),S=id("soldier"),D=id("defender"),P=id("supply-restart"),W=id("war"),R=id("restart-war");
 Path proof;World world;Location origin;Town town;String phase;int checks;Player mayor,soldier;NeverLandTownyArmy owner;ArmyService army;ArmyRepository ledger;NeverLandTownySiegesPlus siege;CampSupplies camp;CampSupplyLedger campLedger;ResourcesService resources;CountDownLatch release;
 final Map<UUID,Inventory> windows=new HashMap<>();final List<String> messages=new ArrayList<>();
 interface Work{void run()throws Exception;}
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 static Object invoke(Object o,String name,Class<?>[] signature,Object...args)throws Exception{var m=o.getClass().getDeclaredMethod(name,signature);m.setAccessible(true);try{return m.invoke(o,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
 JavaPlugin plugin(String shortName){return(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTowny"+shortName);}
 void check(boolean ok,String why)throws Exception{if(!ok)throw new AssertionError(why);checks++;Files.writeString(proof.resolve(phase+"-checks.txt"),"PASS "+why+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+why);}
 void denied(Work work,String why)throws Exception{boolean no=false;try{work.run();}catch(Exception expected){no=true;}check(no,why);}
 void in(Work w){try{w.run();}catch(Throwable error){fail(error);}}
 void later(long ticks,Work w){Bukkit.getRegionScheduler().runDelayed(this,origin,t->{if(!SuiteState.tryRun("Army native proof",()->in(w)))later(1,w);},ticks);}
 <V>void await(CompletionStage<V> future,Consumer<V> next){future.whenComplete((v,error)->{if(error!=null)fail(error);else later(1,()->next.accept(v));});}
 void idle(Work next){idle(next,300);}void idle(Work next,int left){later(1,()->{if(!army.busy()){next.run();return;}if(left==0)throw new AssertionError("army write timeout");idle(next,left-1);});}
 void fail(Throwable error){if(release!=null)release.countDown();getLogger().log(java.util.logging.Level.SEVERE,"ARMY ASYNC PROBE FAILED",error);try{Files.writeString(proof.resolve("failed.txt"),error.toString());}catch(Exception ignored){}Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
 @Override public void onLoad(){if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||Files.exists(getDataFolder().toPath().resolve("first-passed.txt")))return;try{var y=new org.bukkit.configuration.file.YamlConfiguration();for(UUID u:List.of(M,S))y.set("ages."+u,25);y.createSection("soldiers");y.set("retired",false);var p=Path.of("plugins/NeverLandTownyBuilds/army-data.yml");Files.createDirectories(p.getParent());y.save(p.toFile());}catch(Exception e){throw new IllegalStateException(e);}}
 @Override public void onEnable(){if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}proof=getDataFolder().toPath();Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{Files.createDirectories(proof);phase=Files.exists(proof.resolve("first-passed.txt"))?"restart":"first";world=Bukkit.getWorlds().getFirst();origin=new Location(world,8.5,120,8.5);world.getChunkAtAsync(0,0).whenComplete((chunk,error)->{if(error!=null)fail(error);else later(3,this::setup);});}catch(Throwable error){fail(error);}},40);}
 @SuppressWarnings("unchecked") Player actor(UUID uuid,String name){var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(p,m,a)->switch(m.getName()){case "runDelayed"->Bukkit.getRegionScheduler().runDelayed(this,origin,(Consumer<ScheduledTask>)a[1],(Long)a[3]);case "run"->Bukkit.getRegionScheduler().run(this,origin,(Consumer<ScheduledTask>)a[1]);default->throw new UnsupportedOperationException(m.getName());});return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
 case "getUniqueId"->uuid;case "getName"->name;case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp","isDead","isInsideVehicle"->false;case "getGameMode"->GameMode.SURVIVAL;case "getScheduler"->scheduler;case "hasPermission"->true;case "sendMessage"->{messages.add(name+":"+String.valueOf(a[a.length-1]));yield null;}case "openInventory"->{windows.put(uuid,(Inventory)a[0]);yield null;}case "getOpenInventory"->view((Player)p,uuid);case "closeInventory"->{windows.remove(uuid);yield null;}case "hashCode"->uuid.hashCode();case "equals"->p==a[0];default->null;});}
 InventoryView view(Player player,UUID uuid){return(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,m,a)->switch(m.getName()){case "getTopInventory"->windows.computeIfAbsent(uuid,k->Bukkit.createInventory(null,9));case "getPlayer"->player;case "getType"->InventoryType.CHEST;case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];default->null;});}
 void setup()throws Exception{
  for(String name:List.of("Builds","Resources","Citizens","Army","SiegesPlus"))check(plugin(name).isEnabled()&&!SuiteState.frozen(plugin(name).getName()),"native provider enabled and unfrozen: "+name);
  owner=(NeverLandTownyArmy)plugin("Army");army=(ArmyService)Bukkit.getServicesManager().load(TownyArmyApi.class);ledger=army.repository();var scheduler=Class.forName("ru.neverland.core.SuiteScheduler",true,owner.getClass().getClassLoader());scheduler.getMethod("cancelTasks",org.bukkit.plugin.Plugin.class).invoke(scheduler.getMethod("get").invoke(null),owner);siege=(NeverLandTownySiegesPlus)plugin("SiegesPlus");((org.bukkit.scheduler.BukkitTask)field(siege,"task")).cancel();((org.bukkit.scheduler.BukkitTask)field(siege,"lootTask")).cancel();camp=siege.supplies();campLedger=(CampSupplyLedger)field(camp,"ledger");resources=(ResourcesService)field(plugin("Resources"),"service");((org.bukkit.scheduler.BukkitTask)field(resources,"task")).cancel();
  check(owner.getDescription().getVersion().equals("0.2.2")&&siege.getDescription().getVersion().equals("0.6.4"),"exact paired Army0.2.2/Sieges0.6.4 load on Folia");
  mayor=actor(M,"ArmyAckMayor");soldier=actor(S,"ArmyAckSoldier");idle(()->{if(!army.healthy()){await(army.migrate(),v->idle(this::ready));return;}ready();});
 }
 void ready()throws Exception{
  check(ledger.state().imported()&&ledger.state().handoff()&&Bukkit.getServicesManager().load(TownArmyApi.class).legacyRoster().isEmpty(),"legacy import and retirement acknowledged before army actions");
  if(phase.equals("restart")){town=TownyAPI.getInstance().getTown(T);resumed();return;}
  var u=TownyUniverse.getInstance();u.newTownInternal("ArmyAckCity",T);town=TownyAPI.getInstance().getTown(T);for(var e:Map.of(M,"ArmyAckMayor",S,"ArmyAckSoldier").entrySet()){var r=u.getDataSource().newResident(e.getValue(),e.getKey());r.setTown(town);r.save();}town.setMayor(TownyAPI.getInstance().getResident(M));var claim=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(origin);town.save();
  var data=(DataStore)field(plugin("Builds"),"dataStore");for(String name:List.of("army","barracks","stables")){data.town(T).setLevel(name,3);data.town(T).setConstructionSite(new ConstructionSite(name,world.getUID(),8,120,8,org.bukkit.block.BlockFace.NORTH,3,3,1,false));}data.markDirty();data.saveOrThrow();
  invoke(resources,"refresh",new Class<?>[]{boolean.class},false);for(Resource r:Resource.values())resources.adjust(T,r,Math.min(200000,resources.resources(T).orElseThrow().capacity().get(r)),"set");
  check(new ArmyBridge().levels(T,army.settings()).get("army")==3&&new ArmyBridge().age(S).orElse(-1)==25,"real Builds supplies operational HQ and retained verified age");
  await(army.apply(soldier,Unit.INFANTRY),v->in(()->{denied(()->army.approve(soldier,S),"own admission refused even with wildcard permissions");await(army.approve(mayor,S),a->in(()->await(army.base(mayor),b->in(()->await(army.oath(soldier),c->in(this::supplies))))));}));
 }
 long city(){return resources.resources(T).orElseThrow().state().balances().get(Resource.METAL);}long stock(String type){return army.reserves(T).getOrDefault(type,0L);}
 void supplies()throws Exception{
  check(ledger.state().soldiers().get(S).status()==Status.RESERVE,"native application, admission, base and oath persist in order");long before=city();await(army.supply(mayor,"metal",10000),id->in(()->{
    check(ledger.transfer(id).phase()==Phase.PLANNED&&city()==before&&stock("metal")==0,"supply intent ACK precedes any city withdrawal or army credit");
    await(army.process(id),v->in(()->{check(ledger.transfer(id).phase()==Phase.APPLIED&&city()==before-10000&&stock("metal")==10000,"real Resources debit and acknowledged army credit occur once");await(army.process(id),x->in(()->{check(ledger.transfer(id).phase()==Phase.CLOSED,"credit receipt remains until provider forget and close ACK");await(army.process(id),y->in(()->{check(city()==before-10000&&stock("metal")==10000,"closed supply replay makes no second debit or credit");seedStock();}));}));}));
  }));
 }
 void seedStock()throws Exception{var d=ledger.draft();d.cities.put(T,d.city(T).stock(Map.of("metal",10000L,"materials",10000L,"food",10000L,"water",10000L,"knowledge",10000L)));await(ledger.commit(d),v->in(this::menuAndDuty));}
 @SuppressWarnings("unchecked") ReentrantLock writerLock()throws Exception{var type=Class.forName("ru.neverland.core.AtomicFiles",true,owner.getClass().getClassLoader());var f=type.getDeclaredField("writers");f.setAccessible(true);return ((Map<Path,ReentrantLock>)f.get(null)).computeIfAbsent(owner.getDataFolder().toPath().resolve("army-data.yml").toAbsolutePath().normalize(),p->new ReentrantLock());}
 void holdWriter()throws Exception{var lock=writerLock();release=new CountDownLatch(1);var entered=new CountDownLatch(1);new Thread(()->{lock.lock();entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{lock.unlock();}},"army-native-delayed-file").start();if(!entered.await(1,TimeUnit.SECONDS))throw new AssertionError("file lock unavailable");}
 void menuAndDuty()throws Exception{
  army.duty(soldier,true);Object session=((Map<?,?>)field(army,"duty")).get(S);set(session,"accounted",System.currentTimeMillis()-2000);set(session,"moved",System.currentTimeMillis());
  var menu=new ArmyMenu(owner,army);menu.record(mayor,S,null);check(windows.get(M).getItem(34).getType()==Material.IRON_CHESTPLATE,"real command record exposes equipment action");holdWriter();long metal=stock("metal");var click=new InventoryClickEvent(view(mayor,M),InventoryType.SlotType.CONTAINER,34,ClickType.LEFT,InventoryAction.PICKUP_ALL);menu.click(click);
  later(3,()->{check(click.isCancelled()&&army.busy()&&army.healthy()&&stock("metal")==metal&&ledger.state().soldiers().get(S).equipment()==0,"actual click cannot publish equipment or spend stock before delayed disk ACK");
    check(army.garrison(T).isPresent()&&army.roster(T).size()==1&&army.serviceRecord(S).isPresent(),"read-only APIs remain usable during accepted write");denied(()->army.equip(mayor,S),"second equipment click rejected while first is saving");army.leave(S);check(!army.onDuty(S),"quitting stops duty immediately during another write");release.countDown();idle(()->{check(ledger.state().soldiers().get(S).equipment()==100&&stock("metal")==metal-2000,"equipment and exact kit debit publish together after ACK");army.pulse();idle(()->{check(ledger.state().soldiers().get(S).dutyMillis()>=2000&&!((Map<?,?>)field(army,"duty")).containsKey(S),"quit-time service credit survives outstanding equipment write");idleDuty();});});
  });
 }
 void idleDuty()throws Exception{
  army.duty(soldier,true);Object session=((Map<?,?>)field(army,"duty")).get(S);long start=System.currentTimeMillis()-2*army.settings().dutyIdle();set(session,"accounted",start);set(session,"moved",start);long before=ledger.state().soldiers().get(S).dutyMillis();
  army.moved(soldier,origin,origin.clone().add(1,0,0));check(!army.onDuty(S),"movement after expired idle deadline cannot revive an AFK duty session");army.pulse();idle(()->{check(ledger.state().soldiers().get(S).dutyMillis()-before==army.settings().dutyIdle(),"late movement never credits service beyond the idle deadline");raidSupply();});
 }
 RaidStore.Raid war(UUID id,long starts){return new RaidStore.Raid(id,T,D,M,starts,starts,starts+3600000,starts+7200000,"OPEN");}
 void fund(RaidStore.Raid war)throws Exception{invoke(camp,"fund",new Class<?>[]{RaidStore.Raid.class},war);}
 void settle(CampSupplyLedger.Funding f,boolean accept)throws Exception{invoke(camp,"completeArmy",new Class<?>[]{CampSupplyLedger.Funding.class,boolean.class},f,accept);}
 void raidSupply()throws Exception{
  var war=war(W,System.currentTimeMillis());long before=stock("food");denied(()->army.consumeRaidSupplies(T,id("old-caller"),Map.of("food",1000L)),"legacy boolean API cannot enqueue an unobserved debit");holdWriter();fund(war);var f=campLedger.state().funding().get(W);
  later(3,()->{check(f.state().equals("PLANNED")&&!camp.supplied(war)&&stock("food")==before&&!army.raidSupplyReceipt(T,f.id(),f.cost()),"camp stays unsupplied while Army disk receipt is pending");fund(war);check(stock("food")==before,"camp polling cannot duplicate a pending debit");release.countDown();idle(()->{settle(f,true);check(camp.supplied(war)&&stock("food")==before-1000&&army.raidSupplyReceipt(T,f.id(),f.cost()),"camp becomes supplied only after exact Army debit receipt");fund(war);check(stock("food")==before-1000,"paid raid minute replays without charging again");expiredMinute();});});
 }
 void expiredMinute()throws Exception{
  var stale=new CampSupplyLedger.Funding(id("stale-minute"),T,0,"ARMY","PLANNED",Map.of("food",1000L,"metal",250L));campLedger.funding(stale,false);long before=stock("food");holdWriter();var active=war(stale.war(),System.currentTimeMillis()-120000);fund(active);var current=campLedger.state().funding().get(stale.war());
  check(current.minute()==2&&!current.id().equals(stale.id())&&stock("food")==before,"expired never-accepted minute is replaced before requesting current minute");release.countDown();idle(()->{settle(current,true);check(stock("food")==before-1000&&!army.raidSupplyReceipt(T,stale.id(),stale.cost()),"active war charges only current minute, never the missed minute");endedChecks();});
 }
 void endedChecks()throws Exception{
  var old=new CampSupplyLedger.Funding(id("expired-war"),T,0,"ARMY","PLANNED",Map.of("food",1000L));campLedger.funding(old,false);long before=stock("food");set(siege.raids(),"raids",List.of(war(old.war(),System.currentTimeMillis()-120000)));camp.reload(new CampSupplies.Settings(false,Map.of("BREAD",2L,"IRON_INGOT",1L),Map.of("food",1000L,"metal",250L)));camp.pulse();check(!campLedger.state().funding().containsKey(old.war())&&stock("food")==before,"disabled camp clears never-accepted intent without retrospective charge");
  camp.reload(new CampSupplies.Settings(true,Map.of("BREAD",2L,"IRON_INGOT",1L),Map.of("food",1000L,"metal",250L)));var ended=new CampSupplyLedger.Funding(id("ended-war"),T,0,"ARMY","PLANNED",Map.of("food",1000L));campLedger.funding(ended,false);set(siege.raids(),"raids",List.of());camp.pulse();check(!campLedger.state().funding().containsKey(ended.war())&&stock("food")==before,"ended war never starts a new army debit");unknownReply();
 }
 void unknownReply()throws Exception{
  var services=Bukkit.getServicesManager();var real=services.load(TownyArmyApi.class);var broken=(TownyArmyApi)Proxy.newProxyInstance(TownyArmyApi.class.getClassLoader(),new Class<?>[]{TownyArmyApi.class},(p,m,a)->{if(m.getName().equals("requestRaidSupplies"))return CompletableFuture.completedFuture(null);try{return m.invoke(real,a);}catch(InvocationTargetException e){throw e.getCause();}});
  services.register(TownyArmyApi.class,broken,owner,org.bukkit.plugin.ServicePriority.Highest);var war=war(id("unknown-reply"),System.currentTimeMillis());long before=stock("food");
  try{fund(war);var f=campLedger.state().funding().get(war.id());denied(()->settle(f,true),"malformed asynchronous provider reply is not treated as a declined debit");check(campLedger.state().funding().get(war.id()).state().equals("PLANNED")&&stock("food")==before,"unknown reply retains exact pending receipt for reconciliation");}finally{services.unregister(TownyArmyApi.class,broken);}
  settle(campLedger.state().funding().get(war.id()),false);beforeRestart();
 }
 void beforeRestart()throws Exception{
  var d=ledger.draft();d.transfers.put(P,new Transfer(P,T,M,Map.of("metal",1000L),Phase.PLANNED,System.currentTimeMillis()));await(ledger.commit(d),v->in(()->{var api=Bukkit.getServicesManager().load(TownyResourcesApi.class);check(api.reserveResources(P,T,Map.of("metal",1000L)),"restart fixture reserves real city stock");api.settleResources(P,true);
   var f=new CampSupplyLedger.Funding(R,T,0,"ARMY","PLANNED",Map.of("food",1000L));campLedger.funding(f,false);await(army.requestRaidSupplies(T,f.id(),f.cost()),ok->in(()->{check(ok&&campLedger.state().funding().get(R).state().equals("PLANNED"),"accepted Army receipt survives before camp-side publication");Files.writeString(proof.resolve("expected.txt"),city()+"\n"+stock("metal")+"\n"+stock("food")+"\n"+ledger.state().soldiers().get(S).dutyMillis()+"\n");finish();}));
  }));
 }
 void resumed()throws Exception{
  var expected=Files.readAllLines(proof.resolve("expected.txt"));long city=Long.parseLong(expected.get(0)),metal=Long.parseLong(expected.get(1)),food=Long.parseLong(expected.get(2)),time=Long.parseLong(expected.get(3));
  check(!army.onDuty(S)&&ledger.state().soldiers().get(S).equipment()==100&&ledger.state().soldiers().get(S).dutyMillis()==time,"restart retains equipment and earned duty without restarting a session");
  // Normal startup may already have completed pending resource credit; the same ID must remain harmless.
  await(army.process(P),v->in(()->await(army.process(P),x->in(()->{check(ledger.transfer(P).phase()==Phase.CLOSED&&city()==city&&stock("metal")==metal+1000,"restart finishes consumed Resources receipt once without second city debit");
    var f=campLedger.state().funding().get(R);if(f!=null&&f.state().equals("PLANNED"))settle(f,false);check(campLedger.state().funding().get(R).state().equals("PAID")&&stock("food")==food,"ended raid recovers already accepted Army receipt without another charge");
    denied(()->army.consumeRaidSupplies(T,new CampSupplyLedger.Funding(R,T,0,"ARMY","PLANNED",Map.of("food",2000L)).id(),Map.of("food",2000L)),"replayed receipt with changed cost refused after restart");
    var old=new CampSupplyLedger.Funding(id("never-accepted"),T,0,"ARMY","PLANNED",Map.of("food",1000L));campLedger.funding(old,false);settle(old,false);check(stock("food")==food&&!campLedger.state().funding().containsKey(old.war()),"never-accepted expired raid does not charge on recovery");shutdownWrite();
  }))));
 }
 void shutdownWrite()throws Exception{
  army.duty(soldier,true);Object session=((Map<?,?>)field(army,"duty")).get(S);set(session,"accounted",System.currentTimeMillis()-2000);long previous=ledger.state().soldiers().get(S).dutyMillis();
  var lock=writerLock();lock.lock();CompletionStage<Boolean> accepted;try{accepted=army.requestRaidSupplies(T,id("shutdown"),Map.of("food",1000L));long start=System.nanoTime();owner.onDisable();check(System.nanoTime()-start<200_000_000L&&!accepted.toCompletableFuture().isDone(),"onDisable under external Suite returns while actual disk is blocked");}finally{lock.unlock();}
  await(army.stop(),v->in(()->{check(accepted.toCompletableFuture().get()&&ledger.state().soldiers().get(S).dutyMillis()>=previous+2000,"shutdown drains accepted raid debit then final duty credit");check(!ledger.writable(),"closed army rejects subsequent mutations");finish();}));
 }
 void finish()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve(phase+"-passed.txt"),"PASS "+checks+" assertions; native Folia/Towny/Builds/Resources/Army/Sieges, actual inventory, acknowledged stock, real file delay, consumed supply recovery, raid receipt, duty quit and shutdown; offline Player/View/EntityScheduler and accepted infrastructure/war fixtures, no network client or live PvP claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
