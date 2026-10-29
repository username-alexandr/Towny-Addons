package ru.neverland.runtime;
import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.concurrent.locks.*;import java.util.function.*;
import org.bukkit.*;import org.bukkit.plugin.java.JavaPlugin;import org.bukkit.configuration.file.YamlConfiguration;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.economy.*;import com.palmergames.bukkit.towny.object.economy.adapter.*;import com.palmergames.bukkit.towny.object.economy.provider.*;
import ru.neverland.core.*;import ru.neverland.townytreasury.*;import ru.neverland.townytreasury.service.*;import ru.neverland.townytreasury.data.*;import ru.neverland.townytreasury.model.*;import ru.neverland.townytreasury.debits.*;

/** Actual Folia/Treasury/Towny Account+Observer; a deterministic substituted bank backend is explicit. */
public final class TreasuryAsyncProbe extends JavaPlugin{
 static UUID id(String text){return UUID.nameUUIDFromBytes(("treasury-async030-"+text).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static final UUID TOWN=id("town"),SECOND=id("second"),THIRD=id("third"),ACTOR=id("mayor"),A=id("a"),AR=id("ar"),B=id("b"),C=id("c"),CR=id("cr"),D=id("d"),DR=id("dr"),E=id("e"),ER=id("er"),BUSY=id("busy"),BUSYR=id("busy-refund"),GAP=id("gap");
 Path proof;boolean restart;int checks;NeverLandTownyTreasuryPlus plugin;TreasuryService service;TreasuryRepository repo;FixtureBank bank=new FixtureBank();CountDownLatch release;volatile long disableNanos;volatile CompletionStage<Void> closing;boolean stream;
 interface Work{void run()throws Exception;}
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 void check(boolean ok,String why)throws Exception{if(!ok)throw new AssertionError(why);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),"PASS "+why+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+why);}
 void fail(Throwable error){if(release!=null)release.countDown();getLogger().log(java.util.logging.Level.SEVERE,"TREASURY ASYNC PROBE FAILED",error);try{Files.writeString(proof.resolve("failed.txt"),error.toString());}catch(Exception ignored){}Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
 void later(int ticks,Work next){Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{if(!SuiteState.tryRun("native treasury async probe",()->{try{next.run();}catch(Throwable e){fail(e);}}))later(1,next);},ticks);}
 <T>void await(CompletionStage<T> f,Work next){f.whenComplete((v,e)->{if(e!=null)fail(e);else later(1,next);});}
 void pulse()throws Exception{var m=TreasuryService.class.getDeclaredMethod("pulse");m.setAccessible(true);m.invoke(service);}
 boolean idle(){try{Object gateway=field(service,"asynchronous");var m=gateway.getClass().getDeclaredMethod("busy");m.setAccessible(true);return !repo.busy()&&!((Boolean)m.invoke(gateway))&&((CompletionStage<?>)field(service,"maintenance")).toCompletableFuture().isDone();}catch(Exception e){throw new IllegalStateException(e);}}
 void until(BooleanSupplier ready,Work next){until(ready,next,500);}
 void until(BooleanSupplier ready,Work next,int attempts){later(1,()->{if(idle()&&ready.getAsBoolean()){next.run();return;}if(attempts==0)throw new AssertionError("treasury state timeout "+service.asyncDebits());if(stream){var account=TownyAPI.getInstance().getTown(THIRD).getAccount();account.deposit(.01,"native continuous-flow fairness");}pulse();until(ready,next,attempts-1);});}
 void ticks(int count,Work next){later(1,()->{if(count==0){next.run();return;}pulse();ticks(count-1,next);});}
 String state(UUID op){return String.valueOf(service.debitStatus(op).get("status"));}
 boolean paid(UUID op){return state(op).equals("PAID");}long balance(UUID town){return Math.round(bank.money.getOrDefault(town,0D)*100);}
 long infra(UUID town){return repo.cities().get(town).funds().get(Budget.INFRASTRUCTURE);}
 CompletionStage<Map<String,Object>> debit(UUID op,UUID town,long cents){return service.requestDebit(op,town,"infrastructure","native/license",cents,"native backed purpose",ACTOR,getName());}
 CompletionStage<Map<String,Object>> refund(UUID op,UUID backed){return service.requestRefund(op,backed,ACTOR,getName(),"native full escrow refund");}
 void denied(Supplier<CompletionStage<?>> action,Work next){try{var f=action.get();f.whenComplete((v,error)->{if(error==null)fail(new AssertionError("invalid payment accepted"));else later(1,next);});}catch(IllegalArgumentException|IllegalStateException expected){later(1,next);}}
 @Override public void onEnable(){if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}proof=getDataFolder().toPath();try{Files.createDirectories(proof);}catch(Exception e){throw new RuntimeException(e);}restart=Files.exists(proof.resolve("first-passed.txt"));later(50,this::setup);}
 void setup()throws Exception{
  plugin=(NeverLandTownyTreasuryPlus)Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus");service=(TreasuryService)Bukkit.getServicesManager().load(ru.neverland.townytreasury.api.TownyTreasuryApi.class);repo=(TreasuryRepository)field(service,"repo");((org.bukkit.scheduler.BukkitTask)field(service,"task")).cancel();
  check(plugin.isEnabled()&&plugin.getDescription().getVersion().equals("0.3.1"),"actual Treasury0.3.1 plugin starts on real Folia");
  var economy=TownyEconomyHandler.class.getDeclaredField("economy");economy.setAccessible(true);economy.set(null,bank);var provider=TownyEconomyHandler.class.getDeclaredField("provider");provider.setAccessible(true);provider.set(null,new VaultEconomyProvider());TownySettings.setProperty("economy.using_economy",true);
  if(restart){var y=YamlConfiguration.loadConfiguration(proof.resolve("fixture.yml").toFile());for(String text:y.getConfigurationSection("money").getKeys(false))bank.money.put(UUID.fromString(text),y.getDouble("money."+text));until(()->initialized(),this::resumed);return;}
  for(var e:Map.of(TOWN,"BudgetNativeOne",SECOND,"BudgetNativeTwo",THIRD,"BudgetNativeStream").entrySet()){var u=TownyUniverse.getInstance();u.newTownInternal(e.getValue(),e.getKey());var town=TownyAPI.getInstance().getTown(e.getKey());var mayor=u.getDataSource().newResident(e.getValue()+"Mayor",id(e.getValue()));mayor.setTown(town);town.setMayor(mayor);mayor.save();town.save();bank.money.put(e.getKey(),1000D);}
  until(()->repo.cities().size()==3&&initialized(),()->{var cities=new HashMap<>(repo.cities());for(var id:List.of(TOWN,SECOND,THIRD))cities.put(id,LedgerEngine.mode(cities.get(id),true,"explicit native fixture budget seed"));await(repo.replace(cities),this::ackGate);});
 }
 boolean initialized(){try{return (Boolean)field(service,"initialized");}catch(Exception e){throw new IllegalStateException(e);}}
 @SuppressWarnings("unchecked")void ackGate()throws Exception{
  var locksField=Class.forName("ru.neverland.core.AtomicFiles",true,plugin.getClass().getClassLoader()).getDeclaredField("writers");locksField.setAccessible(true);var locks=(Map<Path,ReentrantLock>)locksField.get(null);var path=plugin.getDataFolder().toPath().resolve("treasury-data.yml").toAbsolutePath().normalize();var lock=locks.computeIfAbsent(path,p->new ReentrantLock());var entered=new CountDownLatch(1);release=new CountDownLatch(1);
  var blocker=new Thread(()->{lock.lock();entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{lock.unlock();}},"native-treasury-physical-writer-hold");blocker.setDaemon(true);blocker.start();
  later(1,()->{check(entered.getCount()==0,"physical Treasury file writer deliberately held");var future=debit(A,TOWN,10000);later(4,()->{check(!future.toCompletableFuture().isDone()&&state(A).equals("ABSENT")&&bank.debits==0,"before durable intent ACK neither receipt nor bank call is published");check(SuiteState.held(),"global Suite remains available while physical writer waits");release.countDown();await(future,this::prepared);});});
 }
 void prepared()throws Exception{
  check(state(A).equals("PREPARED")&&repo.cities().get(TOWN).pending().id().equals(A),"durable prepared receipt and exact budget reservation appear atomically");
  check(!service.canSpend(TOWN,"infrastructure",1)&&!service.spend(TOWN,"infrastructure","native",1,"old caller")&&bank.debits==0,"legacy boolean spend fails closed before bank and pending blocks new budget use");
  await(debit(A,TOWN,10000),()->denied(()->debit(A,TOWN,10001),()->{check(bank.debits==0,"same UUID repeats unchanged receipt and rejects changed amount");SuiteState.freeze(getName());ticks(8,()->{check(bank.debits==0&&state(A).equals("PREPARED"),"paused owner does not invoke a prepared payment");SuiteState.thaw(getName());stream=true;until(()->paid(A),this::paidFirst);});}));
 }
 void paidFirst()throws Exception{
  stream=false;check(bank.debits==1&&balance(TOWN)==90000&&infra(TOWN)==15000,"exact observed bank debit and category decrease occur once despite continuous other-town flows");check(Boolean.TRUE.equals(service.debitStatus(A).get("backingAvailable")),"paid escrow exposes live available backing");
  until(()->!((BankFlows)fieldUnchecked(service,"flows")).contains(TOWN),this::busyRefund);
 }
 Object fieldUnchecked(Object o,String name){try{return field(o,name);}catch(Exception e){throw new IllegalStateException(e);}}
 void busyRefund()throws Exception{
  await(debit(BUSY,TOWN,100),()->denied(()->refund(AR,A),()->{check(state(AR).equals("ABSENT")&&repo.cities().get(TOWN).pending().id().equals(BUSY)&&bank.creditsFor(TOWN)==0,"busy refund creates no rejected intent and preserves the other exact pending reservation");until(()->paid(BUSY),()->await(refund(BUSYR,BUSY),()->until(()->paid(BUSYR),()->{check(balance(TOWN)==90000&&infra(TOWN)==15000,"other payment finishes and is fully refunded before reusing original refund UUID");await(refund(AR,A),this::refundBalancePause);})));}));
 }
 void refundBalancePause()throws Exception{
  int credits=bank.creditsFor(TOWN);bank.money.put(TOWN,Money.MAX/100.0);ticks(10,()->until(()->state(AR).equals("PREPARED")&&service.debitStatus(AR).get("review").toString().contains("превысит"),()->{check(bank.creditsFor(TOWN)==credits&&repo.cities().get(TOWN).pending().id().equals(AR),"temporary actual-bank balance ceiling preserves PREPARED refund UUID without credit or rejection");bank.money.put(TOWN,900D);await(refund(AR,A),()->until(()->paid(AR),this::refundedFirst));}));
 }
 void refundedFirst()throws Exception{
  check(balance(TOWN)==100000&&infra(TOWN)==25000&&bank.creditsFor(TOWN)==2,"full backed refund restores exact original category without fresh allocation");check(Boolean.FALSE.equals(service.debitStatus(A).get("backingAvailable"))&&((Number)service.debitStatus(A).get("refundedCents")).longValue()==10000,"refunded debit is no longer available escrow backing");
  await(refund(AR,A),()->denied(()->refund(id("duplicate-refund"),A),()->{check(bank.creditsFor(TOWN)==2,"same refund ID and second refund request cannot repeat bank credit");await(debit(B,TOWN,30000),()->{check(state(B).equals("REJECTED")&&bank.debits==2,"rich total balance cannot bypass insufficient infrastructure allocation");until(()->idle(),()->{bank.loseDebit=true;await(debit(C,TOWN,10000),()->until(()->state(C).equals("REVIEW"),this::unknownDebit));});});}));
 }
 void unknownDebit()throws Exception{
  int calls=bank.debits;ticks(8,()->{check(bank.debits==calls&&balance(TOWN)==90000&&repo.cities().get(TOWN).balance()==100000&&repo.cities().get(TOWN).pending().id().equals(C),"lost backend response retains exact REVIEW and budget lock without replay");await(service.resolveAsync(C,true,"native exact bank receipt inspection"),()->until(()->paid(C),()->{check(bank.debits==calls&&repo.cities().get(TOWN).balance()==90000,"manual paid reconciliation updates ledger only after exact receipt decision");await(refund(CR,C),()->until(()->paid(CR),this::unknownRefund));}));});
 }
 void unknownRefund()throws Exception{
  check(balance(TOWN)==100000&&infra(TOWN)==25000,"reconciled debit refund conserves original city funds");await(debit(D,TOWN,10000),()->until(()->paid(D),()->{bank.loseCredit=true;await(refund(DR,D),()->until(()->state(DR).equals("REVIEW"),()->{check(balance(TOWN)==100000&&repo.cities().get(TOWN).balance()==90000,"unknown refund remains REVIEW with unchanged ledger awaiting restart");bank.stopOnDebit=true;await(debit(E,SECOND,10000),()->waitShutdown(300));}));}));
 }
 void waitShutdown(int attempts){later(1,()->{if(closing==null){if(attempts==0)throw new AssertionError("shutdown hook missing");pulse();waitShutdown(attempts-1);return;}await(closing,()->{check(disableNanos<200_000_000L,"actual onDisable under external Suite returns without waiting for bank or IO");check(paid(E)&&balance(SECOND)==90000&&infra(SECOND)==15000,"bank result after shutdown request receives durable PAID and exact budget ACK");finish();});});}
 void resumed()throws Exception{
  check(state(DR).equals("REVIEW")&&paid(E)&&balance(TOWN)==100000&&balance(SECOND)==90000,"full JVM restart preserves unknown refund and shutdown paid debit");int calls=bank.debits+bank.totalCredits;ticks(8,()->{check(bank.debits+bank.totalCredits==calls,"restart never repeats REVIEW refund or previously PAID debit");await(service.resolveAsync(DR,true,"native exact previous refund journal inspection"),()->until(()->paid(DR),()->{check(balance(TOWN)==100000&&repo.cities().get(TOWN).balance()==100000&&infra(TOWN)==25000&&bank.totalCredits==0,"manual refund reconciliation restores original budget without a second credit");denied(()->service.resolveAsync(DR,true,"duplicate review"),()->await(refund(ER,E),()->until(()->paid(ER),this::recovered)));}));});
 }
 void recovered()throws Exception{
  check(balance(SECOND)==100000&&infra(SECOND)==25000&&bank.creditsFor(SECOND)==1,"paid shutdown debit can be refunded exactly once after restart");
  var before=repo.image();var terms=new AsyncDebit.Terms(GAP,SECOND,"infrastructure","native/license",1000,"explicit crash-boundary recovery seed",ACTOR,getName());var row=new AsyncDebit(terms,AsyncDebit.State.INVOKING,System.currentTimeMillis(),System.currentTimeMillis(),"");var cities=new HashMap<>(before.cities());cities.put(SECOND,LedgerEngine.prepare(cities.get(SECOND),terms.pending(System.currentTimeMillis())));var records=new HashMap<>(before.debits());records.put(GAP,row);repo.noteFlowGap();
  await(repo.commit(before,new TreasuryRepository.Image(cities,records,true)),()->until(()->state(GAP).equals("REVIEW"),()->{check(repo.flowGap()&&state(GAP).equals("REVIEW"),"recovery-only INVOKING to REVIEW works even while durable flow-gap closes new payments");await(service.resolveAsync(GAP,false,"native proven no bank invocation for seeded crash window"),()->{check(state(GAP).equals("REJECTED")&&repo.cities().get(SECOND).pending()==null,"exact review clears one held budget without clearing global flow-gap");denied(()->debit(id("blocked-gap"),SECOND,100),()->{check(bank.debits==0,"flow gap rejects fresh debit before any bank call");finish();});});}));
 }
 void finish()throws Exception{
  var y=new YamlConfiguration();bank.money.forEach((id,amount)->y.set("money."+id,amount));y.save(proof.resolve("fixture.yml").toFile());TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; real Folia, Treasury async budgets, actual Towny Account/Observer, physical ACK, continuous-flow fairness, backed debit/refund, uncertainty, exact reconciliation, outer-Suite shutdown and JVM restart. Controlled economy adapter and explicit recovery seed; no real Vault provider or network client claimed.\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());
 }
 final class FixtureBank implements EconomyAdapter{
  final Map<UUID,Double> money=new ConcurrentHashMap<>();final Map<UUID,Integer> credits=new ConcurrentHashMap<>();int debits,totalCredits;boolean loseDebit,loseCredit,stopOnDebit;
  int creditsFor(UUID town){return credits.getOrDefault(town,0);}
  public String name(){return "NativeTreasuryFixture";}
  public boolean add(Account a,double n){if(!a.getUUID().equals(THIRD)&&SuiteState.held())throw new AssertionError("refund bank held Suite");totalCredits++;credits.merge(a.getUUID(),1,Integer::sum);money.merge(a.getUUID(),n,Double::sum);if(loseCredit){loseCredit=false;throw new IllegalStateException("explicit adapter lost credit response");}return true;}
  public boolean subtract(Account a,double n){if(SuiteState.held())throw new AssertionError("debit bank held Suite");debits++;money.merge(a.getUUID(),-n,Double::sum);if(loseDebit){loseDebit=false;throw new IllegalStateException("explicit adapter lost debit response");}if(stopOnDebit){stopOnDebit=false;var done=new CountDownLatch(1);var thread=new Thread(()->{try(var guard=SuiteState.guard()){long start=System.nanoTime();plugin.onDisable();disableNanos=System.nanoTime()-start;closing=service.stop();done.countDown();}},"native-treasury-shutdown-inside-bank");thread.setDaemon(true);thread.start();try{if(!done.await(1,TimeUnit.SECONDS))throw new AssertionError("disable blocked while bank awaited result");}catch(InterruptedException e){throw new RuntimeException(e);}}return true;}
  public boolean hasAccount(Account a){return true;}public double getBalance(Account a){return money.getOrDefault(a.getUUID(),0D);}public void newAccount(Account a){}public void deleteAccount(Account a){}public boolean renameAccount(Account a,String n){return true;}public boolean setBalance(Account a,double n){money.put(a.getUUID(),n);return true;}public String getFormattedBalance(double n){return Double.toString(n);}
 }
}
