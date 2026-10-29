package ru.neverland.townypower.service;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townypower.api.*;
import ru.neverland.townypower.config.PowerSettings;
import ru.neverland.townypower.data.PowerRepository;
import ru.neverland.townypower.integration.CityBridge;
import ru.neverland.townypower.model.*;
import ru.neverland.core.SuiteState;
import java.util.*;
import java.util.concurrent.*;
public final class PowerService implements TownyPowerApi {
    private final Plugin plugin;private final PowerRepository repository;private final CityBridge bridge=new CityBridge();
    private volatile PowerSettings settings;private BukkitTask task;private boolean calculating,closing;private long nextRefresh=System.nanoTime();
    private record Cache(Map<UUID,PowerSnapshot> towns,Map<UUID,UUID> residents){Cache{towns=Map.copyOf(towns);residents=Map.copyOf(residents);}}
    private volatile Cache cache=new Cache(Map.of(),Map.of());private final Set<UUID> failures=new HashSet<>();
    private final Map<UUID,PowerWindow> windows=new HashMap<>();private Map<UUID,Accumulator.Step> storage=Map.of();
    private final PowerUptime uptime=new PowerUptime(System.nanoTime());
    private CompletionStage<Void> shutdown;private CompletionStage<Void> pending=CompletableFuture.completedFuture(null);
    public PowerService(Plugin plugin,PowerRepository repository,PowerSettings settings){this.plugin=plugin;this.repository=repository;this.settings=settings;}
    public PowerSettings settings(){try(var guard=SuiteState.guard()){return settings;}}
    private void primary(){if(!SuiteState.held())throw new IllegalStateException("Требуется согласованное состояние энергосети");}
    public void start(){try(var guard=SuiteState.guard()){primary();refresh();schedule();}}
    private void schedule(){task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,this::refresh,1L,1L);}
    public void reload(PowerSettings next){try(var guard=SuiteState.guard()){primary();if(calculating||repository.busy())throw new IllegalStateException("Дождитесь сохранения энергосети");settings=next;if(task!=null)task.cancel();schedule();/* Existing prepaid windows finish; settings never mint charge. */}}
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){if(shutdown!=null)return shutdown;closing=true;if(task!=null)task.cancel();windows.clear();cache=new Cache(Map.of(),Map.of());shutdown=pending.handle((v,error)->null).thenCompose(v->repository.prepareClose());return shutdown;}}
    public void awaitClosed(){var done=prepareClose();if(SuiteState.held()&&!done.toCompletableFuture().isDone())throw new IllegalStateException("Сначала дождитесь энергосети вне общей блокировки");try{done.toCompletableFuture().get(10,TimeUnit.SECONDS);repository.awaitClosed();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}catch(ExecutionException|TimeoutException error){throw new IllegalStateException("Сохранение энергосети не завершено",error);}}
    public void stop(){prepareClose();}
    public void refresh(){try(var guard=SuiteState.guard()){
        primary();if(closing||calculating||repository.busy())return;long now=System.nanoTime();
        // Refresh is idempotent during an already prepaid live window. Commands cannot farm charging.
        if(now-nextRefresh<0)return;nextRefresh=now+TimeUnit.SECONDS.toNanos(settings.interval());
        calculating=true;var previous=new HashMap<>(windows);previous.forEach((id,w)->{var s=cache.towns().get(id);if(s!=null)uptime.settle(id,w,s.grid(),now);});windows.clear();
        Map<UUID,PowerSnapshot> towns=new HashMap<>();Map<UUID,UUID> residents=new HashMap<>();
        var next=new HashMap<>(repository.towns());var nextStorage=new HashMap<UUID,Accumulator.Step>();var nextWindows=new HashMap<UUID,PowerWindow>();
        try{
            for(Town town:List.copyOf(TownyAPI.getInstance().getTowns())){
                UUID id=town.getUUID();TownPowerState state=repository.towns().getOrDefault(id,TownPowerState.empty());for(var r:town.getResidents())residents.put(r.getUUID(),id);
                try{
                    var old=previous.get(id);long surplus=old==null?0:old.completedEnergy(now);
                    var step=settings.accumulator().plan(settings.profiles(),bridge.buildings(id),state,surplus,settings.interval());
                    if(!step.state().equals(TownPowerState.empty()))next.put(id,step.state());else next.remove(id);
                    nextStorage.put(id,step);nextWindows.put(id,PowerWindow.start(now,Math.min(step.grid().spare(),settings.accumulator().chargePower()),settings.interval()));
                    towns.put(id,new PowerSnapshot(id,town.getName(),false,"Сеть рассчитана",step.grid(),step.state()));failures.remove(id);
                }catch(Exception|LinkageError ex){towns.put(id,unavailable(id,town.getName(),state,"Расчёт сети недоступен"));if(failures.add(id))plugin.getLogger().log(java.util.logging.Level.WARNING,"Не удалось рассчитать энергосеть города "+town.getName(),ex);}
            }
            pending=repository.replaceAsync(next).handle((saved,error)->{try(var committed=SuiteState.guard()){
                calculating=false;if(closing)return null;
                if(error!=null){cache=new Cache(Map.of(),residents);storage=Map.of();plugin.getLogger().log(java.util.logging.Level.SEVERE,"Заряд не подтверждён: энергоснабжение приостановлено, списание не повторяется",error);return null;}
                cache=new Cache(towns,residents);storage=Map.copyOf(nextStorage);long acknowledged=System.nanoTime();nextWindows.forEach((id,window)->windows.put(id,window.acknowledged(acknowledged)));uptime.retain(towns.keySet());failures.retainAll(towns.keySet());return null;
            }});
        }catch(Exception|LinkageError ex){calculating=false;cache=new Cache(Map.of(),Map.of());storage=Map.of();plugin.getLogger().log(java.util.logging.Level.WARNING,"Энергосеть временно недоступна",ex);}
    }}
    private static PowerSnapshot unavailable(UUID id,String name,TownPowerState state,String reason){return new PowerSnapshot(id,name,true,reason,new PowerEngine.Grid(0,0,0,Map.of()),state);}
    private PowerSnapshot effective(PowerSnapshot s){if(s==null||s.paused())return s;var window=windows.get(s.townId());return window!=null&&window.live(System.nanoTime())?new PowerSnapshot(s.townId(),s.townName(),false,s.status(),s.grid(),repository.towns().getOrDefault(s.townId(),s.state())):unavailable(s.townId(),s.townName(),repository.towns().getOrDefault(s.townId(),s.state()),"Ожидается подтверждение следующего периода питания");}
    @Override public boolean powered(UUID town,String project){try(var guard=SuiteState.guard()){var p=settings.profiles().get(project);if(p==null||!p.enabled()||!p.relevant())return true;var s=effective(cache.towns().get(town));if(closing||s==null||s.paused())return false;var a=s.grid().buildings().get(project);return a!=null&&a.powered();}}
    @Override public String status(UUID town,String project){try(var guard=SuiteState.guard()){var p=settings.profiles().get(project);if(p==null||!p.relevant())return "Энергия не требуется";if(!p.enabled())return PowerEngine.Status.EXEMPT.title;var s=effective(cache.towns().get(town));if(s==null||s.paused())return s==null?PowerEngine.Status.UNAVAILABLE.title:s.status();var a=s.grid().buildings().get(project);return a==null?PowerEngine.Status.NOT_BUILT.title:a.status().title;}}
    @Override public Optional<PowerSnapshot> power(UUID town){try(var guard=SuiteState.guard()){return Optional.ofNullable(effective(cache.towns().get(town)));}}
    @Override public Optional<PowerSnapshot> residentPower(UUID resident){try(var guard=SuiteState.guard()){UUID town=cache.residents().get(resident);return Optional.ofNullable(town==null?null:effective(cache.towns().get(town)));}}
    @Override public Collection<PowerSnapshot> towns(){try(var guard=SuiteState.guard()){return cache.towns().values().stream().map(this::effective).toList();}}
    @Override public Map<String,Object> uptime(UUID town,String project){try(var guard=SuiteState.guard()){
        var p=settings.profiles().get(project);var s=cache.towns().get(town);return uptime.read(town,project,p!=null&&p.enabled()&&p.relevant(),windows.get(town),s==null?null:s.grid(),System.nanoTime());
    }}
    @Override public Map<String,Object> energy(UUID town){try(var guard=SuiteState.guard()){
        var step=storage.get(town);var state=repository.towns().getOrDefault(town,TownPowerState.empty());var s=effective(cache.towns().get(town));
        return Map.of("charge",state.charge(),"capacity",step==null?0L:step.capacity(),"backupPower",s==null||s.paused()?0L:s.grid().backup(),"charged",step==null?0L:step.charged(),"discharged",step==null?0L:step.discharged(),"periodSeconds",settings.interval(),"ready",!closing&&s!=null&&!s.paused());
    }}
    public Town town(Player p){try(var guard=SuiteState.guard()){var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();}}
    @Override public Map<String,Object> development(UUID town,Map<String,Integer> plannedLevels){try(var guard=SuiteState.guard()){
        try{
            var planned=DevelopmentEstimate.levels(plannedLevels);var cached=cache.towns().get(town);
            if(closing||!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||TownyAPI.getInstance().getTown(town)==null||cached==null||cached.paused())throw new IllegalStateException("Прогноз энергосети временно недоступен");
            forecastProvider("NeverLandTownyUpkeep","ru.neverland.townyupkeep.api.TownyUpkeepApi","active");forecastProvider("NeverLandTownyDistricts","ru.neverland.townydistricts.api.TownyDistrictsApi","multiplier");forecastProvider("NeverLandTownySpecialization","ru.neverland.townyspecialization.api.TownySpecializationApi","canUseBuilding");
            var buildings=bridge.buildings(town);var allowed=new HashMap<String,Boolean>();for(String id:planned.keySet())allowed.put(id,ru.neverland.integration.SpecializationAccess.allowed(town,id));
            return DevelopmentEstimate.calculate(settings,buildings,planned,repository.towns().getOrDefault(town,TownPowerState.empty()),allowed);
        }catch(Exception|LinkageError unavailable){return Map.of("known",false,"reason",unavailable instanceof IllegalArgumentException?unavailable.getMessage():"Прогноз энергосети недоступен: проверьте состояние города и связанных модулей","periodSeconds",settings.interval());}
    }}
    private static void forecastProvider(String module,String api,String capability){var connection=ru.neverland.core.ApiServices.connect(module,api,1,capability);if(!connection.ready()&&connection.state()!=ru.neverland.core.ApiServices.State.NOT_INSTALLED)throw new IllegalStateException("Поставщик прогноза недоступен");}
    public boolean manager(Player p,Town t){try(var guard=SuiteState.guard()){var r=TownyAPI.getInstance().getResident(p);return r!=null&&t.equals(r.getTownOrNull())&&(t.isMayor(r)||r.hasTownRank("assistant")||p.hasPermission("neverlandtownypower.manage"));}}
    public CompletionStage<Void> change(UUID town,String project,Boolean stop,Integer priority){return change(town,project,stop,priority,null);}
    public CompletionStage<Void> change(UUID town,String project,Boolean stop,Integer priority,Integer emergency){try(var guard=SuiteState.guard()){
        primary();if(closing||calculating||repository.busy())throw new IllegalStateException("Энергосеть сохраняется; повторите через несколько секунд");var p=settings.profiles().get(project);if(p==null||!p.enabled()||!p.relevant())throw new IllegalArgumentException("Укажите здание энергетической сети");var s=power(town).orElseThrow(()->new IllegalStateException("Ожидается расчёт города"));var a=s.grid().buildings().get(project);if(s.paused()||a==null||a.level()==0)throw new IllegalArgumentException("Здание не построено или сеть недоступна");
        var state=repository.towns().getOrDefault(town,TownPowerState.empty());if(stop!=null)state=state.stop(project,stop);if(priority!=null)state=state.priority(project,priority);if(emergency!=null)state=state.emergency(project,emergency);
        var next=new HashMap<>(repository.towns());if(state.equals(TownPowerState.empty()))next.remove(town);else next.put(town,state);
        return repository.replaceAsync(next); // Only acknowledged controls are used at the next regular period.
    }}
}
