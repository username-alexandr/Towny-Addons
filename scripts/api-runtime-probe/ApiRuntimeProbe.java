package ru.neverland.runtime;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.ApiServices;
import ru.neverland.townymarket.*;
import ru.neverland.townybuilds.data.DataStore;

/** Opt-in native integration audit. The fixture is never included in published plugin ZIPs. */
public final class ApiRuntimeProbe extends JavaPlugin {
    static UUID id(String s){return UUID.nameUUIDFromBytes(("api-audit-0271-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID TOWN=id("town"), MAYOR=id("mayor"), LOT=id("lot"), ORDER=id("order");
    Path proof;int checks;boolean restart;MarketGateway market;DataStore data;
    void check(boolean ok,String label)throws Exception {if(!ok)throw new AssertionError(label);checks++;getLogger().info("CHECK "+label);Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    Plugin owner(String n){return Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(n),n);}
    Class<?> type(String plugin,String contract)throws Exception{return Class.forName(contract,true,owner(plugin).getClass().getClassLoader());}
    Object api(String plugin,String contract)throws Exception{return Objects.requireNonNull(Bukkit.getServicesManager().load(type(plugin,contract)),contract);}
    Object invoke(Object o,String name,Class<?>[] sig,Object... args)throws Exception{return o.getClass().getMethod(name,sig).invoke(o,args);}
    static <T>T field(Object o,Class<T> type)throws Exception{for(var f:o.getClass().getDeclaredFields())if(type.isAssignableFrom(f.getType())){f.setAccessible(true);return type.cast(f.get(o));}throw new IllegalArgumentException(type.getName());}
    @Override public void onEnable(){if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}Bukkit.getScheduler().runTaskLater(this,this::run,100);}
    void run(){try{
        proof=getDataFolder().toPath();Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==31,"all 31 addons enabled");
        if(!restart){check(TownyAPI.getInstance().getTown(TOWN)==null,"fresh disposable fixture");var u=TownyUniverse.getInstance();u.newTownInternal("ApiAudit",TOWN);var t=TownyAPI.getInstance().getTown(TOWN);var resident=u.getDataSource().newResident("ApiAuditor",MAYOR);resident.setTown(t);t.setMayor(resident);resident.save();t.save();}
        data=field(owner("NeverLandTownyBuilds"),DataStore.class);
        market=new MarketGateway((JavaPlugin)owner("NeverLandTownyMarket"),new MarketRepository(proof.resolve("market-unused.yml")));
        contracts();consumers();bridges();market();threading();lifecycle();
        TownyUniverse.getInstance().getDataSource().saveAll();
        Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions\n");
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"API_AUDIT_FAIL",e);try{var w=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(w));Files.writeString(proof.resolve("failed.txt"),w.toString());}catch(Exception ignored){}}finally{Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);}}
    List<String[]> rows(String resource)throws Exception{try(var in=new java.io.BufferedReader(new java.io.InputStreamReader(Objects.requireNonNull(getResource(resource)),java.nio.charset.StandardCharsets.UTF_8))){return in.lines().filter(s->!s.isBlank()).map(s->s.split("\t",-1)).toList();}}
    void contracts()throws Exception{
        Map<String,String> reads=Map.ofEntries(
            Map.entry("TownyArchaeologyApi","museum"),Map.entry("BuildingStorageApi","stock"),Map.entry("MarketStorageApi","snapshot"),Map.entry("MunicipalStorageApi","capacity"),Map.entry("TownArmyApi","soldiers"),Map.entry("TownyBuildsApi","buildingFootprints"),Map.entry("WarehouseApi","transferred"),Map.entry("TownyCampsApi","camp"),Map.entry("TownyChroniclesApi","entries"),Map.entry("TownyCitizensApi","passport"),Map.entry("CompaniesApi","companyName"),Map.entry("MintTownyContractsApi","activeContracts"),Map.entry("TownyCouncilApi","holders"),Map.entry("TownyDiplomacyApi","relations"),Map.entry("TownyDistrictsApi","districts"),Map.entry("TownyElectionsApi","snapshot"),Map.entry("MintTownyEspionageApi","snapshot"),Map.entry("MintTownyEventsApi","activeEvent"),Map.entry("TownyGovernanceApi","snapshot"),Map.entry("TownyIdeologiesApi","ideology"),Map.entry("TownyJobsApi","supports"),Map.entry("MarketApi","offers"),Map.entry("TownyPoliciesApi","policies"),Map.entry("TownyPopulationApi","population"),Map.entry("TownyPowerApi","power"),Map.entry("TownyReputationApi","snapshot"),Map.entry("TownyResearchApi","research"),Map.entry("TownyResourcesApi","resources"),Map.entry("TownySpecializationApi","specialization"),Map.entry("NeverLandTownyTaxesApi","serverReserve"),Map.entry("MintTownyTradeApi","activeRoutes"),Map.entry("TownyTreasuryApi","treasury"),Map.entry("TownyUpkeepApi","buildings"));
        var rows=rows("contracts.tsv");check(rows.size()==33,"inventory contains 33 public contracts");
        for(String[] row:rows){Class<?> c=type(row[0],row[1]);Object service=api(row[0],row[1]);var registration=Bukkit.getServicesManager().getRegistration(c);
            check(registration!=null&&registration.getPlugin()==owner(row[0])&&owner(row[0]).getDescription().getVersion().equals(row[3])&&Integer.valueOf(1).equals(c.getMethod("apiVersion").invoke(service))&&new HashSet<>(Arrays.asList(row[2].split(","))).equals(c.getMethod("capabilities").invoke(service)),"registered v1 provider: "+row[1]);
            String read=Objects.requireNonNull(reads.get(c.getSimpleName()),row[1]);Method method=Arrays.stream(c.getMethods()).filter(m->m.getName().equals(read)).findFirst().orElseThrow();Object[] args=new Object[method.getParameterCount()];
            for(int i=0;i<args.length;i++){Class<?> t=method.getParameterTypes()[i];args[i]=t==UUID.class?(read.equals("passport")||read.equals("camp")?MAYOR:TOWN):t==String.class?"market":t==int.class?0:t==boolean.class?false:t==ItemStack.class?new ItemStack(Material.IRON_INGOT):t.isEnum()?t.getEnumConstants()[0]:null;if(args[i]==null)throw new AssertionError("Unspecified read argument "+method);}
            method.invoke(service,args);check(true,"native read: "+c.getSimpleName()+"."+read);
            var connection=ApiServices.require(row[0],row[1],row[2].split(","));
            check(connection.invoke("storageMetrics",new Class<?>[]{}) instanceof List<?> && connection.invoke("integrationDiagnostics",new Class<?>[]{}) instanceof List<?>,"metadata callable: "+c.getSimpleName());
        }
    }
    void consumers()throws Exception{int n=0;for(String[] r:rows("consumers.tsv")){check(ApiServices.connect(r[0],r[1],1,r[2].isEmpty()?new String[]{}:r[2].split(",")).ready(),"consumer requirement "+(++n)+": "+r[1]+" "+r[2]);}}
    void bridges()throws Exception{
        var companyContracts=new ru.neverland.townycompanies.ContractsBridge();
        check(companyContracts.offers(TOWN)!=null&&companyContracts.count(id("missing-company"))==0,"Companies reads Contracts public service");
        var companies=new ru.neverland.mintcontracts.integration.CompaniesBridge();
        for(String method:List.of("canTake","canManage","canContribute")){check(!companies.allow(method,MAYOR,id("missing-company"),TOWN),"dynamic Companies permission denies unknown company: "+method);var status=ApiServices.statuses().stream().filter(s->s.contract().equals("ru.neverland.townycompanies.api.CompaniesApi")).findFirst().orElseThrow();
            // Bridge has its own shaded diagnostics; verify the actual service method as well.
            check(!Boolean.TRUE.equals(ApiServices.call("NeverLandTownyCompanies",status.contract(),method,new Class<?>[]{UUID.class,UUID.class,UUID.class},MAYOR,id("missing-company"),TOWN)),"public Companies method callable: "+method);}
        Class companyType=type("NeverLandTownyCompanies","ru.neverland.townycompanies.api.CompaniesApi");Object original=api("NeverLandTownyCompanies",companyType.getName());
        Object permitted=Proxy.newProxyInstance(companyType.getClassLoader(),new Class<?>[]{companyType},(p,m,args)->Set.of("canTake","canManage","canContribute").contains(m.getName())?true:m.invoke(original,args));
        Bukkit.getServicesManager().register(companyType,permitted,owner("NeverLandTownyCompanies"),ServicePriority.Highest);
        try{for(String method:List.of("canTake","canManage","canContribute"))check(companies.allow(method,MAYOR,id("missing-company"),TOWN),"dynamic bridge executes public method: "+method);}
        finally{Bukkit.getServicesManager().unregister(companyType,permitted);}
        check(companies.name(id("missing-company"))!=null,"Companies name bridge callable");
    }
    void market()throws Exception{
        check(market.available(),"MarketGateway accepts current Builds capabilities");
        var three=new Class<?>[]{UUID.class,UUID.class,UUID.class};
        if(!restart){ItemStack[] storage=data.town(TOWN).storage();storage[0]=new ItemStack(Material.IRON_INGOT,16);data.town(TOWN).setStorage(storage,storage.length);data.saveOrThrow();
            check("OPEN".equals(market.call("open",new Class<?>[]{UUID.class,UUID.class,ItemStack.class,int.class},TOWN,LOT,new ItemStack(Material.IRON_INGOT),10)),"Market gateway opens actual Builds stock");
            check("HELD".equals(market.call("reserve",new Class<?>[]{UUID.class,UUID.class,UUID.class,UUID.class,boolean.class,int.class},TOWN,LOT,ORDER,MAYOR,false,3)),"Market gateway reserves stock");}
        check("RETURNED".equals(market.call("refund",three,TOWN,LOT,ORDER)),"refund through consumer is idempotent"+(restart?" after restart":""));
        check("CLOSED".equals(market.call("close",new Class<?>[]{UUID.class,UUID.class},TOWN,LOT)),"close through consumer is idempotent");
        check(Arrays.stream(data.town(TOWN).storage()).filter(Objects::nonNull).filter(i->i.getType()==Material.IRON_INGOT).mapToInt(ItemStack::getAmount).sum()==16,"stock conserved across refund and close");
        if(restart){market.call("acknowledge",three,TOWN,LOT,ORDER);market.call("forget",new Class<?>[]{UUID.class,UUID.class},TOWN,LOT);check(data.town(TOWN).marketStock().get(LOT)==null,"completed stock receipt can be retired after restart");}
    }
    @SuppressWarnings({"unchecked","rawtypes"}) void lifecycle()throws Exception{
        String plugin="NeverLandTownyCompanies",contract="ru.neverland.townycompanies.api.CompaniesApi";Class c=type(plugin,contract);Object original=api(plugin,contract);Plugin provider=owner(plugin);
        check(ApiServices.connect("NeverLandMissingProbe",contract,1).state()==ApiServices.State.NOT_INSTALLED,"missing plugin diagnosed distinctly");
        check(ApiServices.connect(plugin,contract,2).state()==ApiServices.State.INCOMPATIBLE_API,"wrong major diagnosed distinctly");
        check(ApiServices.connect(plugin,contract,1,"release").state()==ApiServices.State.INCOMPATIBLE_API,"missing capability diagnosed distinctly");
        var saved=ApiServices.require(plugin,contract,"companyName");Bukkit.getServicesManager().unregister(c,original);
        try{check(ApiServices.connect(plugin,contract,1).state()==ApiServices.State.SERVICE_UNAVAILABLE,"unregistered provider diagnosed distinctly");reject(saved,"companyName",new Class<?>[]{UUID.class},TOWN);check(status(contract)==ApiServices.State.SERVICE_UNAVAILABLE,"saved connection preserves unavailable diagnosis");}
        finally{Bukkit.getServicesManager().register(c,original,provider,ServicePriority.Normal);}
        Object replacement=Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},(p,m,a)->m.getName().equals("apiVersion")?1:m.getName().equals("capabilities")?Set.of("companyName"):throwCall());
        Bukkit.getServicesManager().register(c,replacement,provider,ServicePriority.Highest);
        try{reject(saved,"companyName",new Class<?>[]{UUID.class},TOWN);check(status(contract)==ApiServices.State.SERVICE_UNAVAILABLE,"saved connection rejects replaced provider");var failing=ApiServices.require(plugin,contract,"companyName");reject(failing,"companyName",new Class<?>[]{UUID.class},TOWN);check(status(contract)==ApiServices.State.INVOCATION_ERROR,"provider exception diagnosed distinctly");}
        finally{Bukkit.getServicesManager().unregister(c,replacement);}
        check(ApiServices.call(plugin,contract,"companyName",new Class<?>[]{UUID.class},TOWN)!=null&&status(contract)==ApiServices.State.READY,"real provider recovers after replacement");
        // Disable a read-only provider at the end; next boot must register it again.
        String p="NeverLandTownyPopulation",a="ru.neverland.townypopulation.api.TownyPopulationApi";var connection=ApiServices.require(p,a,"population");Bukkit.getPluginManager().disablePlugin(owner(p));
        check(ApiServices.connect(p,a,1).state()==ApiServices.State.DISABLED,"disabled plugin diagnosed distinctly");reject(connection,"population",new Class<?>[]{UUID.class},TOWN);check(status(a)==ApiServices.State.DISABLED,"saved connection preserves disabled diagnosis");
    }
    static Object throwCall(){throw new IllegalStateException("intentional API fixture failure");}
    ApiServices.State status(String contract){return ApiServices.statuses().stream().filter(s->s.contract().equals(contract)).findFirst().orElseThrow().state();}
    void reject(ApiServices.Connection c,String method,Class<?>[] signature,Object... args)throws Exception{try{c.invoke(method,signature,args);}catch(ReflectiveOperationException|IllegalStateException expected){check(true,"rejected unsafe call: "+method);return;}throw new AssertionError("Unsafe call succeeded: "+method);}
    void threading()throws Exception{
        var connection=ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","consumeInsurance");
        List<Runnable> calls=new ArrayList<>();calls.add(()->{try{connection.invoke("consumeInsurance",new Class<?>[]{UUID.class,double.class},TOWN,10.0);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}});
        for(String[] spec:List.of(new String[]{"NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","consumeInsurance"},new String[]{"NeverLandTownyContracts","ru.neverland.mintcontracts.api.MintTownyContractsApi","addProgress"},new String[]{"NeverLandTownyEvents","ru.neverland.mintevents.api.MintTownyEventsApi","addProgress"},new String[]{"NeverLandTownyChronicles","ru.neverland.townychronicles.api.TownyChroniclesApi","record"})){
            Class<?> c=type(spec[0],spec[1]);Object service=api(spec[0],spec[1]);Method m=Arrays.stream(c.getMethods()).filter(x->x.getName().equals(spec[2])).findFirst().orElseThrow();Object[] args=new Object[m.getParameterCount()];for(int i=0;i<args.length;i++){Class<?> t=m.getParameterTypes()[i];args[i]=t==UUID.class?TOWN:t==int.class?1:t==double.class?10.0:t==long.class?1L:t==String.class?"fixture":t==List.class?List.of():t.isEnum()?t.getEnumConstants()[0]:null;}calls.add(()->{try{m.invoke(service,args);}catch(InvocationTargetException e){if(e.getCause() instanceof IllegalStateException x)throw x;throw new RuntimeException(e);}catch(Exception e){throw new RuntimeException(e);}});
        }
        for(Runnable call:calls){AtomicReference<Throwable> error=new AtomicReference<>();Thread worker=new Thread(()->{try{call.run();}catch(Throwable e){error.set(e);}},"api-audit-worker");worker.start();worker.join(3000);check(!worker.isAlive()&&error.get() instanceof IllegalStateException,"worker mutation rejected before data access "+calls.indexOf(call));}
    }
}
