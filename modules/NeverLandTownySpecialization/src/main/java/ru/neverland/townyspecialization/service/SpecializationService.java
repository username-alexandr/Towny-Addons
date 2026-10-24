package ru.neverland.townyspecialization.service;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townyspecialization.api.*;
import ru.neverland.townyspecialization.config.SpecializationSettings;
import ru.neverland.townyspecialization.data.SpecializationRepository;
import ru.neverland.townyspecialization.integration.CityBridge;
import ru.neverland.townyspecialization.model.*;
import ru.neverland.integration.*;
import java.util.*;
public final class SpecializationService implements TownySpecializationApi {
    private final Plugin plugin;private final SpecializationRepository repository;private final CityBridge bridge=new CityBridge();
    private volatile SpecializationSettings settings;private BukkitTask task;private long warned;
    private record Cache(Map<UUID,SpecializationSnapshot> towns,Map<UUID,UUID> residents){Cache{towns=Map.copyOf(towns);residents=Map.copyOf(residents);}}
    private volatile Cache cache=new Cache(Map.of(),Map.of());private volatile Set<UUID> liveTowns=Set.of();
    public record Offer(UUID town,String specialization,long revision,SpecializationSettings settings,long expires,boolean admin){}
    private final Map<UUID,Offer> offers=new HashMap<>();
    public SpecializationService(Plugin plugin,SpecializationRepository repository,SpecializationSettings settings){this.plugin=plugin;this.repository=repository;this.settings=settings;}
    public SpecializationSettings settings(){ synchronized (ru.neverland.core.SuiteState.lock()) {return settings;} }
    private void primary(){if(!ru.neverland.core.SuiteState.held())throw new IllegalStateException("Нужен основной поток сервера");}
    public void start(){ synchronized (ru.neverland.core.SuiteState.lock()) {primary();refresh();schedule();} }
    private void schedule(){task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,this::refresh,20L*settings.interval(),20L*settings.interval());}
    public void reload(SpecializationSettings next){ synchronized (ru.neverland.core.SuiteState.lock()) {primary();cache=new Cache(Map.of(),Map.of());settings=next;offers.clear();if(task!=null)task.cancel();refresh();schedule();} }
    public void stop(){ synchronized (ru.neverland.core.SuiteState.lock()) {if(task!=null)task.cancel();cache=new Cache(Map.of(),Map.of());liveTowns=Set.of();offers.clear();} }
    private void warn(Exception ex){long now=System.currentTimeMillis();if(now-warned>=60000){warned=now;plugin.getLogger().log(java.util.logging.Level.WARNING,"Бонусы специализации приостановлены; выбор города сохранён",ex);}}
    public void refresh(){ synchronized (ru.neverland.core.SuiteState.lock()) {primary();Map<UUID,SpecializationSnapshot> towns=new HashMap<>();Map<UUID,UUID> residents=new HashMap<>();
        try{var current=List.copyOf(TownyAPI.getInstance().getTowns());Set<UUID> existing=new HashSet<>();current.forEach(t->existing.add(t.getUUID()));liveTowns=Set.copyOf(existing);
            for(Town town:current){UUID id=town.getUUID();town.getResidents().forEach(r->residents.put(r.getUUID(),id));var state=repository.get(id);int level=0,hall=0,unique=0;boolean paused=false;String status=state.specialization().isEmpty()?"Направление ещё не выбрано":"Специализация действует";Map<String,Double> bonuses=new HashMap<>();
                try{level=town.getLevelNumber();var built=bridge.levels(id);hall=built.getOrDefault("town_hall",0);var profile=settings.profiles().get(state.specialization());if(profile!=null){if(!profile.enabled()){paused=true;status="Направление отключено в настройках";}else{int completed=built.getOrDefault(profile.building(),0);unique=completed>=profile.minimumBuildingLevel()&&BuildingOperations.active(id,profile.building())?completed:0;for(String effect:profile.bonuses().keySet())bonuses.put(effect,profile.bonus(effect,unique));if(unique==0)status="Базовые бонусы действуют; уникальное здание ещё не работает";}}}
                catch(Exception ex){paused=true;status="Ожидается расчёт зданий";bonuses.clear();unique=0;warn(ex);}towns.put(id,new SpecializationSnapshot(id,town.getName(),level,hall,state,unique,bonuses,paused,status));
            }
            cache=new Cache(towns,residents);long now=System.currentTimeMillis();offers.values().removeIf(o->o.expires()<now||!liveTowns.contains(o.town()));
        }catch(Exception ex){cache=new Cache(Map.of(),Map.of());liveTowns=Set.of();warn(ex);}
    } }
    public Town town(Player p){ synchronized (ru.neverland.core.SuiteState.lock()) {var resident=TownyAPI.getInstance().getResident(p);return resident==null?null:resident.getTownOrNull();} }
    public boolean manager(Player p,Town town){ synchronized (ru.neverland.core.SuiteState.lock()) {var resident=TownyAPI.getInstance().getResident(p);return resident!=null&&town.equals(resident.getTownOrNull())&&(town.isMayor(resident)||ru.neverland.core.CouncilAccess.check(p,town.getUUID(),"specialization",resident.hasTownRank("assistant")||p.hasPermission("neverlandtownyspecialization.manage")));} }
    private Town authorized(Player p,UUID id,boolean admin){Town town=TownyAPI.getInstance().getTown(id);if(town==null)throw new IllegalArgumentException("Город не найден");if(admin){if(!p.hasPermission("neverlandtownyspecialization.admin"))throw new IllegalArgumentException("Недостаточно прав администратора");}else if(!p.hasPermission("neverlandtownyspecialization.use")||!manager(p,town))throw new IllegalArgumentException("Нужны права мэра, помощника или управляющего своего города");return town;}
    private CityChoice proposed(Player p,UUID id,String target,long revision,SpecializationSettings quoted,boolean admin)throws Exception{primary();Town town=authorized(p,id,admin);if(settings!=quoted)throw new IllegalArgumentException("Настройки изменились. Откройте меню заново.");var profile=settings.profiles().get(target);if(profile==null)throw new IllegalArgumentException("Неизвестная специализация");var built=bridge.levels(id);return SelectionPolicy.choose(repository.get(id),profile,town.getLevelNumber(),built.getOrDefault("town_hall",0),settings,System.currentTimeMillis(),revision,admin);}
    public void choose(Player p,UUID id,String target,long revision,SpecializationSettings quoted,boolean admin)throws Exception{ synchronized (ru.neverland.core.SuiteState.lock()) {var next=proposed(p,id,target,revision,quoted,admin);repository.put(id,next);offers.remove(p.getUniqueId());refresh();} }
    public void offer(Player p,UUID id,String target,boolean admin)throws Exception{ synchronized (ru.neverland.core.SuiteState.lock()) {primary();long revision=repository.get(id).revision();proposed(p,id,target,revision,settings,admin);offers.put(p.getUniqueId(),new Offer(id,target,revision,settings,System.currentTimeMillis()+60000,admin));} }
    public Offer offer(Player p){ synchronized (ru.neverland.core.SuiteState.lock()) {primary();var o=offers.get(p.getUniqueId());if(o==null||o.expires()<System.currentTimeMillis()){offers.remove(p.getUniqueId());return null;}return o;} }
    public UUID confirm(Player p)throws Exception{ synchronized (ru.neverland.core.SuiteState.lock()) {primary();var o=offer(p);if(o==null)throw new IllegalArgumentException("Нет действующего выбора. Используйте choose ещё раз.");offers.remove(p.getUniqueId());choose(p,o.town(),o.specialization(),o.revision(),o.settings(),o.admin());return o.town();} }
    @Override public boolean canUseBuilding(UUID town,String project){ synchronized (ru.neverland.core.SuiteState.lock()) {String required=SpecializationRules.required(project);if(required.isEmpty())return true;if(town==null||!liveTowns.contains(town))return false;var profile=settings.profiles().get(required);return profile!=null&&profile.enabled()&&repository.get(town).specialization().equals(required);} }
    @Override public double bonus(UUID town,String effect){ synchronized (ru.neverland.core.SuiteState.lock()) {if(town==null)return 0;var view=cache.towns().get(town);return view==null||view.paused()||view.state().revision()!=repository.get(town).revision()?0:view.bonuses().getOrDefault(effect,0d);} }
    @Override public double productionBonus(UUID town,String project){ synchronized (ru.neverland.core.SuiteState.lock()) {if(town==null||project==null)return 0;var profile=settings.profiles().get(repository.get(town).specialization());return profile!=null&&profile.productionProjects().contains(project)?bonus(town,"production"):0;} }
    @Override public Optional<SpecializationSnapshot> specialization(UUID town){ synchronized (ru.neverland.core.SuiteState.lock()) {return Optional.ofNullable(town==null?null:cache.towns().get(town));} }
    @Override public Optional<SpecializationSnapshot> residentSpecialization(UUID resident){ synchronized (ru.neverland.core.SuiteState.lock()) {var current=cache;var town=resident==null?null:current.residents().get(resident);return Optional.ofNullable(town==null?null:current.towns().get(town));} }
    @Override public Collection<SpecializationSnapshot> towns(){ synchronized (ru.neverland.core.SuiteState.lock()) {return cache.towns().values();} }
}
