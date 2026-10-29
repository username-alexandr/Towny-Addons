package ru.neverland.runtime;
import java.nio.file.*;import java.util.*;import java.util.function.*;import java.lang.reflect.*;
import org.bukkit.*;import org.bukkit.block.BlockFace;import org.bukkit.plugin.java.JavaPlugin;import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.*;import com.palmergames.bukkit.towny.object.economy.*;import com.palmergames.bukkit.towny.object.economy.adapter.EconomyAdapter;import com.palmergames.bukkit.towny.object.economy.provider.EconomyProvider;
import ru.neverland.core.*;import ru.neverland.townybuilds.data.DataStore;import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townyupkeep.api.TownyUpkeepApi;import ru.neverland.townyupkeep.service.UpkeepService;import ru.neverland.townyupkeep.model.*;import ru.neverland.townyupkeep.model.Entry.*;
import ru.neverland.townyresources.api.TownyResourcesApi;import ru.neverland.townyresources.service.ResourcesService;import ru.neverland.townyresources.model.Resource;
import ru.neverland.townytreasury.api.TownyTreasuryApi;import ru.neverland.townytreasury.service.TreasuryService;import ru.neverland.townytreasury.config.TreasurySettings;import ru.neverland.townytreasury.model.Budget;
/** Actual Upkeep/Resources/Treasury service integration with an explicit persistent economy fixture. */
public final class UpkeepTreasuryProbe extends JavaPlugin {
 static UUID id(String value){return UUID.nameUUIDFromBytes(("upkeep-treasury-052-"+value).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static final UUID T=id("town"),M=id("mayor"),FIRST=id("first"),PENDING=id("pending"),LEGACY=id("legacy");
 final Key first=new Key(T,"guard"),pending=new Key(T,"foundry"),legacy=new Key(T,"generator"),poor=new Key(T,"mill");
 Path proof;String phase;int checks,withdrawals,deposits;World world;Location origin;Town town;UpkeepService upkeep;ResourcesService resources;TreasuryService treasury;PaymentProcessor payments;final Map<UUID,Double> balances=new HashMap<>();
 interface Work{void run()throws Exception;}
 Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 void check(boolean result,String label)throws Exception{if(!result)throw new AssertionError(label);checks++;Files.writeString(proof.resolve(phase+"-checks.txt"),"PASS "+label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+label);}
 void fail(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"UPKEEP TREASURY PROBE FAILED",error);try{Files.writeString(proof.resolve("failed.txt"),error.toString());}catch(Exception ignored){}Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
 void later(long ticks,Work action){Bukkit.getRegionScheduler().runDelayed(this,origin,t->{if(!SuiteState.tryRun("Upkeep Treasury native",()->{try{action.run();}catch(Throwable error){fail(error);}}))later(1,action);},ticks);}
 void until(BooleanSupplier condition,Work next,int left)throws Exception{if(condition.getAsBoolean()){next.run();return;}if(left<=0)throw new AssertionError("Upkeep fixture condition timed out");later(1,()->until(condition,next,left-1));}
 @Override public void onEnable(){if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}proof=getDataFolder().toPath();Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{Files.createDirectories(proof);phase=Files.exists(proof.resolve("first-passed.txt"))?"restart":"first";world=Bukkit.getWorlds().getFirst();origin=new Location(world,8,140,8);world.getChunkAtAsync(0,0).whenComplete((chunk,error)->{if(error!=null)fail(error);else later(2,this::setup);});}catch(Throwable error){fail(error);}},40);}
 double balance(){return town.getAccount().getHoldingBalance(true);}long cents(){return Math.round(balance()*100);}long food(){return resources.resources(T).orElseThrow().state().balances().get(Resource.FOOD);}
 void setup()throws Exception{
  upkeep=(UpkeepService)Bukkit.getServicesManager().load(TownyUpkeepApi.class);resources=(ResourcesService)Bukkit.getServicesManager().load(TownyResourcesApi.class);treasury=(TreasuryService)Bukkit.getServicesManager().load(TownyTreasuryApi.class);payments=(PaymentProcessor)field(upkeep,"payments");
  check(upkeep!=null&&resources!=null&&treasury.capabilities().contains("resolveCategory"),"actual Upkeep, Resources and category-aware Treasury providers are installed");
  if(phase.equals("first")){var u=TownyUniverse.getInstance();u.newTownInternal("UpkeepTreasuryCity",T);town=TownyAPI.getInstance().getTown(T);var r=u.getDataSource().newResident("UpkeepTreasuryMayor",M);r.setTown(town);town.setMayor(r);r.save();var home=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(home);home.setTown(town);town.setHomeBlock(home);town.setSpawn(origin);home.save();town.save();var store=(DataStore)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds"),"dataStore");store.town(T).setConstructionSite(new ConstructionSite("guard",world.getUID(),1,140,1,BlockFace.NORTH,3,3,4,false,2));store.town(T).setLevel("guard",1);store.markDirty();store.saveOrThrow();}else{town=TownyAPI.getInstance().getTown(T);SuiteState.freeze("NeverLandTownyUpkeep");}
  bank();until(()->resources.resources(T).isPresent()&&!resources.resources(T).orElseThrow().paused(),()->{((BukkitTask)field(upkeep,"task")).cancel();((BukkitTask)field(resources,"task")).cancel();waitTreasury(300);},300);
 }
 void waitTreasury(int left)throws Exception{try{treasury.sync(town);}catch(IllegalStateException busy){if(left<=0)throw busy;later(1,()->waitTreasury(left-1));return;}if(phase.equals("first"))first();else resume();}
 void first()throws Exception{
  resources.adjust(T,Resource.FOOD,30000,"set");check(food()==30000&&cents()==10000000,"fixture seeds exact real town account and strategic resources");
  upkeep.put(first,new Entry(false,0,"Native invoice",new Invoice(FIRST,new Cost(1234,Map.of("food",3000L)),3600,Phase.PREPARED)));
  prepareHeld(first,FIRST,300,this::heldFirst);
 }
 void prepareHeld(Key key,UUID invoice,int left,Work next)throws Exception{
  SuiteState.thaw("NeverLandTownyUpkeep");try{payments.process(key,10,300);}finally{SuiteState.freeze("NeverLandTownyUpkeep");}
  String status=(String)treasury.debitStatus(invoice).get("status");if(status.equals("PREPARED")){next.run();return;}if(!status.equals("ABSENT")||left<=0)throw new AssertionError("Expected pending exact invoice before bank, got "+status);later(1,()->prepareHeld(key,invoice,left-1,next));
 }
 void heldFirst()throws Exception{
  check(upkeep.get(first).invoice().category().equals("army")&&food()==27000&&withdrawals==0,"frozen category and held strategic resources are durable before an external debit");
  var old=treasury.settings();var mapping=new HashMap<>(old.projects());mapping.put("guard",Budget.CONSTRUCTION);treasury.reload(new TreasurySettings(old.shares(),mapping,old.taxWords(),old.notifyMayor()));
  check(treasury.resolveCategory("project/guard").equals("construction")&&upkeep.get(first).invoice().category().equals("army"),"changing Treasury project mapping cannot rewrite an accepted invoice category");
  SuiteState.thaw("NeverLandTownyUpkeep");settle(first,300,this::paidFirst);
 }
 void settle(Key key,int left,Work next)throws Exception{payments.process(key,10,300);if(upkeep.get(key).invoice()==null){next.run();return;}if(left<=0)throw new AssertionError("Payment remains "+upkeep.get(key));later(1,()->settle(key,left-1,next));}
 void paidFirst()throws Exception{
  check(upkeep.get(first).active()&&withdrawals==1&&cents()==9998766&&food()==27000,"one acknowledged debit activates upkeep and consumes only its held resources");check(treasury.debitStatus(FIRST).get("category").equals("army"),"bank receipt retains the original configured budget after reload");
  payments.process(first,10,300);check(withdrawals==1,"reprocessing a completed upkeep interval cannot debit again");
  upkeep.put(poor,new Entry(false,0,"Native insufficient budget",new Invoice(id("poor"),new Cost(20000000,Map.of("food",1000L)),3600,Phase.PREPARED)));settle(poor,300,()->{
   check(!upkeep.get(poor).active()&&food()==27000&&withdrawals==1,"confirmed budget rejection releases strategic escrow without charging city");
   resources.reserveResources(LEGACY,T,Map.of("food",1000L));upkeep.put(legacy,new Entry(false,0,"Legacy uncertain debit",new Invoice(LEGACY,new Cost(999,Map.of("food",1000L)),3600,Phase.MONEY_PENDING)));payments.process(legacy,10,300);
   check(treasury.debitStatus(LEGACY).get("status").equals("ABSENT")&&upkeep.get(legacy).invoice().phase()==Phase.MONEY_PENDING,"legacy ambiguous invoice never becomes a new Treasury request");
   upkeep.put(pending,new Entry(false,0,"Native restart pending",new Invoice(PENDING,new Cost(5678,Map.of("food",2000L)),3600,Phase.PREPARED)));prepareHeld(pending,PENDING,300,()->{check(food()==24000&&withdrawals==1&&upkeep.get(pending).invoice().category().equals("construction"),"restart fixture retains exact pending amount category and reserved resources");finish();});
  });
 }
 void resume()throws Exception{
  check(upkeep.get(pending).invoice()!=null&&upkeep.get(pending).invoice().id().equals(PENDING)&&upkeep.get(pending).invoice().category().equals("construction")&&food()==24000,"full JVM restart restores exact new invoice route and real resource escrow");check(cents()==9998766&&withdrawals==0&&treasury.debitStatus(PENDING).get("status").equals("PREPARED"),"owner pause holds prepared bank call during restart without replay");
  payments.process(legacy,10,300);check(treasury.debitStatus(LEGACY).get("status").equals("ABSENT")&&resources.reservationStatus(LEGACY).equals("HELD"),"old unknown bank call and its collateral remain available for administrator reconciliation");
  SuiteState.thaw("NeverLandTownyUpkeep");settle(pending,300,()->{check(upkeep.get(pending).active()&&withdrawals==1&&cents()==9993088&&food()==24000,"restored request commits exactly once through original receipt without second resource consumption");payments.process(pending,10,300);payments.process(first,10,300);check(withdrawals==1,"both paid intervals ignore reprocessing after restart");finish();});
 }
 void finish()throws Exception{var values=new Properties();balances.forEach((k,v)->values.setProperty(k.toString(),v.toString()));try(var out=Files.newOutputStream(proof.resolve("bank.properties"))){values.store(out,"Explicit persistent deterministic bank fixture");}TownySettings.setProperty("economy.using_economy",false);TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve(phase+"-passed.txt"),"PASS "+checks+" checks; actual Folia/Towny/Upkeep/Resources/Treasury integration, persistent bank substitute, frozen categories, paid/rejected receipts, old uncertainty isolation and complete restart; no Vault provider or network player claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
 void bank()throws Exception{if(Files.exists(proof.resolve("bank.properties"))){var p=new Properties();try(var in=Files.newInputStream(proof.resolve("bank.properties"))){p.load(in);}p.forEach((k,v)->balances.put(UUID.fromString(k.toString()),Double.parseDouble(v.toString())));}
  var adapter=(EconomyAdapter)Proxy.newProxyInstance(EconomyAdapter.class.getClassLoader(),new Class<?>[]{EconomyAdapter.class},(p,m,a)->{Account account=a!=null&&a.length>0&&a[0] instanceof Account x?x:null;UUID id=account==null?null:account.getUUID();double old=balances.getOrDefault(id,0.0);return switch(m.getName()){case "name","toString"->"Upkeep deterministic native bank fixture";case "hasAccount"->true;case "getBalance"->old;case "newAccount","deleteAccount"->null;case "getFormattedBalance"->String.valueOf(a[0]);case "setBalance"->{balances.put(id,(Double)a[1]);yield true;}case "renameAccount"->true;case "add","subtract"->{double n=(Double)a[1];boolean add=m.getName().equals("add");if(!add&&old<n)yield false;balances.put(id,old+(add?n:-n));if(add)deposits++;else withdrawals++;yield true;}default->throw new UnsupportedOperationException(m.getName());};});
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(sun.misc.Unsafe)f.get(null);EconomyProvider provider=(EconomyProvider)unsafe.allocateInstance(Class.forName("com.palmergames.bukkit.towny.object.economy.provider.ReserveEconomyProvider",false,Towny.class.getClassLoader()));for(var e:Map.of("economy",adapter,"provider",provider).entrySet()){var x=TownyEconomyHandler.class.getDeclaredField(e.getKey());x.setAccessible(true);x.set(null,e.getValue());}TownySettings.setProperty("economy.using_economy",true);if(phase.equals("first"))town.getAccount().deposit(100000,"disposable upkeep seed");deposits=withdrawals=0;
 }
}
