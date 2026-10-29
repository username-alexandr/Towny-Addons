package ru.neverland.townycrime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.ApiServices;
import ru.neverland.townycrime.api.*;
public final class CrimeService implements TownyCrimeApi {
    private final JavaPlugin plugin;private final CrimeRepository repository;private final CrimeBridge bridge=new CrimeBridge();
    private CrimePatrolService patrols;public CrimePatrolService patrols(){return patrols;}public void patrols(CrimePatrolService value){patrols=value;}
    private CrimeSettings settings;private BukkitTask task;private final Map<UUID,String> faults=new HashMap<>();private final Map<UUID,Long> warnings=new HashMap<>();
    private final TheftProcessor.Store store=new TheftProcessor.Store(){public CrimeState get(UUID id){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return repository.all().get(id);} }public void put(CrimeState s)throws Exception{ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {repository.put(s);} }};
    public CrimeService(JavaPlugin p,CrimeRepository r,CrimeSettings s){plugin=p;repository=r;settings=s;}
    public void start()throws Exception{ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        // Grant one online cycle after every start, never replay downtime cycles/incidents.
        long now=System.currentTimeMillis();var next=new LinkedHashMap<UUID,CrimeState>();
        for(var v:repository.all().values())next.put(v.town(),new CrimeState(v.town(),v.level(),now+settings.cycle(),Math.max(v.nextIncident(),now+settings.cycle()),v.incident()));repository.commit(next);
        task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,this::pulse,40,100);
    } }
    public void stop(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(task!=null)task.cancel();} }
    public void reload(CrimeSettings next){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {ApiServices.primaryThread();settings=next;} }
    private void writable(){if(!repository.writable())throw new IllegalStateException("Преступность: ошибка сохранения, требуется исправление базы и перезапуск");}
    private CrimeState state(UUID town)throws Exception{
        var state=repository.all().get(town);if(state==null){long now=System.currentTimeMillis();state=new CrimeState(town,0,now+settings.cycle(),now+settings.cooldown(),null);repository.put(state);}return state;
    }
    void pulse(){ApiServices.primaryThread();long now=System.currentTimeMillis();
        if(patrols!=null)try{for(var saved:repository.all().values())patrols.observe(saved);patrols.tick();}catch(RuntimeException|LinkageError error){plugin.getLogger().warning("Журнал патрулей недоступен; расчёт преступности продолжается: "+error.getMessage());}
        // Pending reservations are reconciled even if a town was deleted or its Population paused.
        for(var s:List.copyOf(repository.all().values()))if(s.incident()!=null&&s.incident().pending())try{writable();if(TheftProcessor.resume(s.town(),store,bridge))announce(repository.all().get(s.town()));}catch(Exception|LinkageError ex){fault(s.town(),ex,now);}
        for(var town:TownyAPI.getInstance().getTowns())try{
            writable();UUID id=town.getUUID();var s=state(id);
            long shield=ru.neverland.core.NewcomerProtection.remaining(plugin,id);
            if(shield>0){if(now>=s.nextCycle())repository.put(new CrimeState(id,s.level(),now+settings.cycle(),Math.max(s.nextIncident(),now+shield),s.incident()));faults.remove(id);continue;}
            var inputs=bridge.inputs(id);
            if(now>=s.nextCycle()){
                double level=CrimeEngine.advance(s.level(),inputs,settings);var incident=s.incident();long nextIncident=s.nextIncident();
                if(now>=nextIncident&&(incident==null||!incident.pending()&&now>=incident.until())&&level>=settings.incidentMinimum()&&ThreadLocalRandom.current().nextDouble()<settings.chance()){
                    // Resolve optional resources before committing an intent. Missing stock becomes extortion.
                    String resource="";long amount=0;boolean burglary=ThreadLocalRandom.current().nextBoolean();
                    if(burglary){var connection=ApiServices.connect("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi",1,"resources");
                        if(connection.state()!=ApiServices.State.NOT_INSTALLED){if(!connection.ready())throw new IllegalStateException("Ресурсы: "+connection.state());var options=new ArrayList<>(bridge.available(id,settings.resources()).entrySet());Collections.shuffle(options);for(var e:options){long n=CrimeEngine.theft(e.getValue(),settings);if(n>0){resource=e.getKey();amount=n;break;}}}}
                    incident=new CrimeState.Incident(UUID.randomUUID(),amount>0?"BURGLARY":"EXTORTION",resource,amount,"PLANNED",now,now+settings.duration());nextIncident=now+settings.cooldown();
                }
                repository.put(new CrimeState(id,level,now+settings.cycle(),nextIncident,incident));
            }
            faults.remove(id);
        }catch(Exception|LinkageError ex){fault(town.getUUID(),ex,now);}
    }
    private void fault(UUID town,Throwable ex,long now){faults.put(town,"Расчёт приостановлен: проверьте население, стражу и журнал ресурсов");if(now-warnings.getOrDefault(town,0L)>60000){warnings.put(town,now);plugin.getLogger().warning("Преступность "+town+": "+ex);}}
    private void announce(CrimeState s){if(patrols!=null)try{patrols.observe(s);}catch(RuntimeException error){plugin.getLogger().warning("Происшествие ожидает записи в журнал патрулей");}var i=s.incident();var town=TownyAPI.getInstance().getTown(s.town());if(town!=null){String message=i.kind().equals("BURGLARY")?"Кража со склада: "+CrimeMenu.resource(i.resource())+" — "+CrimeMenu.number(i.amount()/1000.0):"Вымогательство: временно выросли потери городской лавки";
        for(var resident:town.getResidents()){var player=Bukkit.getPlayer(resident.getUUID());if(player!=null)player.sendMessage(ru.neverland.core.MenuStyle.decode("&e[Городская стража] &f"+message+". &b/t crime"));}}
        Bukkit.getPluginManager().callEvent(new CrimeIncidentEvent(i.id(),s.town(),i.kind(),i.resource(),i.amount()));
    }
    @Override public Optional<Map<String,Object>> crime(UUID town){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {ApiServices.primaryThread();Objects.requireNonNull(town);if(TownyAPI.getInstance().getTown(town)==null)return Optional.empty();
        var s=repository.all().get(town);Map<String,Object> result=new LinkedHashMap<>();result.put("town",town);result.put("level",s==null?0.0:s.level());result.put("nextCycle",s==null?0L:s.nextCycle());
        boolean paused=false;boolean protectedTown=false;String status="Расчёт работает";CrimeEngine.Inputs inputs=null;
        try{writable();protectedTown=ru.neverland.core.NewcomerProtection.remaining(plugin,town)>0;if(protectedTown)status="Щит новичка: новые происшествия и потери дохода отключены";inputs=bridge.inputs(town);if(s==null)throw new IllegalStateException("Первый расчёт ещё не выполнен");if(faults.containsKey(town))throw new IllegalStateException(faults.get(town));}catch(Exception|LinkageError ex){paused=true;status="Расчёт приостановлен; новые продажи ожидают восстановления";}
        result.put("paused",paused);result.put("status",status);result.put("happiness",inputs==null?0.0:inputs.happiness());result.put("guard",inputs==null?0.0:CrimeEngine.guard(inputs,settings));result.put("workers",inputs==null?0:inputs.workers());result.put("target",inputs==null?0.0:CrimeEngine.target(inputs,settings));
        var i=s==null?null:s.incident();long now=System.currentTimeMillis();boolean extortion=i!=null&&i.kind().equals("EXTORTION")&&i.active(now);
        result.put("newcomerProtected",protectedTown);result.put("incomeBasisPoints",protectedTown?10000:CrimeEngine.income(s==null?0:s.level(),extortion,settings));result.put("incident",i==null?Map.of():Map.<String,Object>of("id",i.id(),"kind",i.kind(),"resource",i.resource(),"amount",i.amount(),"phase",i.phase(),"until",i.until(),"active",i.active(now)));
        return Optional.of(Map.copyOf(result));
    } }
    @Override public Map<String,Object> patrols(UUID town){return patrols==null?Map.of("available",false,"pending",false,"rows",List.of()):patrols.report(town);}
    @Override public Optional<Map<String,Object>> incidentEvidence(UUID incident){return patrols==null?Optional.empty():patrols.incidentEvidence(incident);}
    @Override public Collection<Map<String,Object>> towns(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {ApiServices.primaryThread();return TownyAPI.getInstance().getTowns().stream().map(t->crime(t.getUUID()).orElseThrow()).toList();} }
    @Override public int shopIncomeBasisPoints(UUID town){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {var snapshot=crime(town).orElseThrow(()->new IllegalArgumentException("Город не найден"));if(Boolean.TRUE.equals(snapshot.get("paused")))throw new IllegalStateException((String)snapshot.get("status"));return (Integer)snapshot.get("incomeBasisPoints");} }
}
