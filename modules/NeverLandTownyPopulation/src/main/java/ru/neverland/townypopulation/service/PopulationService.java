package ru.neverland.townypopulation.service;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.core.*;
import ru.neverland.townypopulation.api.*;
import ru.neverland.townypopulation.config.PopulationSettings;
import ru.neverland.townypopulation.data.PopulationRepository;
import ru.neverland.townypopulation.integration.BuildsBridge;
import ru.neverland.townypopulation.model.*;
import java.util.*;
import java.util.concurrent.*;

public final class PopulationService implements TownyPopulationApi {
    private final JavaPlugin plugin;private final PopulationRepository repository;private final BuildsBridge builds=new BuildsBridge();private PopulationSettings settings;
    /** Publish immutable sources together with their city totals; async readers never query Towny. */
    private record Cache(Map<UUID,PopulationSnapshot> towns,Map<UUID,UUID> residents,Map<UUID,Map<String,Capacity>> sources,long at){}
    private volatile Cache cache=new Cache(Map.of(),Map.of(),Map.of(),0);private BukkitTask task;private volatile boolean running,startupReady;
    private final java.util.concurrent.atomic.AtomicLong lastWarning=new java.util.concurrent.atomic.AtomicLong();
    public PopulationService(JavaPlugin plugin,PopulationRepository repository,PopulationSettings settings){this.plugin=plugin;this.repository=repository;this.settings=settings;}
    public PopulationSettings settings(){try(var guard=SuiteState.guard()){return settings;}}
    public void start(){try(var guard=SuiteState.guard()){
        running=true;startupReady=false;long now=System.currentTimeMillis();var before=repository.state();var rebased=new HashMap<UUID,PopulationState>();before.forEach((id,state)->rebased.put(id,state.rebase(now)));
        repository.commit(before,rebased).whenComplete((saved,error)->{if(error==null)startupReady=true;else warn("Запуск расчёта населения ожидает исправления записи; подтверждённые жители сохранены: "+error.getMessage());queueRefresh(null);});
        refresh(false,null,false);task=SuiteScheduler.get().runTaskTimer(plugin,this::pulse,1,200);
    }}
    public void reload(PopulationSettings next){try(var guard=SuiteState.guard()){var previous=settings;try{settings=next;refresh(false,null,false);}catch(RuntimeException error){settings=previous;throw error;}}}
    private void pulse(){try{refresh(true,null,true);}catch(RuntimeException error){warn("Расчёт населения приостановлен без изменения подтверждённых жителей: "+error.getMessage());}}
    private List<Town> selected(Set<UUID> targets){if(targets==null)return List.copyOf(TownyAPI.getInstance().getTowns());var result=new ArrayList<Town>();for(UUID id:targets){var town=TownyAPI.getInstance().getTown(id);if(town!=null)result.add(town);}return List.copyOf(result);}
    private void refresh(boolean advance,Set<UUID> targets,boolean maintain){
        long now=System.currentTimeMillis();var confirmed=repository.state();boolean canChange=maintain&&startupReady&&repository.writable()&&!repository.busy();
        var towns=targets==null?new HashMap<UUID,PopulationSnapshot>():new HashMap<>(cache.towns());var residents=targets==null?new HashMap<UUID,UUID>():new HashMap<>(cache.residents());var allSources=targets==null?new HashMap<UUID,Map<String,Capacity>>():new HashMap<>(cache.sources());var proposed=targets==null?new HashMap<UUID,PopulationState>():new HashMap<>(confirmed);
        if(targets!=null){targets.forEach(id->{towns.remove(id);allSources.remove(id);proposed.remove(id);});residents.entrySet().removeIf(entry->targets.contains(entry.getValue()));}
        for(var town:selected(targets)){
            UUID id=town.getUUID();town.getResidents().forEach(resident->residents.put(resident.getUUID(),id));var saved=confirmed.get(id);var state=saved==null?new PopulationState(settings.initial(),0,0,now):saved;boolean paused=!startupReady||!repository.writable();Map<String,Integer> levels;Map<String,Capacity> sources;Capacity capacity;double medicine=0;
            try{
                levels=builds.levels(id,settings.buildings().keySet());Map<String,Integer> chosen=levels;sources=settings.contributions(key->chosen.getOrDefault(key,0),key->ru.neverland.integration.DistrictBonuses.multiplier(id,key));capacity=PopulationSettings.combined(sources);
                var supply=ru.neverland.townypopulation.integration.ResourcesBridge.supply(id);if(supply.isPresent()){paused|=supply.get().paused();if(!paused)capacity=supply.get().limit(capacity,state.population(),settings.rules());}
                capacity=capacity.plus(new Capacity(0,0,0,0,ru.neverland.integration.SpecializationAccess.bonus(id,"happiness")));capacity=capacity.plus(new Capacity(0,0,0,0,ru.neverland.integration.PoliciesAccess.effect(id,"happiness")));capacity=capacity.plus(new Capacity(0,0,0,0,AchievementBonuses.happiness(id)));capacity=capacity.plus(new Capacity(0,0,0,0,EnvironmentAccess.happiness(id)));medicine=levels.getOrDefault("infirmary",0)>0?ru.neverland.integration.ResearchBonuses.bonus(id,"medicine"):0;
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){paused=true;var previous=cache.towns().get(id);levels=previous==null?Map.of():previous.buildingLevels();capacity=previous==null?settings.base():previous.capacity();sources=cache.sources().getOrDefault(id,Map.of("@base",settings.base()));warn("Городские службы временно недоступны; население не убывает из-за ошибки интеграции: "+error.getMessage());}
            if(paused){var previous=cache.towns().get(id);if(previous!=null){capacity=previous.capacity();levels=previous.buildingLevels();sources=cache.sources().getOrDefault(id,sources);}}
            var candidate=state;if(canChange){if(paused||now<state.lastCycle())candidate=state.rebase(now);else if(advance&&saved!=null&&now-state.lastCycle()>=settings.intervalMillis())candidate=PopulationMath.advance(state,capacity,settings.rules(),now,medicine);proposed.put(id,candidate);}
            // A new town becomes visible only once its initial population is durably registered.
            if(saved==null)continue;long next=state.lastCycle()>Long.MAX_VALUE-settings.intervalMillis()?Long.MAX_VALUE:state.lastCycle()+settings.intervalMillis();towns.put(id,new PopulationSnapshot(id,town.getName(),state.population(),capacity,PopulationMath.evaluate(state.population(),capacity,settings.rules(),medicine),state.lastChange(),next,paused,levels));allSources.put(id,Map.copyOf(sources));
        }
        cache=new Cache(Map.copyOf(towns),Map.copyOf(residents),Map.copyOf(allSources),now);
        if(canChange&&!proposed.equals(confirmed))repository.commit(confirmed,proposed).whenComplete((saved,error)->{if(error!=null)warn("Изменение населения не подтверждено; прежние жители сохранены: "+error.getMessage());queueRefresh(targets);});
    }
    private void queueRefresh(Set<UUID> targets){if(!running||!plugin.isEnabled()||SuiteState.frozen(plugin.getName()))return;Set<UUID> captured=targets==null?null:Set.copyOf(targets);try{SuiteScheduler.get().runTask(plugin,()->{if(running)try{refresh(false,captured,false);}catch(RuntimeException error){warn("Показатели населения ожидают обновления: "+error.getMessage());}});}catch(RuntimeException ignored){/* Confirmed data is already durable; the next active pulse reconstructs the view. */}}
    public CompletionStage<Boolean> setPopulation(UUID townId,int amount){try(var guard=SuiteState.guard()){
        if(!running||!startupReady||SuiteState.frozen(plugin.getName())||amount<0||amount>settings.rules().maximum()||TownyAPI.getInstance().getTown(townId)==null)return CompletableFuture.completedFuture(false);
        var before=repository.state();var next=new HashMap<>(before);next.put(townId,new PopulationState(amount,0,0,System.currentTimeMillis()));return repository.commit(before,next).thenApply(saved->{queueRefresh(Set.of(townId));return true;});
    }}
    public void refreshView(){try(var guard=SuiteState.guard()){if(running)refresh(false,null,false);}}
    public void refreshTownView(UUID town){try(var guard=SuiteState.guard()){if(running&&town!=null)refresh(false,Set.of(town),false);}}
    /** Metadata event hint: new/deleted towns are registered without a catch-up growth cycle. */
    public void refreshTownRegistration(UUID town){try(var guard=SuiteState.guard()){if(running&&town!=null&&startupReady&&repository.writable()&&!repository.busy())refresh(false,Set.of(town),true);}}
    public CompletionStage<Void> prepareClose(){running=false;return repository.prepareClose();}
    public void stop(){try(var guard=SuiteState.guard()){if(task!=null)task.cancel();prepareClose();}}
    public void awaitClosed(){repository.awaitClosed();}
    private void warn(String message){long now=System.currentTimeMillis(),before=lastWarning.get();if(now-before>=60_000&&lastWarning.compareAndSet(before,now))plugin.getLogger().warning(message);}
    @Override public boolean healthy(){return repository.readable();}
    @Override public Optional<PopulationSnapshot> population(UUID id){return Optional.ofNullable(id==null?null:cache.towns().get(id));}
    @Override public Optional<PopulationSnapshot> residentPopulation(UUID id){Cache current=cache;UUID town=id==null?null:current.residents().get(id);return Optional.ofNullable(town==null?null:current.towns().get(town));}
    @Override public Collection<PopulationSnapshot> populations(){return List.copyOf(cache.towns().values());}
}
