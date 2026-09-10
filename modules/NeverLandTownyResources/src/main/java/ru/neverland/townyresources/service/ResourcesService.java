package ru.neverland.townyresources.service;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townyresources.api.*;
import ru.neverland.townyresources.config.ResourcesSettings;
import ru.neverland.townyresources.data.ResourcesRepository;
import ru.neverland.townyresources.integration.CityBridge;
import ru.neverland.townyresources.model.*;
import java.util.*;
import java.io.IOException;

public final class ResourcesService implements TownyResourcesApi {
    private final JavaPlugin plugin;private final ResourcesRepository repository;private final CityBridge bridge=new CityBridge();
    private ResourcesSettings settings;private BukkitTask task;private int remainingTicks;private boolean fault;
    private record Cache(Map<UUID,ResourceSnapshot> towns,Map<UUID,UUID> residents) {}
    private volatile Cache cache=new Cache(Map.of(),Map.of());
    public ResourcesService(JavaPlugin plugin,ResourcesRepository repository,ResourcesSettings settings){this.plugin=plugin;this.repository=repository;this.settings=settings;remainingTicks=settings.interval()*20;}
    public ResourcesSettings settings(){return settings;}
    public void start()throws IOException{refresh(false);task=Bukkit.getScheduler().runTaskTimer(plugin,this::pulse,100,100);}
    public void stop(){if(task!=null)task.cancel();}
    public void reload(ResourcesSettings next)throws IOException{
        var previous=settings;int oldTicks=remainingTicks;boolean previousFault=fault;
        try{settings=next;remainingTicks=next.interval()*20;fault=false;refresh(false);}catch(Exception ex){settings=previous;remainingTicks=oldTicks;fault=previousFault;throw ex;}
    }
    private void pulse(){
        if(fault)return;remainingTicks-=100;boolean due=remainingTicks<=0;
        try{if(due)remainingTicks=settings.interval()*20;refresh(due);}
        catch(Exception ex){fault=true;publishFault();plugin.getLogger().log(java.util.logging.Level.SEVERE,"Расчёт ресурсов приостановлен; сохранённые запасы не изменены",ex);}
    }
    private void refresh(boolean advance)throws IOException{
        long now=System.currentTimeMillis();Map<UUID,TownState> states=new HashMap<>(repository.states());Map<UUID,ResourceSnapshot> snapshots=new HashMap<>();Map<UUID,UUID> residents=new HashMap<>();Set<UUID> existing=new HashSet<>();
        for(Town town:List.copyOf(TownyAPI.getInstance().getTowns())){
            UUID id=town.getUUID();existing.add(id);town.getResidents().forEach(r->residents.put(r.getUUID(),id));
            TownState state=states.getOrDefault(id,TownState.initial(settings.initial()));states.put(id,state);
            try{
                var built=bridge.buildings(id);int people=settings.populationLinked()?bridge.population(town):0;
                var preview=ResourceEngine.calculate(state,built,people,settings,now);
                if(advance){state=preview.state();states.put(id,state);preview=ResourceEngine.calculate(state,built,people,settings,now);}
                snapshots.put(id,new ResourceSnapshot(id,town.getName(),people,settings.populationLinked(),false,"Расчёт работает",now+remainingTicks*50L,state,preview.capacity(),preview.state().income(),preview.state().expense(),preview.demand(),preview.activity(),
                    Math.min(state.foodCoverage(),preview.state().foodCoverage()),Math.min(state.waterCoverage(),preview.state().waterCoverage())));
            }catch(ReflectiveOperationException|RuntimeException|LinkageError ex){
                var old=cache.towns().get(id);snapshots.put(id,new ResourceSnapshot(id,town.getName(),old==null?0:old.population(),settings.populationLinked(),true,"Ожидает данные: "+ex.getMessage(),now+remainingTicks*50L,state,old==null?settings.baseCapacity():old.capacity(),Map.of(),Map.of(),Map.of(),Map.of(),state.foodCoverage(),state.waterCoverage()));
            }
        }
        states.keySet().retainAll(existing);repository.replace(states);cache=new Cache(Map.copyOf(snapshots),Map.copyOf(residents));
    }
    private void publishFault(){var next=new HashMap<UUID,ResourceSnapshot>();for(var s:cache.towns().values())next.put(s.townId(),new ResourceSnapshot(s.townId(),s.townName(),s.population(),s.populationLinked(),true,"Ошибка сохранения — обратитесь к администратору",s.nextCycle(),s.state(),s.capacity(),Map.of(),Map.of(),s.populationDemand(),s.buildings(),s.foodCoverage(),s.waterCoverage()));cache=new Cache(Map.copyOf(next),cache.residents());}
    public Town town(Player p){var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();}
    public boolean manager(Player p,Town town){var r=TownyAPI.getInstance().getResident(p);return r!=null&&town.equals(r.getTownOrNull())&&(town.isMayor(r)||r.hasTownRank("assistant")||p.hasPermission("neverlandtownyresources.manage"));}
    private TownState state(UUID id){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Нужен основной поток сервера");if(fault)throw new IllegalStateException("Сначала устраните ошибку записи и выполните reload");if(TownyAPI.getInstance().getTown(id)==null)throw new IllegalArgumentException("Город не найден");var s=repository.states().get(id);if(s==null)throw new IllegalStateException("Ожидается первый расчёт города");return s;}
    private void change(UUID id,TownState state)throws IOException{
        var next=new HashMap<>(repository.states());next.put(id,state);repository.replace(next);
        // The edit is already durable. A later view refresh must not report it as a failed edit.
        var s=cache.towns().get(id);if(s!=null){var views=new HashMap<>(cache.towns());views.put(id,new ResourceSnapshot(s.townId(),s.townName(),s.population(),s.populationLinked(),s.paused(),s.status(),s.nextCycle(),state,s.capacity(),s.forecastIncome(),s.forecastExpense(),s.populationDemand(),s.buildings(),s.foodCoverage(),s.waterCoverage()));cache=new Cache(Map.copyOf(views),cache.residents());}
        try{refresh(false);}catch(IOException|RuntimeException ex){fault=true;publishFault();plugin.getLogger().log(java.util.logging.Level.SEVERE,"Изменение ресурсов сохранено, но обновление расчёта приостановлено",ex);}
    }
    public void pause(UUID id,String project,boolean paused)throws IOException{profile(project);var s=state(id);var stop=new HashSet<>(s.paused());if(paused)stop.add(project);else stop.remove(project);change(id,s.settings(s.reserves(),stop,s.priorities()));}
    public void priority(UUID id,String project,int priority)throws IOException{profile(project);if(priority<0||priority>100)throw new IllegalArgumentException("Приоритет: 0..100; меньшее число выполняется раньше");var s=state(id);var order=new HashMap<>(s.priorities());order.put(project,priority);change(id,s.settings(s.reserves(),s.paused(),order));}
    public void reserve(UUID id,Resource r,long amount)throws IOException{var s=state(id);var keep=Amounts.mutable(s.reserves());keep.put(r,Amounts.valid(amount));change(id,s.settings(keep,s.paused(),s.priorities()));}
    public void adjust(UUID id,Resource r,long amount,String action)throws IOException{
        var s=state(id);Amounts.valid(amount);long old=s.balances().get(r);long value=switch(action){case "set"->amount;case "add"->Math.addExact(old,amount);case "take"->old-amount;default->throw new IllegalArgumentException("set, add или take");};
        Amounts.valid(value);var snapshot=resources(id).orElseThrow();if(value>old&&value>snapshot.capacity().get(r))throw new IllegalArgumentException("Не хватает вместимости");change(id,s.balance(r,value));
    }
    public BuildingProfile profile(String id){var p=settings.buildings().get(id);if(p==null)throw new IllegalArgumentException("Неизвестное здание: "+id);return p;}
    @Override public Optional<ResourceSnapshot> resources(UUID id){return Optional.ofNullable(id==null?null:cache.towns().get(id));}
    @Override public Optional<ResourceSnapshot> residentResources(UUID id){var current=cache;var town=id==null?null:current.residents().get(id);return Optional.ofNullable(town==null?null:current.towns().get(town));}
    @Override public Collection<ResourceSnapshot> towns(){return List.copyOf(cache.towns().values());}
}
