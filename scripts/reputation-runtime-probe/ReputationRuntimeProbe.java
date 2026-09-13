package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.reputation.api.TownyReputationApi;
import ru.neverland.reputation.model.*;
import ru.neverland.reputation.service.*;
import ru.neverland.minttrade.contract.*;
import ru.neverland.minttrade.model.*;
import ru.neverland.minttrade.service.*;
import ru.neverland.mintevents.model.*;
import ru.neverland.mintevents.service.*;
import ru.neverland.townydiplomacy.*;
import ru.neverland.mintcontracts.model.ActiveContract;
import ru.neverland.mintcontracts.model.ContractStatus;
import ru.neverland.mintcontracts.service.ContractService;
import ru.neverland.townycompanies.api.CompaniesApi;

/** Explicitly marked disposable fixture; never included in release archives. */
public final class ReputationRuntimeProbe extends JavaPlugin {
    int expected(String key)throws Exception{var p=new java.util.Properties();try(var in=getResource("expected-counts.properties")){p.load(in);}return Integer.parseInt(p.getProperty(key));}
    static UUID id(String text){return UUID.nameUUIDFromBytes(("reputation-028-"+text).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID A=id("A"),B=id("B"),C=id("C"),MA=id("mayorA"),MB=id("mayorB"),MC=id("mayorC"),FROZEN=id("frozen"),LOST=id("lost");
    Path proof;boolean restart;int checks;Town a,b,c;TownyReputationApi api;ReputationService reputation;TradeService trade;SupplyService supply;SupplyRepository supplies;
    void check(boolean condition,String label)throws Exception{if(!condition)throw new AssertionError(label);checks++;getLogger().info("CHECK "+label);Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    JavaPlugin plugin(String name){return (JavaPlugin)Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(name),name);}
    static <T>T field(Object owner,Class<T> type)throws Exception{for(var f:owner.getClass().getDeclaredFields())if(type.isAssignableFrom(f.getType())){f.setAccessible(true);return type.cast(f.get(owner));}throw new IllegalArgumentException(type.getName());}
    static Object callPrivate(Object owner,String method,Class<?>[] types,Object... args)throws Exception{var m=owner.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(owner,args);}
    interface Action{void run()throws Exception;}
    void denied(Action action,String label)throws Exception{boolean rejected=false;try{action.run();}catch(Exception ex){rejected=true;}check(rejected,label);}
    Town town(UUID id){return TownyAPI.getInstance().getTown(id);}
    int score(UUID town,String aspect){return ((Number)api.profile("TOWN",town).get(aspect)).intValue();}
    void create(String name,UUID id,UUID mayor)throws Exception{var u=TownyUniverse.getInstance();u.newTownInternal(name,id);var town=town(id);var r=u.getDataSource().newResident(name+"Mayor",mayor);r.setTown(town);town.setMayor(r);r.save();town.save();}
    @Override public void onEnable(){if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}Bukkit.getScheduler().runTaskLater(this,this::run,100);}
    void run(){try{
        proof=getDataFolder().toPath();Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==expected("addons"),"all expected candidate addons enabled");
        contracts();
        api=Bukkit.getServicesManager().load(TownyReputationApi.class);reputation=field(plugin("NeverLandTownyReputation"),ReputationService.class);
        trade=field(plugin("NeverLandTownyTrade"),TradeService.class);supply=field(plugin("NeverLandTownyTrade"),SupplyService.class);supplies=field(supply,SupplyRepository.class);
        check(api!=null&&api.healthy()&&api.apiVersion()==1,"native Reputation v1 service and profile storage healthy");
        if(!restart){check(town(A)==null,"fresh disposable towns");create("RepAuditA",A,MA);create("RepAuditB",B,MB);create("RepAuditC",C,MC);}
        a=town(A);b=town(B);c=town(C);
        if(restart)restartChecks();else scenarios();
        TownyUniverse.getInstance().getDataSource().saveAll();
        Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions\n");
    }catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"REPUTATION_PROBE_FAIL",ex);try{var text=new java.io.StringWriter();ex.printStackTrace(new java.io.PrintWriter(text));Files.writeString(proof.resolve("failed.txt"),text.toString());}catch(Exception ignored){}}
    finally{Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);}}
    void contracts()throws Exception{
        try(var in=new java.io.BufferedReader(new java.io.InputStreamReader(Objects.requireNonNull(getResource("contracts.tsv")),java.nio.charset.StandardCharsets.UTF_8))){var rows=in.lines().toList();check(rows.size()==expected("contracts"),"all public contracts in exact source inventory");
            for(String line:rows){String[] row=line.split("\t");var owner=plugin(row[0]);Class<?> type=Class.forName(row[1],true,owner.getClass().getClassLoader());Object provider=Bukkit.getServicesManager().load(type);
                check(provider!=null&&owner.isEnabled()&&owner.getDescription().getVersion().equals(row[3])&&Integer.valueOf(1).equals(type.getMethod("apiVersion").invoke(provider))&&new HashSet<>(Arrays.asList(row[2].split(","))).equals(type.getMethod("capabilities").invoke(provider)),"provider version/capabilities: "+row[1]);
            }
        }
    }
    SupplyContract contract(String name,boolean active)throws Exception{
        long now=System.currentTimeMillis();String data=Base64.getEncoder().encodeToString(new ItemStack(Material.IRON_INGOT).serializeAsBytes());
        var value=SupplyContract.proposal(new SupplyContract.Terms(id(name),A,B,data,"Железо",8,10000,1,now,now+SupplyContract.DAY));
        if(active)value=value.accept(B,now);supplies.put(value);return value;
    }
    void cancel(String name,UUID actor)throws Exception{var value=contract(name,true);supply.action(actor,value.terms().id().toString(),"cancel",value.terms());}
    @SuppressWarnings("unchecked") Map<UUID,Double> tolls()throws Exception{
        var definition=new ExportDefinition("probe","Probe",Material.IRON_INGOT,-1,List.of(),"IRON_INGOT",new ItemStack(Material.IRON_INGOT),1,1000);
        return (Map<UUID,Double>)callPrivate(trade,"tolls",new Class<?>[]{ExportDefinition.class,List.class,Town.class,Town.class},definition,List.of(C),b,a);
    }
    void scenarios()throws Exception{
        check(score(A,"trade")==0&&score(A,"diplomatic")==0&&score(A,"military")==0,"new town starts with three neutral tracks");
        cancel("cancel1",A);cancel("cancel2",A);
        check(score(A,"trade")==-40&&score(B,"trade")==0,"two actual active supply cancellations penalize actor only");
        var old=supplies.get(id("cancel1"));supply.action(A,old.terms().id().toString(),"cancel",old.terms());
        check(score(A,"trade")==-40,"repeated cancellation does not create another breach");
        var proposal=contract("rejected",false);supply.action(B,proposal.terms().id().toString(),"reject",proposal.terms());
        check(score(B,"trade")==0,"unsigned rejected proposal has no penalty");
        check(Math.abs(trade.feeMultiplier(A)-1.04)<1e-12,"live Trade uses public Reputation fee multiplier");
        trade.setTariff(c,10);Map<UUID,Double> quote=tolls();check(quote.get(C)==104,"actual Trade toll formula charges 104 instead of 100");
        var ledger=new TradeRepository(this);ledger.load();World world=Bukkit.getWorlds().get(0);long now=System.currentTimeMillis();
        var point=new RoutePoint(world.getUID(),world.getName(),0,64,0,RoutePoint.Kind.TOWN,"probe",1,A);
        var frozen=new Caravan(FROZEN,B,A,"probe",new ItemStack(Material.IRON_INGOT),1,1,1000,1104,quote,List.of(point),now,now+60000,now+30000,false,false,CaravanStatus.ACTIVE);
        ledger.add(frozen);ledger.save();
        reputation.profiles().administer(ReputationScope.TOWN,A,ReputationAspect.TRADE,-1000,true,"fixture");
        check(tolls().get(C)==200,"new quote reflects worsened trade reputation");ledger.load();
        var restored=ledger.caravans().stream().filter(x->x.id().equals(FROZEN)).findFirst().orElseThrow();
        check(restored.escrow()==1104&&restored.tariffs().get(C)==104,"persisted caravan retains its signed escrow and tolls");
        reputation.profiles().administer(ReputationScope.TOWN,A,ReputationAspect.TRADE,-40,true,"fixture");
        diplomacy();events();municipal();
        check(score(A,"diplomatic")==5&&score(A,"military")==10&&score(A,"trade")==-55,"diplomatic, military and trade outcomes stay independent");
        check(score(B,"diplomatic")==5&&score(B,"military")==-10&&score(B,"trade")==0,"counterparty gets own diplomacy/defense outcomes only");
        // A lost reply AFTER the provider durably accepts the breach must not penalize twice.
        Object original=api;var services=Bukkit.getServicesManager();
        TownyReputationApi lost=(TownyReputationApi)Proxy.newProxyInstance(TownyReputationApi.class.getClassLoader(),new Class<?>[]{TownyReputationApi.class},(p,m,args)->{
            Object answer=m.invoke(original,args);if(m.getName().equals("recordOutcome"))throw new IllegalStateException("lost durable reply");return answer;});
        services.register(TownyReputationApi.class,lost,plugin("NeverLandTownyReputation"),ServicePriority.Highest);
        try{cancel("lost",A);check(score(A,"trade")==-75&&supplies.pendingReputation()==1,"durably applied breach remains queued after lost reply");}
        finally{services.unregister(TownyReputationApi.class,lost);}
        check(api.recordOutcome("TOWN",C,"SUPPLY_MISSED","api-replay",now,"API receipt").equals("APPLIED"),"public receipt API durably accepts independent event");
        check(api.recordOutcome("TOWN",C,"SUPPLY_MISSED","api-replay",now,"API receipt").equals("DUPLICATE"),"public receipt API rejects replay");
        denied(()->api.recordOutcome("TOWN",B,"SUPPLY_MISSED","api-replay",now,"API receipt"),"receipt cannot be reassigned to another town");
        var worker=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{api.tradeFeeMultiplier(A);}catch(Throwable ex){worker.set(ex);}});t.start();t.join(2000);check(worker.get() instanceof IllegalStateException,"profile API rejects worker-thread world access");
        // Leave one durable outbox item for real restart recovery, with no provider available to drain it.
        services.unregister(TownyReputationApi.class,api);
        denied(()->trade.feeMultiplier(A),"installed unavailable provider blocks new quotes");
        check(supplies.pendingReputation()==1,"unavailable provider preserves pending outbox on disk");
        var expected=new YamlConfiguration();expected.set("api-at",now);expected.set("trade-a",-75);expected.set("trade-b",0);expected.set("trade-c",-10);expected.save(proof.resolve("expected.yml").toFile());
    }
    void diplomacy()throws Exception{
        var diplomacy=field(plugin("NeverLandTownyDiplomacy"),DiplomacyService.class);long now=System.currentTimeMillis(),start=now-2*SupplyContract.DAY;
        var treaty=new Treaty(id("honoured"),TreatyType.NONAGGRESSION,A,B,MA,MB,"fixture","full term",Treaty.Phase.ACTIVE,start,start+1000,SupplyContract.DAY,start,start+SupplyContract.DAY,0,1000,0,Treaty.SanctionScope.NONE);
        var values=new LinkedHashMap<>(diplomacy.repository().all());values.put(treaty.id(),treaty);diplomacy.repository().commit(values,List.of(),List.of());
        diplomacy.maintain(now);check(score(A,"diplomatic")==5&&score(B,"diplomatic")==5,"full active treaty term rewards both diplomatic profiles");
        diplomacy.maintain(now);check(score(A,"diplomatic")==5,"repeated diplomatic maintenance cannot reward twice");
    }
    void events()throws Exception{
        var service=field(plugin("NeverLandTownyEvents"),EventService.class);var repo=field(plugin("NeverLandTownyEvents"),EventRepository.class);var registry=field(plugin("NeverLandTownyEvents"),EventRegistry.class);
        var def=registry.all().stream().filter(d->d.mode()==EventMode.RAID).findFirst().orElseThrow();long now=System.currentTimeMillis();
        for(var town:List.of(a,b)){var event=new ActiveEvent(town.getUUID(),def.id(),now,now+60000,0,100,0,0);event.raid(new RaidState());repo.put(event);repo.save();service.resolve(town,town==a);}
        check(score(A,"military")==10&&score(B,"military")==-10,"actual raid completion selects military reputation");
        check(!service.resolve(a,true)&&score(A,"military")==10,"already completed raid cannot award again");
    }
    void municipal()throws Exception{
        var service=field(plugin("NeverLandTownyContracts"),ContractService.class);var def=service.registry().all().iterator().next();long now=System.currentTimeMillis();
        var contract=new ActiveContract(id("municipal"),A,def.id(),now-10000,now-1000,0,1,0,Map.of());contract.restoreCompany(id("company"));contract.funded(true);service.repository().add(contract);service.repository().saveOrThrow();
        var services=Bukkit.getServicesManager();var original=services.load(CompaniesApi.class);
        CompaniesApi gateway=(CompaniesApi)Proxy.newProxyInstance(CompaniesApi.class.getClassLoader(),new Class<?>[]{CompaniesApi.class},(p,m,args)->m.getName().equals("settleEscrow")?true:m.invoke(original,args));
        services.register(CompaniesApi.class,gateway,plugin("NeverLandTownyCompanies"),ServicePriority.Highest);
        try{callPrivate(service,"resolve",new Class<?>[]{ActiveContract.class,ContractStatus.class},contract,ContractStatus.EXPIRED);}
        finally{services.unregister(CompaniesApi.class,gateway);}
        check(score(A,"trade")==-55,"assigned municipal contract expiry applies one trade penalty after settlement");
        var unclaimed=new ActiveContract(id("unclaimed"),B,def.id(),now-10000,now-1000,0,1,0,Map.of());unclaimed.funded(true);service.repository().add(unclaimed);service.repository().saveOrThrow();
        callPrivate(service,"resolve",new Class<?>[]{ActiveContract.class,ContractStatus.class},unclaimed,ContractStatus.EXPIRED);
        check(score(B,"trade")==0,"unclaimed optional public task expiry does not penalize town");
    }
    void restartChecks()throws Exception{
        var expected=YamlConfiguration.loadConfiguration(proof.resolve("expected.yml").toFile());
        for(var entry:Map.of(A,"a",B,"b",C,"c").entrySet())check(score(entry.getKey(),"trade")==expected.getInt("trade-"+entry.getValue()),"trade score survived restart: "+entry.getValue());
        check(score(A,"diplomatic")==5&&score(A,"military")==10&&score(B,"diplomatic")==5&&score(B,"military")==-10,"all diplomatic and military scores survive restart");
        check(supplies.pendingReputation()==0,"lost-reply outbox acknowledged after native restart");
        check(Math.abs(trade.feeMultiplier(A)-1.075)<1e-12,"recovered fee multiplier includes each distinct breach once");
        check(api.recordOutcome("TOWN",C,"SUPPLY_MISSED","api-replay",expected.getLong("api-at"),"API receipt").equals("DUPLICATE")&&score(C,"trade")==-10,"provider receipt identity survives restart");
        var ledger=new TradeRepository(this);ledger.load();var frozen=ledger.caravans().stream().filter(x->x.id().equals(FROZEN)).findFirst().orElseThrow();
        check(frozen.escrow()==1104&&frozen.tariffs().get(C)==104,"agreed caravan sums unchanged after server restart");
        check(YamlConfiguration.loadConfiguration(plugin("NeverLandTownyReputation").getDataFolder().toPath().resolve("data.yml").toFile()).getBoolean("profiles-initialized"),"migration marker persisted beside legacy relationships");
    }
}
