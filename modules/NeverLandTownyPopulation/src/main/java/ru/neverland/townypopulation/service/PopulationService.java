package ru.neverland.townypopulation.service;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townypopulation.api.*;
import ru.neverland.townypopulation.config.PopulationSettings;
import ru.neverland.townypopulation.data.PopulationRepository;
import ru.neverland.townypopulation.integration.BuildsBridge;
import ru.neverland.townypopulation.model.*;
import java.util.*;

public final class PopulationService implements TownyPopulationApi {
    private final JavaPlugin plugin;
    private final PopulationRepository repository;
    private final BuildsBridge builds = new BuildsBridge();
    private PopulationSettings settings;
    // Publish both maps together; async callers never touch Towny or mutable simulation data.
    private record Cache(Map<UUID,PopulationSnapshot> towns, Map<UUID,UUID> residents) {}
    private volatile Cache cache = new Cache(Map.of(),Map.of());
    private BukkitTask task;
    private long lastWarning;
    public PopulationService(JavaPlugin plugin, PopulationRepository repository, PopulationSettings settings) {
        this.plugin=plugin; this.repository=repository; this.settings=settings;
    }
    public PopulationSettings settings() { return settings; }
    public void start() {
        repository.rebase(System.currentTimeMillis());
        pulse();
        task = Bukkit.getScheduler().runTaskTimer(plugin,this::pulse,200,200);
    }
    public void reload(PopulationSettings settings) {
        PopulationSettings previous=this.settings;
        try { this.settings=settings; refresh(false); }
        catch(RuntimeException ex) { this.settings=previous; throw ex; }
    }
    private void pulse() {
        try { refresh(true); save(); }
        catch (RuntimeException ex) { warn("Ошибка расчёта населения: "+ex.getMessage()); }
    }
    private void refresh(boolean advance) {
        long now = System.currentTimeMillis();
        Map<UUID, PopulationSnapshot> towns = new HashMap<>();
        Map<UUID, UUID> residents = new HashMap<>();
        Set<UUID> existing = new HashSet<>();
        for (Town town : List.copyOf(TownyAPI.getInstance().getTowns())) {
            UUID id = town.getUUID(); existing.add(id);
            town.getResidents().forEach(resident -> residents.put(resident.getUUID(),id));
            var state = repository.get(id);
            if (state == null) state = new PopulationState(settings.initial(),0,0,now);
            boolean paused = false;
            Map<String,Integer> levels;
            try { levels = builds.levels(id,settings.buildings().keySet()); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                paused = true; state = state.rebase(now);
                var previous = cache.towns().get(id);
                levels = previous == null ? Map.of() : previous.buildingLevels();
                warn("Расчёт населения приостановлен: "+ex.getMessage());
            }
            Map<String,Integer> selected = levels;
            Capacity capacity = settings.capacity(key -> selected.getOrDefault(key,0), key -> ru.neverland.integration.DistrictBonuses.multiplier(id,key));
            try {
                var supply=ru.neverland.townypopulation.integration.ResourcesBridge.supply(id);
                if(supply.isPresent()) {
                    paused |= supply.get().paused();
                    if(!paused) capacity=supply.get().limit(capacity,state.population(),settings.rules());
                }
            } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {
                paused=true;warn("Стратегическое снабжение недоступно: "+ex.getMessage());
            }
            if (paused || now < state.lastCycle()) state = state.rebase(now);
            if (!paused && advance && now-state.lastCycle() >= settings.intervalMillis())
                state = PopulationMath.advance(state,capacity,settings.rules(),now);
            repository.put(id,state);
            towns.put(id,new PopulationSnapshot(id,town.getName(),state.population(),capacity,
                    PopulationMath.evaluate(state.population(),capacity,settings.rules()),state.lastChange(),
                    state.lastCycle()+settings.intervalMillis(),paused,levels));
        }
        repository.retain(existing);
        cache = new Cache(Map.copyOf(towns),Map.copyOf(residents));
    }
    public boolean setPopulation(UUID townId, int amount) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Изменение населения требует основного потока");
        if (amount < 0 || amount > settings.rules().maximum() || TownyAPI.getInstance().getTown(townId) == null) return false;
        repository.put(townId,new PopulationState(amount,0,0,System.currentTimeMillis()));
        refresh(false); save(); return true;
    }
    public void refreshView() { refresh(false); }
    public void stop() { if (task != null) task.cancel(); save(); }
    private void save() {
        try { repository.save(); }
        catch (java.io.IOException ex) { warn("Не удалось сохранить population-data.yml: "+ex.getMessage()); }
    }
    private void warn(String message) {
        long now = System.currentTimeMillis();
        if (now-lastWarning >= 60000) { lastWarning=now; plugin.getLogger().warning(message); }
    }
    @Override public Optional<PopulationSnapshot> population(UUID id) { return Optional.ofNullable(id == null ? null : cache.towns().get(id)); }
    @Override public Optional<PopulationSnapshot> residentPopulation(UUID id) {
        Cache current = cache; UUID town = id == null ? null : current.residents().get(id);
        return Optional.ofNullable(town == null ? null : current.towns().get(town));
    }
    @Override public Collection<PopulationSnapshot> populations() { return List.copyOf(cache.towns().values()); }
}
