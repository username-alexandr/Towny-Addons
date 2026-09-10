package ru.neverland.townypower.service;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townypower.api.*;
import ru.neverland.townypower.config.PowerSettings;
import ru.neverland.townypower.data.PowerRepository;
import ru.neverland.townypower.integration.CityBridge;
import ru.neverland.townypower.model.*;
import java.util.*;
public final class PowerService implements TownyPowerApi {
    private final Plugin plugin;private final PowerRepository repository;private final CityBridge bridge=new CityBridge();
    private volatile PowerSettings settings;private BukkitTask task;
    private record Cache(Map<UUID,PowerSnapshot> towns,Map<UUID,UUID> residents){Cache{towns=Map.copyOf(towns);residents=Map.copyOf(residents);}}
    private volatile Cache cache=new Cache(Map.of(),Map.of());private final Set<UUID> failures=new HashSet<>();
    public PowerService(Plugin plugin,PowerRepository repository,PowerSettings settings){this.plugin=plugin;this.repository=repository;this.settings=settings;}
    public PowerSettings settings(){return settings;}
    private void primary(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Расчёт сети выполняется в основном потоке");}
    public void start(){primary();refresh();schedule();}
    private void schedule(){task=Bukkit.getScheduler().runTaskTimer(plugin,this::refresh,20L*settings.interval(),20L*settings.interval());}
    public void reload(PowerSettings next){primary();settings=next;if(task!=null)task.cancel();refresh();schedule();}
    public void stop(){if(task!=null)task.cancel();cache=new Cache(Map.of(),Map.of());}
    public void refresh(){primary();Map<UUID,PowerSnapshot> towns=new HashMap<>();Map<UUID,UUID> residents=new HashMap<>();
        try{for(Town town:List.copyOf(TownyAPI.getInstance().getTowns())){
            UUID id=town.getUUID();TownPowerState state=repository.towns().getOrDefault(id,TownPowerState.empty());
            for(var r:town.getResidents())residents.put(r.getUUID(),id);
            try{var grid=PowerEngine.calculate(settings.profiles(),bridge.buildings(id),state);towns.put(id,new PowerSnapshot(id,town.getName(),false,"Сеть рассчитана",grid,state));failures.remove(id);}
            catch(Exception|LinkageError ex){towns.put(id,new PowerSnapshot(id,town.getName(),true,"Расчёт сети недоступен",new PowerEngine.Grid(0,0,0,Map.of()),state));if(failures.add(id))plugin.getLogger().log(java.util.logging.Level.WARNING,"Не удалось рассчитать энергосеть города "+town.getName(),ex);}
        }cache=new Cache(towns,residents);failures.retainAll(towns.keySet());}
        catch(Exception|LinkageError ex){cache=new Cache(Map.of(),Map.of());plugin.getLogger().log(java.util.logging.Level.WARNING,"Энергосеть временно недоступна",ex);}
    }
    @Override public boolean powered(UUID town,String project){var p=settings.profiles().get(project);if(p==null||!p.enabled()||!p.relevant())return true;var s=cache.towns().get(town);if(s==null||s.paused())return false;var a=s.grid().buildings().get(project);return a!=null&&a.powered();}
    @Override public String status(UUID town,String project){var p=settings.profiles().get(project);if(p==null||!p.relevant())return "Энергия не требуется";if(!p.enabled())return PowerEngine.Status.EXEMPT.title;var s=cache.towns().get(town);if(s==null||s.paused())return PowerEngine.Status.UNAVAILABLE.title;var a=s.grid().buildings().get(project);return a==null?PowerEngine.Status.NOT_BUILT.title:a.status().title;}
    @Override public Optional<PowerSnapshot> power(UUID town){return Optional.ofNullable(cache.towns().get(town));}
    @Override public Optional<PowerSnapshot> residentPower(UUID resident){var current=cache;UUID town=current.residents().get(resident);return Optional.ofNullable(town==null?null:current.towns().get(town));}
    @Override public Collection<PowerSnapshot> towns(){return cache.towns().values();}
    public Town town(Player p){var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();}
    public boolean manager(Player p,Town t){var r=TownyAPI.getInstance().getResident(p);return r!=null&&t.equals(r.getTownOrNull())&&(t.isMayor(r)||r.hasTownRank("assistant")||p.hasPermission("neverlandtownypower.manage"));}
    public void change(UUID town,String project,Boolean stop,Integer priority)throws Exception{primary();var p=settings.profiles().get(project);if(p==null||!p.enabled()||!p.relevant())throw new IllegalArgumentException("Укажите здание энергетической сети");var s=power(town).orElseThrow(()->new IllegalStateException("Ожидается расчёт города"));var a=s.grid().buildings().get(project);if(s.paused()||a==null||a.level()==0)throw new IllegalArgumentException("Здание не построено или сеть недоступна");
        var state=repository.towns().getOrDefault(town,TownPowerState.empty());if(stop!=null)state=state.stop(project,stop);if(priority!=null)state=state.priority(project,priority);
        var next=new HashMap<>(repository.towns());if(state.equals(TownPowerState.empty()))next.remove(town);else next.put(town,state);repository.replace(next);refresh();
    }
}
