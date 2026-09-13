package ru.neverland.runtime;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townycrime.*;
import ru.neverland.townycrime.api.*;
import ru.neverland.townyresources.api.TownyResourcesApi;
import ru.neverland.townyresources.data.ResourcesRepository;
import ru.neverland.townyresources.model.*;
import ru.neverland.townypopulation.api.TownyPopulationApi;
import ru.neverland.townybuilds.api.TownyBuildsApi;
import ru.neverland.townyjobs.api.TownyJobsApi;
import ru.neverland.townybuilds.shop.*;

/** Never ship this fixture in production. Native integration and actual restart, no live players. */
public final class CrimeRuntimeProbe extends JavaPlugin {
    int expected(String key)throws Exception{var p=new java.util.Properties();try(var in=getResource("expected-counts.properties")){p.load(in);}return Integer.parseInt(p.getProperty(key));}
    static UUID id(String s){return UUID.nameUUIDFromBytes(("crime-029-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID T=id("town"),M=id("mayor"),LOST=id("lost"),ORDER=id("order");
    Path proof;boolean restart;int checks;CrimeService crime;CrimeRepository ledger;TownyCrimeApi api;TownyResourcesApi resources;Town town;
    static <T>T field(Object owner,Class<T> type)throws Exception{for(var f:owner.getClass().getDeclaredFields())if(type.isAssignableFrom(f.getType())){f.setAccessible(true);return type.cast(f.get(owner));}throw new IllegalArgumentException(type.getName());}
    JavaPlugin plugin(String name){return (JavaPlugin)Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(name));}
    void check(boolean b,String label)throws Exception{if(!b)throw new AssertionError(label);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+label);}
    interface Action{void run()throws Exception;}
    void denied(Action a,String label)throws Exception{boolean denied=false;try{a.run();}catch(Exception e){denied=true;}check(denied,label);}
    void pulse()throws Exception{var method=CrimeService.class.getDeclaredMethod("pulse");method.setAccessible(true);method.invoke(crime);}
    @Override public void onEnable(){if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}Bukkit.getScheduler().runTaskLater(this,this::prepare,100);}
    void prepare(){try{proof=getDataFolder().toPath();Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));
        if(!restart){check(TownyAPI.getInstance().getTown(T)==null,"fresh disposable town");var universe=TownyUniverse.getInstance();universe.newTownInternal("CrimeAudit",T);town=TownyAPI.getInstance().getTown(T);var mayor=universe.getDataSource().newResident("CrimeAuditMayor",M);mayor.setTown(town);town.setMayor(mayor);mayor.save();town.save();}
        Bukkit.getScheduler().runTaskLater(this,()->awaitReady(0),20);
    }catch(Throwable e){fail(e);}}
    void awaitReady(int attempt){try{var service=Bukkit.getServicesManager().load(TownyCrimeApi.class);var snapshot=service==null?Optional.<Map<String,Object>>empty():service.crime(T);if(snapshot.isPresent()&&Boolean.FALSE.equals(snapshot.get().get("paused"))){run();return;}if(attempt>=40)throw new IllegalStateException("Native dependencies did not become ready: "+snapshot);Bukkit.getScheduler().runTaskLater(this,()->awaitReady(attempt+1),20);}catch(Throwable e){fail(e);}}
    void run(){try{
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==expected("addons"),"all expected exact-version addons enabled");contracts();
        api=Bukkit.getServicesManager().load(TownyCrimeApi.class);crime=field(plugin("NeverLandTownyCrime"),CrimeService.class);ledger=field(crime,CrimeRepository.class);resources=Bukkit.getServicesManager().load(TownyResourcesApi.class);town=TownyAPI.getInstance().getTown(T);
        check(api!=null&&resources!=null,"native Crime and Resources services available");
        var pop=Bukkit.getServicesManager().load(TownyPopulationApi.class);check(pop.population(T).isPresent(),"real Population snapshot available for new town");
        if(restart)restartChecks();else scenarios();TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions\n");Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }catch(Throwable e){fail(e);}}
    void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CRIME_PROBE_FAIL",e);try{Files.createDirectories(getDataFolder().toPath());var sw=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(sw));Files.writeString(getDataFolder().toPath().resolve("failed.txt"),sw.toString());}catch(Exception ignored){}Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);}
    void contracts()throws Exception{try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getResource("contracts.tsv"),java.nio.charset.StandardCharsets.UTF_8))){var rows=reader.lines().toList();check(rows.size()==expected("contracts"),"all public contracts from source inventory");for(String line:rows){String[] row=line.split("\t");var p=plugin(row[0]);Class<?> type=Class.forName(row[1],true,p.getClass().getClassLoader());Object provider=Bukkit.getServicesManager().load(type);check(provider!=null&&p.isEnabled()&&p.getDescription().getVersion().equals(row[3])&&Integer.valueOf(1).equals(type.getMethod("apiVersion").invoke(provider))&&new HashSet<>(Arrays.asList(row[2].split(","))).equals(type.getMethod("capabilities").invoke(provider)),"provider ABI/capabilities: "+row[1]);}}}
    static Object record(Object old,Map<String,Object> changes)throws Exception{var parts=old.getClass().getRecordComponents();var types=new Class<?>[parts.length];var values=new Object[parts.length];for(int i=0;i<parts.length;i++){types[i]=parts[i].getType();values[i]=changes.containsKey(parts[i].getName())?changes.get(parts[i].getName()):parts[i].getAccessor().invoke(old);}return old.getClass().getConstructor(types).newInstance(values);}
    @SuppressWarnings("unchecked") <T>T proxy(Class<T> type,T original,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
    void scenarios()throws Exception{
        var before=api.crime(T).orElseThrow();check(Boolean.FALSE.equals(before.get("paused")),"healthy native Population and Builds permit Crime quotes");check((Integer)before.get("incomeBasisPoints")==10000,"new town grace period has no shop loss");
        var bridge=new CrimeBridge();var nativeInputs=bridge.inputs(T);check(nativeInputs.happiness()>=0&&nativeInputs.happiness()<=100,"Crime reads actual Population metrics");
        var services=Bukkit.getServicesManager();var builds=services.load(TownyBuildsApi.class);var jobs=services.load(TownyJobsApi.class);var pop=services.load(TownyPopulationApi.class);
        TownyBuildsApi guard=proxy(TownyBuildsApi.class,builds,(p,m,a)->m.getName().equals("operationalLevel")&&a[1].equals("guard")?3:m.invoke(builds,a));
        TownyJobsApi workers=proxy(TownyJobsApi.class,jobs,(p,m,a)->m.getName().equals("workers")&&a[1].equals("guard")?2:m.invoke(jobs,a));
        services.register(TownyBuildsApi.class,guard,plugin("NeverLandTownyBuilds"),ServicePriority.Highest);services.register(TownyJobsApi.class,workers,plugin("NeverLandTownyJobs"),ServicePriority.Highest);
        try{var i=bridge.inputs(T);check(i.guardLevel()==3&&i.workers()==2,"public guard level and staffed Jobs bridge");check(((Double)api.crime(T).orElseThrow().get("guard"))==70,"native service combines operational level and staff");}finally{services.unregister(TownyBuildsApi.class,guard);services.unregister(TownyJobsApi.class,workers);}
        services.unregister(TownyPopulationApi.class,pop);try{denied(()->api.shopIncomeBasisPoints(T),"unavailable Population blocks new economic quotes");check(Boolean.TRUE.equals(api.crime(T).orElseThrow().get("paused")),"snapshot explicitly diagnoses paused integration");}finally{services.register(TownyPopulationApi.class,pop,plugin("NeverLandTownyPopulation"),ServicePriority.Normal);}
        TownyPopulationApi paused=proxy(TownyPopulationApi.class,pop,(p,m,a)->m.getName().equals("population")?Optional.of(record(pop.population((UUID)a[0]).orElseThrow(),Map.of("paused",true))):m.invoke(pop,a));
        services.register(TownyPopulationApi.class,paused,plugin("NeverLandTownyPopulation"),ServicePriority.Highest);try{denied(()->api.shopIncomeBasisPoints(T),"paused Population never becomes fictitious zero happiness");}finally{services.unregister(TownyPopulationApi.class,paused);}
        long now=System.currentTimeMillis();
        var settings=CrimeSettings.load(plugin("NeverLandTownyCrime").getConfig());crime.reload((CrimeSettings)record(settings,Map.of("chance",1.0,"incidentMinimum",0.0)));
        ledger.put(new CrimeState(T,80,0,0,null));pulse();var cycle=ledger.all().get(T);check(Math.abs(cycle.level()-80)<=settings.change()&&cycle.nextCycle()>now,"actual scheduled cycle changes crime gradually and persists next deadline");check(cycle.incident()!=null&&cycle.incident().phase().equals("PLANNED")&&cycle.nextIncident()>now,"automatic incident selection durably creates intent and cooldown");crime.reload(settings);
        ledger.put(new CrimeState(T,80,now+600000,now+3600000,null));check(api.shopIncomeBasisPoints(T)==8000,"80 crime produces configured 20 percent shop loss");
        ledger.put(new CrimeState(T,80,now+600000,now+3600000,new CrimeState.Incident(id("extortion"),"EXTORTION","",0,"PLANNED",now,now+900000)));pulse();check(api.shopIncomeBasisPoints(T)==7000,"durably applied extortion adds temporary shop loss");pulse();check(ledger.all().get(T).incident().phase().equals("CLOSED"),"extortion completes without a Resources reservation");
        // Seed only the disposable virtual ledger; actual public API performs theft and protects reserves.
        var resourcesRepo=field(resources,ResourcesRepository.class);var states=new HashMap<>(resourcesRepo.states());var original=states.get(T);check(original!=null,"native Resources owns town ledger");states.put(T,original.balance(Resource.WOOD,100000).settings(Map.of(Resource.WOOD,90000L),original.paused(),original.priorities()));resourcesRepo.replace(states);

        check(!resources.reserveResources(id("protected"),T,Map.of("wood",11000L)),"actual Resources refuses theft below protected reserve");
        var refresh=resources.getClass().getDeclaredMethod("refresh",boolean.class);refresh.setAccessible(true);refresh.invoke(resources,false);
        check(bridge.available(T,List.of("wood")).get("wood")==10000L,"public snapshot exposes only unprotected stock for theft planning");
        ledger.put(new CrimeState(T,80,now+600000,now+3600000,new CrimeState.Incident(id("theft"),"BURGLARY","wood",2000,"PLANNED",now,now+900000)));
        pulse();pulse();check(resourcesRepo.states().get(T).balances().get(Resource.WOOD)==98000L,"native Crime steals actual virtual stock once");check(resources.reservationStatus(id("theft")).equals("NONE")&&ledger.all().get(T).incident().phase().equals("CLOSED"),"applied theft saved before provider receipt cleanup");pulse();check(resourcesRepo.states().get(T).balances().get(Resource.WOOD)==98000L,"repeated native pulse does not debit a completed theft");
        var shop=field(plugin("NeverLandTownyBuilds"),ru.neverland.townybuilds.civic.CivicService.class).shops().service();
        var order=new ShopOrder(ORDER,T,M,"STONE",10,1000,now,"DELIVERED",now,"fixture",false,7500);shop.journal().put(order);double bankBefore=town.getAccount().getHoldingBalance();check(shop.credit(order),"real shop credit accepts frozen net amount");check(Math.abs(town.getAccount().getHoldingBalance()-bankBefore-75)<0.001,"town receives 75 from 100 gross, matching frozen crime loss");shop.journal().put(order.paymentStep("COMPLETE",now,"fixture credited").finish());
        // Lost reserve reply: provider already debited, consumer must retain the same planned ID.
        TownyResourcesApi lost=proxy(TownyResourcesApi.class,resources,(obj,method,a)->{Object answer=method.invoke(resources,a);if(method.getName().equals("reserveResources")&&a[0].equals(LOST))throw new IllegalStateException("lost durable reserve reply");return answer;});
        ledger.put(new CrimeState(T,80,now+600000,now+3600000,new CrimeState.Incident(LOST,"BURGLARY","wood",1000,"PLANNED",now,now+900000)));
        services.register(TownyResourcesApi.class,lost,plugin("NeverLandTownyResources"),ServicePriority.Highest);try{pulse();check(resourcesRepo.states().get(T).balances().get(Resource.WOOD)==97000L&&resources.reservationStatus(LOST).equals("HELD")&&ledger.all().get(T).incident().phase().equals("PLANNED"),"lost reply retains durable plan and one real resource debit");}finally{services.unregister(TownyResourcesApi.class,lost);}
        // Leave provider absent until shutdown, so a real JVM restart performs reconciliation.
        services.unregister(TownyResourcesApi.class,resources);
        var throwable=new java.util.concurrent.atomic.AtomicReference<Throwable>();var worker=new Thread(()->{try{api.shopIncomeBasisPoints(T);}catch(Throwable e){throwable.set(e);}});worker.start();worker.join(2000);check(throwable.get() instanceof IllegalStateException,"Crime API rejects worker-thread world access");
        denied(()->api.crime(T).orElseThrow().put("level",0),"Crime API returns immutable snapshots");
        check(Bukkit.getPluginManager().getPermission("neverlandtownycrime.use").getDefault()==org.bukkit.permissions.PermissionDefault.TRUE&&Bukkit.getPluginManager().getPermission("neverlandtownycrime.admin").getDefault()==org.bukkit.permissions.PermissionDefault.OP,"player and administrator permissions registered separately");
        check(Bukkit.getPluginCommand("townycrime")!=null,"direct Crime command registered");

    }
    void restartChecks()throws Exception{
        var state=ledger.all().get(T);check(state!=null&&state.level()==80,"Crime level survives real restart without offline catch-up");check(state.incident().id().equals(LOST)&&state.incident().phase().equals("CLOSED"),"pending lost-reply theft reconciles on actual restart");
        var repo=field(resources,ResourcesRepository.class);check(repo.states().get(T).balances().get(Resource.WOOD)==97000L,"restart recovery does not steal resource twice");check(repo.states().get(T).reserves().get(Resource.WOOD)==90000L,"protected virtual reserve survives theft and restart");check(resources.reservationStatus(LOST).equals("NONE"),"consumer committed outcome before removing provider receipt");
        var shop=field(plugin("NeverLandTownyBuilds"),ru.neverland.townybuilds.civic.CivicService.class).shops().service();var order=shop.journal().order(ORDER);check(order!=null&&order.total()==10000&&order.sellerIncome()==7500&&order.finalized(),"shop journal preserves gross and agreed net on real restart");check(api.shopIncomeBasisPoints(T)==8000,"recovered Crime quote matches stable crime level");
    }
}
