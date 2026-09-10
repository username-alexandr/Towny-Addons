package ru.neverland.townyresearch.service;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townyresearch.api.*;
import ru.neverland.townyresearch.config.ResearchSettings;
import ru.neverland.townyresearch.data.ResearchRepository;
import ru.neverland.townyresearch.integration.CityBridge;
import ru.neverland.townyresearch.model.*;
import java.util.*;
public final class ResearchService implements TownyResearchApi {
    private final Plugin plugin;private final ResearchRepository repository;private final CityBridge bridge=new CityBridge();private final ResearchProcessor processor;
    private volatile ResearchSettings settings;private BukkitTask task;private long warned;
    private record Cache(Map<UUID,ResearchSnapshot> towns,Map<UUID,UUID> residents){Cache{towns=Map.copyOf(towns);residents=Map.copyOf(residents);}}
    private volatile Cache cache=new Cache(Map.of(),Map.of());
    public ResearchService(Plugin plugin,ResearchRepository repository,ResearchSettings settings){this.plugin=plugin;this.repository=repository;this.settings=settings;processor=new ResearchProcessor(repository,bridge);}
    public ResearchSettings settings(){return settings;}
    private void primary(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Нужен основной поток сервера");}
    public void start()throws Exception{primary();bridge.verify();refresh(0);schedule();}
    private void schedule(){task=Bukkit.getScheduler().runTaskTimer(plugin,()->refresh(settings.interval()),20L*settings.interval(),20L*settings.interval());}
    public void reload(ResearchSettings next){primary();settings=next;if(task!=null)task.cancel();refresh(0);schedule();}
    public void stop(){if(task!=null)task.cancel();cache=new Cache(Map.of(),Map.of());}
    private void warn(Exception ex){long now=System.currentTimeMillis();if(now-warned>=60000){warned=now;plugin.getLogger().log(java.util.logging.Level.WARNING,"Исследование приостановлено; журнал и знания сохранены",ex);}}
    public void refresh(int seconds){primary();Map<UUID,ResearchSnapshot> towns=new HashMap<>();Map<UUID,UUID> residents=new HashMap<>();
        try{for(Town town:List.copyOf(TownyAPI.getInstance().getTowns())){
            UUID id=town.getUUID();for(var resident:town.getResidents())residents.put(resident.getUUID(),id);
            Map<String,Integer> buildings=Map.of();long knowledge=0,reserve=0;boolean paused=false;String status="Нет текущего исследования";
            try{buildings=bridge.buildings(id);var stock=bridge.knowledge(id);knowledge=stock.balance();reserve=stock.reserve();paused=stock.paused();if(paused)status="Расчёт ресурсов приостановлен";}catch(Exception ex){paused=true;status="Недоступны здания или запас знаний";warn(ex);}
            var study=repository.get(id).active();boolean ready=!paused&&(study==null||(settings.technologies().containsKey(study.technology())&&settings.technologies().get(study.technology()).enabled()&&ResearchProcessor.ready(study.buildings(),buildings)));
            try{processor.tick(id,seconds,ready);var stock=bridge.knowledge(id);knowledge=stock.balance();reserve=stock.reserve();}catch(Exception ex){paused=true;status="Операция ожидает восстановления; проверьте консоль";warn(ex);}
            study=repository.get(id).active();if(study!=null&&!paused){status=switch(study.phase()){case PREPARED->ready?"Ожидает свободных знаний":"Ожидает научных зданий или включения технологии";case RUNNING->ready?"Исследование идёт":"Приостановлено: проверьте здания, питание и содержание";case COMPLETING->"Завершается сохранение технологии";case CANCELLING->"Возвращаются зарезервированные знания";};paused=!ready&&study.phase()!=CityStudy.Phase.CANCELLING;}
            towns.put(id,new ResearchSnapshot(id,town.getName(),repository.get(id),knowledge,reserve,paused,status,buildings));
        }
        // Deleted cities never start new work. Reconcile their existing reservations before retaining the archive.
        for(UUID id:repository.towns().keySet())if(!towns.containsKey(id)){var state=repository.get(id);try{if(state.active()!=null&&state.active().phase()!=CityStudy.Phase.COMPLETING&&state.active().phase()!=CityStudy.Phase.CANCELLING)processor.cancel(id);else processor.tick(id,0,true);}catch(Exception ex){warn(ex);}}
        cache=new Cache(towns,residents);
        }catch(Exception ex){cache=new Cache(Map.of(),Map.of());warn(ex);}
    }
    public Town town(Player player){var resident=TownyAPI.getInstance().getResident(player);return resident==null?null:resident.getTownOrNull();}
    public boolean manager(Player p,Town town){var resident=TownyAPI.getInstance().getResident(p);return resident!=null&&town.equals(resident.getTownOrNull())&&(town.isMayor(resident)||resident.hasTownRank("assistant")||p.hasPermission("neverlandtownyresearch.manage"));}
    public void begin(UUID town,String id)throws Exception{primary();var technology=settings.technologies().get(id);if(technology==null)throw new IllegalArgumentException("Неизвестная технология");if(TownyAPI.getInstance().getTown(town)==null)throw new IllegalArgumentException("Город не найден");try{processor.begin(town,technology,bridge.buildings(town));}finally{refresh(0);}}
    public void cancel(UUID town)throws Exception{primary();try{processor.cancel(town);}finally{refresh(0);}}
    @Override public String name(String id){var t=settings.technologies().get(id);return t==null?"Исследование":t.name();}
    @Override public int level(UUID town,String id){var snapshot=cache.towns().get(town);return snapshot==null?0:snapshot.state().learned().getOrDefault(id,0);}
    @Override public double bonus(UUID town,String id){var technology=settings.technologies().get(id);int level=level(town,id);return technology==null||!technology.enabled()||level<=0?0:technology.levels().get(Math.min(level,technology.levels().size())-1).bonus();}
    @Override public Optional<ResearchSnapshot> research(UUID town){return Optional.ofNullable(cache.towns().get(town));}
    @Override public Optional<ResearchSnapshot> residentResearch(UUID resident){var current=cache;UUID town=current.residents().get(resident);return Optional.ofNullable(town==null?null:current.towns().get(town));}
    @Override public Collection<ResearchSnapshot> towns(){return cache.towns().values();}
}
