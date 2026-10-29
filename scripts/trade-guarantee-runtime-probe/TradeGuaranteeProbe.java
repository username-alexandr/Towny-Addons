package ru.neverland.runtime;

import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.function.*;
import org.bukkit.*;import org.bukkit.inventory.ItemStack;import org.bukkit.plugin.*;import org.bukkit.plugin.java.JavaPlugin;import org.bukkit.configuration.file.YamlConfiguration;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.*;import com.palmergames.bukkit.towny.object.economy.*;import com.palmergames.bukkit.towny.object.economy.adapter.*;import com.palmergames.bukkit.towny.object.economy.provider.*;
import ru.neverland.core.*;import ru.neverland.minttrade.*;import ru.neverland.minttrade.model.*;import ru.neverland.minttrade.service.*;import ru.neverland.minttrade.guarantee.*;import ru.neverland.townybuilds.api.WarehouseApi;

/** Real Folia, Trade state machine, Towny Account and observer. External bank backend and warehouse are explicit deterministic substitutes. */
public final class TradeGuaranteeProbe extends JavaPlugin {
 static UUID id(String s){return UUID.nameUUIDFromBytes(("trade-guarantee030-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static final UUID SELLER=id("seller"),BUYER=id("buyer"),TOLL=id("toll"),GOOD=id("guaranteed"),CANCEL=id("cancelled"),OLD=id("old-uninsured"),UNKNOWN=id("unknown"),STOP=id("shutdown");
 Path proof;boolean restart;int checks;MintTownyTrade plugin;TradeService service;TradeRepository repo;World world;FixtureBank bank=new FixtureBank();FixtureWarehouse warehouse=new FixtureWarehouse();CountDownLatch release;CompletionStage<Void> shutdown;Object oldAudit;volatile long disableNanos;
 interface Work{void run()throws Exception;}
 static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 void check(boolean ok,String why)throws Exception{if(!ok)throw new AssertionError(why);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),"PASS "+why+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+why);}
 void fail(Throwable e){if(release!=null)release.countDown();getLogger().log(java.util.logging.Level.SEVERE,"TRADE GUARANTEE PROBE FAILED",e);try{Files.writeString(proof.resolve("failed.txt"),e.toString());}catch(Exception ignored){}Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
 void later(int ticks,Work work){Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{if(!SuiteState.tryRun("native Trade guarantee check",()->{try{work.run();}catch(Throwable e){fail(e);}}))later(1,work);},ticks);}
 <T>void await(CompletionStage<T> future,Work next){future.whenComplete((v,e)->{if(e!=null)fail(e);else later(1,next);});}
 void tick()throws Exception{var m=TradeService.class.getDeclaredMethod("tick");m.setAccessible(true);m.invoke(service);}
 void until(BooleanSupplier condition,Work next){until(condition,next,300);}
 void until(BooleanSupplier condition,Work next,int attempts){later(1,()->{if(!service.processing()&&condition.getAsBoolean()){next.run();return;}if(attempts==0)throw new AssertionError("Trade state did not advance: "+repo.allCaravans().stream().map(c->c.id()+"="+c.settlement()).toList());tick();until(condition,next,attempts-1);});}
 String effect(UUID id){try{return repo.effects().getClass().getMethod("state",UUID.class).invoke(repo.effects(),id).toString();}catch(Exception e){throw new IllegalStateException(e);}}
 Caravan current(UUID id){return repo.findCaravan(id.toString());}boolean settled(UUID id){return current(id)==null&&repo.history(BUYER).stream().anyMatch(h->h.caravanId().equals(id));}
 @Override public void onEnable(){if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}proof=getDataFolder().toPath();try{Files.createDirectories(proof);}catch(Exception e){throw new RuntimeException(e);}restart=Files.exists(proof.resolve("first-passed.txt"));later(50,this::setup);}
 void setup()throws Exception{
  plugin=(MintTownyTrade)Bukkit.getPluginManager().getPlugin("NeverLandTownyTrade");service=(TradeService)field(plugin,"trade");repo=(TradeRepository)field(plugin,"repository");((org.bukkit.scheduler.BukkitTask)field(service,"task")).cancel();set(service,"legacyReviews",true);world=Bukkit.getWorlds().getFirst();
  check(plugin.isEnabled()&&plugin.getDescription().getVersion().equals("0.3.1"),"actual Trade0.3 plugin enabled with real Folia scheduler");
  Bukkit.getServicesManager().register(WarehouseApi.class,warehouse,this,ServicePriority.Highest);
  var economy=TownyEconomyHandler.class.getDeclaredField("economy");economy.setAccessible(true);economy.set(null,bank);var provider=TownyEconomyHandler.class.getDeclaredField("provider");provider.setAccessible(true);provider.set(null,new VaultEconomyProvider());TownySettings.setProperty("economy.using_economy",true);
  if(restart){loadFixture();resumed();return;}
  for(var e:Map.of(SELLER,"GuaranteeSeller",BUYER,"GuaranteeBuyer",TOLL,"GuaranteeToll").entrySet()){var u=TownyUniverse.getInstance();u.newTownInternal(e.getValue(),e.getKey());var town=TownyAPI.getInstance().getTown(e.getKey());var mayor=u.getDataSource().newResident(e.getValue()+"Mayor",id(e.getValue()));mayor.setTown(town);town.setMayor(mayor);mayor.save();town.save();}
  bank.money.put(BUYER,1000D);bank.money.put(SELLER,0D);bank.money.put(TOLL,0D);warehouse.stock.put(SELLER,384);warehouse.stock.put(BUYER,0);
  check(TownyAPI.getInstance().getTown(BUYER).getAccount().getHoldingBalance(true)==1000,"actual Towny account sees controlled backend balance");gate();
 }
 @SuppressWarnings("unchecked")CompletionStage<Object> global(Supplier<Object> action,boolean guard)throws Exception{Class<?> supplier=Class.forName("ru.neverland.minttrade.service.TradeService$CheckedSupplier",true,plugin.getClass().getClassLoader());Object proxy=Proxy.newProxyInstance(supplier.getClassLoader(),new Class<?>[]{supplier},(p,m,a)->action.get());var m=TradeService.class.getDeclaredMethod("global",supplier,boolean.class);m.setAccessible(true);return(CompletionStage<Object>)m.invoke(service,proxy,guard);}
 void gate()throws Exception{
  var entered=new CountDownLatch(1);release=new CountDownLatch(1);var calls=new AtomicInteger();var result=global(()->calls.incrementAndGet(),true);
  Thread holder=new Thread(()->{try(var guard=SuiteState.guard()){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}},"native-suite-contention");holder.setDaemon(true);holder.start();
  Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{check(entered.getCount()==0&&calls.get()==0&&!result.toCompletableFuture().isDone(),"busy Suite defers Trade action while raw global scheduler continues");release.countDown();await(result,()->{check(calls.get()==1,"deferred guarded action runs exactly once after Suite release");seed(GOOD,true,()->until(()->current(GOOD)!=null&&current(GOOD).settlement().equals("ACTIVE"),this::guaranteedActive));});}catch(Throwable e){fail(e);}},4);
 }
 void seed(UUID id,boolean guaranteed,Work next)throws Exception{
  long now=System.currentTimeMillis();var terms=guaranteed?new GuaranteeTerms(2000,10000,0):GuaranteeTerms.none();var o=new TradeOffer(id,SELLER,BUYER,"native_stone",now,now+3_600_000,terms,guaranteed?10000:0,guaranteed?"a".repeat(64):"");
  var route=List.of(new RoutePoint(world.getUID(),world.getName(),0,100,0,RoutePoint.Kind.TOWN,"seller",1,SELLER),new RoutePoint(world.getUID(),world.getName(),30,100,0,RoutePoint.Kind.TOWN,"buyer",1,BUYER));
  var c=new Caravan(id,SELLER,BUYER,"native_stone",new ItemStack(Material.STONE),64,64,100,110,Map.of(TOLL,10D),route,now,now+60_000,now+30_000,false,true,CaravanStatus.ACTIVE);if(guaranteed)c.restoreGuarantee(terms,60_000,0,-1,-1);
  await(repo.putOffer(o),()->{if(guaranteed)check(repo.findOffer(id.toString()).guarantee().equals(terms),"new offer freezes guarantee terms before acceptance");await(repo.accept(o,c),next);});
 }
 void arrive(UUID id,Work next)throws Exception{var c=current(id);long now=System.currentTimeMillis();c.travelTimes(now-120_000,now-90_000);c.arrivesAt(now-60_000);if(c.guarantee().enabled())c.delay(30_000);c.incidentHandled(true);await(repo.update(c),next);}
 void guaranteedActive()throws Exception{
  check(bank.debits==1&&balance(BUYER)==890&&warehouse.stock.get(SELLER)==320,"prepared journal precedes exactly one escrow debit and cargo reservation");
  plugin.getConfig().set("delivery-guarantee.reserve-basis-points",100);check(current(GOOD).guarantee().reserveBasisPoints()==2000,"changing configured future policy does not change accepted caravan");
  arrive(GOOD,()->until(()->current(GOOD).settlement().equals("PAYING"),this::auditFailure));
 }
 void auditFailure()throws Exception{
  oldAudit=field(service,"audits");var close=oldAudit.getClass().getDeclaredMethod("closeAsync");close.setAccessible(true);await((CompletionStage<?>)close.invoke(oldAudit),()->{
   tick();until(()->!service.processing()&&effect(current(GOOD).operation("seller:"+SELLER)).equals("DONE"),()->{
    check(current(GOOD).settlement().equals("PAYING")&&balance(SELLER)==80&&bank.credits==1,"rejected audit leaves PAYING with seller receipt DONE and one actual credit");
    var constructor=oldAudit.getClass().getDeclaredConstructor(JavaPlugin.class);constructor.setAccessible(true);set(service,"audits",constructor.newInstance(plugin));until(()->settled(GOOD),this::guaranteedPaid);
   });
  });
 }
 void guaranteedPaid()throws Exception{
  check(balance(SELLER)==80&&balance(BUYER)==910&&balance(TOLL)==10&&bank.credits==3,"seller80 buyer20 toll10 conserve existing110 escrow with no emission");
  check(repo.history(BUYER).stream().filter(h->h.caravanId().equals(GOOD)).findFirst().orElseThrow().compensationCents()==2000,"history retains exact frozen compensation after terminal cleanup");
  check(warehouse.stock.get(BUYER)==64&&warehouse.actualMoves==2,"delivered cargo remains exactly once after audit-only retry");
  seed(CANCEL,true,()->until(()->current(CANCEL)!=null&&current(CANCEL).settlement().equals("ACTIVE"),()->{check(service.cancelCaravan(current(CANCEL))==TradeService.CancelResult.PROCESSING,"administrative cancellation acknowledges refund asynchronously");until(()->settled(CANCEL),this::cancelled);}));
 }
 void cancelled()throws Exception{
  check(balance(BUYER)==910&&balance(SELLER)==80&&balance(TOLL)==10,"cancelled funded caravan returns full110 escrow without extra compensation");check(warehouse.stock.get(SELLER)==320&&warehouse.stock.get(BUYER)==64,"cancellation returns exact reserved cargo to seller warehouse");
  seed(OLD,false,()->until(()->current(OLD)!=null&&current(OLD).settlement().equals("ACTIVE"),()->{check(!current(OLD).guarantee().enabled(),"legacy-style caravan remains uninsured");arrive(OLD,()->until(()->settled(OLD),this::legacyPaid));}));
 }
 void legacyPaid()throws Exception{
  check(balance(BUYER)==800&&balance(SELLER)==180&&balance(TOLL)==20,"uninsured delivery pays original full seller price and tariff");
  bank.loseNextDebit=true;seed(UNKNOWN,true,()->until(()->effect(current(UNKNOWN).operation("debit")).equals("PENDING")&&bank.lost,()->{int calls=bank.debits;untilTicks(10,()->{check(bank.debits==calls&&balance(BUYER)==690&&current(UNKNOWN).settlement().equals("PREPARED"),"lost native adapter response retains pending debit without automatic retry");seed(STOP,false,()->until(()->current(STOP)!=null&&current(STOP).settlement().equals("ACTIVE"),()->arrive(STOP,()->until(()->current(STOP).settlement().equals("PAYING"),this::shutdownBank))));});}));
 }
 void untilTicks(int remaining,Work next){later(1,()->{if(remaining==0){next.run();return;}tick();untilTicks(remaining-1,next);});}
 void shutdownBank()throws Exception{
  bank.stopOnNextAdd=true;tick();untilShutdown(150);
 }
 void untilShutdown(int attempts){later(1,()->{if(shutdown==null){if(attempts<1)throw new AssertionError("shutdown hook not called");tick();untilShutdown(attempts-1);return;}await(shutdown,()->{check(disableNanos<200_000_000L,"actual onDisable under outer Suite returns without waiting for bank or writer");check(effect(current(STOP).operation("seller:"+SELLER)).equals("DONE")&&balance(SELLER)==280,"bank result after shutdown request still gets durable DONE receipt");check(current(STOP).settlement().equals("PAYING")&&effect(current(STOP).operation("tariff:"+TOLL)).equals("READY"),"shutdown does not begin next external tariff after known seller result");finish();});});}
 void resumed()throws Exception{
  check(effect(current(UNKNOWN).operation("debit")).equals("PENDING")&&balance(BUYER)==580,"full JVM restart retains exact pending unknown debit and controlled account state");check(effect(current(STOP).operation("seller:"+SELLER)).equals("DONE"),"full restart retains seller DONE committed during shutdown");
  int credits=bank.credits;until(()->settled(STOP),()->{check(bank.credits==credits+1&&balance(SELLER)==280&&balance(TOLL)==30,"restart pays remaining tariff without repeating acknowledged seller payment");check(bank.debits==0&&current(UNKNOWN).settlement().equals("PREPARED"),"restart does not replay unknown funding call");
   await(service.resolvePayment(current(UNKNOWN).operation("debit"),true),()->until(()->current(UNKNOWN).settlement().equals("ACTIVE"),()->{check(bank.debits==0,"manual paid reconciliation advances exact old debit without a new withdrawal");service.cancelCaravan(current(UNKNOWN));until(()->settled(UNKNOWN),this::reconciled);}));
  });
 }
 void reconciled()throws Exception{
  check(balance(BUYER)==690&&balance(SELLER)==280&&balance(TOLL)==30,"full refund after explicit reconciliation conserves all initial1000 money");check(warehouse.stock.get(SELLER)==192&&warehouse.stock.get(BUYER)==192,"all384 items conserved across deliveries cancellation unknown funding and restart");
  check(repo.history(BUYER).size()==5&&repo.allCaravans().isEmpty(),"all five histories persist once and terminal state cleans up only after audit ACK");
  var text=new StringBuilder();try(var files=Files.walk(plugin.getDataFolder().toPath().resolve("audit"))){for(var p:files.filter(p->p.toString().endsWith(".tsv")).toList())text.append(Files.readString(p));}check(text.toString().contains("TRADE_COMPENSATION")&&text.toString().contains("TRADE_REFUND")&&text.toString().contains(STOP.toString()),"compensation refund and shutdown payment audit persisted across restart");finish();
 }
 double balance(UUID id){return bank.money.getOrDefault(id,0D);}
 void saveFixture()throws Exception{var y=new YamlConfiguration();bank.money.forEach((id,value)->y.set("money."+id,value));warehouse.stock.forEach((id,value)->y.set("stock."+id,value));y.set("moves",warehouse.actualMoves);y.set("receipts",warehouse.receipts.stream().map(UUID::toString).toList());y.save(proof.resolve("fixture.yml").toFile());}
 void loadFixture(){var y=YamlConfiguration.loadConfiguration(proof.resolve("fixture.yml").toFile());for(String s:y.getConfigurationSection("money").getKeys(false))bank.money.put(UUID.fromString(s),y.getDouble("money."+s));for(String s:y.getConfigurationSection("stock").getKeys(false))warehouse.stock.put(UUID.fromString(s),y.getInt("stock."+s));warehouse.actualMoves=y.getInt("moves");for(String s:y.getStringList("receipts"))warehouse.receipts.add(UUID.fromString(s));}
 void finish()throws Exception{saveFixture();TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; actual Folia Trade state machine, async ACK journals, Towny accounts/observers, contention, audit retry, conservation, reconciliation, shutdown and full restart. Controlled economy adapter, warehouse API and clock/route seeds; no live Vault provider or network client claimed.\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
 final class FixtureBank implements EconomyAdapter{
  final Map<UUID,Double> money=new ConcurrentHashMap<>();int credits,debits;boolean loseNextDebit,lost,stopOnNextAdd;
  public String name(){return "NativeTradeFixture";}public boolean add(Account a,double amount){if(SuiteState.held())throw new AssertionError("incoming bank held Suite");credits++;money.merge(a.getUUID(),amount,Double::sum);if(stopOnNextAdd){stopOnNextAdd=false;var entered=new CountDownLatch(1);var worker=new Thread(()->{try(var guard=SuiteState.guard()){long begin=System.nanoTime();plugin.onDisable();disableNanos=System.nanoTime()-begin;shutdown=service.shutdown();entered.countDown();}},"native-shutdown-during-bank");worker.setDaemon(true);worker.start();try{if(!entered.await(1,TimeUnit.SECONDS))throw new AssertionError("shutdown did not start before bank return");}catch(InterruptedException e){throw new IllegalStateException(e);}}return true;}
  public boolean subtract(Account a,double amount){debits++;money.merge(a.getUUID(),-amount,Double::sum);if(loseNextDebit){loseNextDebit=false;lost=true;throw new IllegalStateException("explicit native adapter lost debit response");}return true;}public boolean hasAccount(Account a){return true;}public double getBalance(Account a){return money.getOrDefault(a.getUUID(),0D);}public void newAccount(Account a){}public void deleteAccount(Account a){}public boolean renameAccount(Account a,String name){return true;}public boolean setBalance(Account a,double amount){money.put(a.getUUID(),amount);return true;}public String getFormattedBalance(double n){return Double.toString(n);}
 }
 final class FixtureWarehouse implements WarehouseApi{
  final Map<UUID,Integer> stock=new HashMap<>();final Set<UUID> receipts=new HashSet<>();int actualMoves;
  public Map<String,Object> take(UUID town,ItemStack item,int amount){throw new UnsupportedOperationException();}public Map<String,Object> deposit(UUID town,ItemStack item,int amount){throw new UnsupportedOperationException();}
  public Map<String,Object> transfer(UUID operation,UUID town,ItemStack item,int amount,boolean incoming){if(!SuiteState.held())throw new AssertionError("warehouse called without Suite");if(!receipts.contains(operation)){if(!incoming&&stock.getOrDefault(town,0)<amount)return Map.of("status","INSUFFICIENT","amount",0);stock.merge(town,incoming?amount:-amount,Integer::sum);actualMoves++;receipts.add(operation);}return Map.of("status","SUCCESS","amount",amount);}
  public boolean transferred(UUID op,UUID town){return receipts.contains(op);}public void acknowledgeMovement(UUID op,UUID town){receipts.remove(op);}
 }
}
