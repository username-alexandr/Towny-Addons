package ru.neverland.townyenvironment.service;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.*;
import ru.neverland.townyenvironment.api.TownyEnvironmentApi;
import ru.neverland.townyenvironment.config.EnvironmentSettings;
import ru.neverland.townyenvironment.data.EnvironmentRepository;
import ru.neverland.townyenvironment.integration.EnvironmentMetrics;
import ru.neverland.townyenvironment.model.*;

public final class EnvironmentService implements TownyEnvironmentApi {
    public record Observation(boolean ready, String status, Map<String, Integer> levels, EnvironmentEngine.Pressure pressure) {
        public Observation { levels = Map.copyOf(levels); }
    }
    private final Plugin plugin; private final EnvironmentRepository repository; private final EnvironmentMetrics metrics;
    private EnvironmentSettings settings; private BukkitTask task; private boolean storageFault;
    private final Map<UUID, Observation> observations = new HashMap<>();
    private final Map<UUID, Integer> elapsed = new HashMap<>(); private long lastWarning;
    public EnvironmentService(Plugin plugin, EnvironmentRepository repository, EnvironmentSettings settings) { this(plugin, repository, settings, new EnvironmentMetrics()); }
    public EnvironmentService(Plugin plugin, EnvironmentRepository repository, EnvironmentSettings settings, EnvironmentMetrics metrics) {
        this.plugin = plugin; this.repository = repository; this.settings = settings; this.metrics = metrics;
    }
    public EnvironmentSettings settings() { return settings; }
    public EnvironmentState state(UUID town) { return repository.get(town); }
    public Observation observation(UUID town) {
        return observations.getOrDefault(town, new Observation(false, "Ожидаются данные зданий", Map.of(), new EnvironmentEngine.Pressure(0, 0, Map.of())));
    }
    public Town town(Player player) { var resident = TownyAPI.getInstance().getResident(player); return resident == null ? null : resident.getTownOrNull(); }
    public Town requireTown(Player player) {
        Town town = town(player); if (town == null || !player.hasPermission("neverlandtownyenvironment.use")) throw new IllegalArgumentException("Нужен свой город и доступ к экологии"); return town;
    }
    public void start() { refresh(false); task = Bukkit.getScheduler().runTaskTimer(plugin, () -> refresh(true), 100, 100); }
    public void stop() { if (task != null) task.cancel(); elapsed.clear(); observations.clear(); }
    public void reload(EnvironmentSettings next) {
        ApiServices.primaryThread(); if (storageFault) throw new IllegalStateException("Ошибка хранилища: восстановите данные и перезапустите модуль");
        stop(); settings = next; start();
    }
    public void refreshView() { refresh(false); }
    private void refresh(boolean advance) {
        ApiServices.primaryThread(); if (storageFault) return;
        var next = new HashMap<>(repository.towns()); Set<UUID> existing = new HashSet<>();
        for (Town town : List.copyOf(TownyAPI.getInstance().getTowns())) {
            UUID id = town.getUUID(); existing.add(id); EnvironmentState state = state(id);
            if (state.paused()) { suspend(id, "Пауза администратора; штрафы отключены"); continue; }
            try {
                var levels = metrics.active(id, settings); var pressure = EnvironmentEngine.pressure(levels, settings);
                boolean wasReady = observation(id).ready();
                observations.put(id, new Observation(true, "Расчёт работает", levels, pressure));
                if (advance && wasReady) {
                    int seconds = elapsed.getOrDefault(id, 0) + 5;
                    if (seconds >= settings.interval()) { next.put(id, EnvironmentEngine.advance(state, pressure)); seconds = 0; }
                    elapsed.put(id, seconds);
                }
            } catch (Exception | LinkageError ex) { suspend(id, "Расчёт приостановлен: " + ex.getMessage()); warn(ex); }
        }
        observations.keySet().retainAll(existing); elapsed.keySet().retainAll(existing);
        // Keep deleted cities' historical data isolated by UUID; never transfer pollution to a new city with the same name.
        try {
            var previous = repository.towns(); repository.replace(next);
            for (UUID id : existing) {
                var before = previous.getOrDefault(id, EnvironmentState.clean()); var after = state(id);
                if (band(before.pollution()) != band(after.pollution())) AuditTrail.record(plugin, "threshold", id + ":" + after.cycles(), "CITY_ENVIRONMENT", "COMPLETED",
                        AuditRecord.Party.unknown(), AuditTrail.town(id), AuditTrail.town(id), "pollution", 0, "", "Загрязнение: " + format(before.pollution()) + " → " + format(after.pollution()));
            }
        } catch (Exception ex) {
            storageFault = true; for (UUID id : existing) suspend(id, "Ошибка сохранения; штрафы отключены"); warn(ex);
        }
    }
    private int band(double pollution) { return pollution <= settings.threshold() ? 0 : pollution < 80 ? 1 : 2; }
    private void suspend(UUID town, String reason) {
        elapsed.remove(town); var old = observation(town); observations.put(town, new Observation(false, reason, old.levels(), old.pressure()));
    }
    private void warn(Throwable ex) { long now = System.currentTimeMillis(); if (now - lastWarning >= 60_000) { lastWarning = now; plugin.getLogger().warning("Экология: " + ex.getMessage()); } }
    private EnvironmentEngine.Effects effects(UUID town) {
        ApiServices.primaryThread(); if (town == null || TownyAPI.getInstance().getTown(town) == null) throw new IllegalArgumentException("Город не найден");
        return EnvironmentEngine.effects(state(town), settings, !storageFault && observation(town).ready());
    }
    @Override public double happiness(UUID town) { return effects(town).happiness(); }
    @Override public double agricultureMultiplier(UUID town, String building) { var effects = effects(town); return settings.agriculture().contains(building) ? effects.agriculture() : 1; }
    @Override public Map<String, Object> environment(UUID town) {
        var effects = effects(town); var state = state(town); var view = observation(town);
        return Map.ofEntries(Map.entry("pollution", state.pollution()), Map.entry("cycles", state.cycles()), Map.entry("paused", state.paused()),
                Map.entry("ready", view.ready() && !storageFault), Map.entry("status", view.status()), Map.entry("emissions", view.pressure().emissions()),
                Map.entry("cleaning", view.pressure().cleaning()), Map.entry("happiness", effects.happiness()), Map.entry("agriculture", effects.agriculture()),
                Map.entry("levels", view.levels()), Map.entry("secondsUntilCycle", settings.interval() - elapsed.getOrDefault(town, 0)));
    }
    public List<ActivityAdmin.Target> adminTargets() {
        ApiServices.primaryThread(); var list = new ArrayList<ActivityAdmin.Target>();
        for (Town town : TownyAPI.getInstance().getTowns()) {
            var state = state(town.getUUID()); list.add(new ActivityAdmin.Target(town.getUUID().toString(), town.getName() + " — экология " + format(state.pollution()) + "/100" + (state.paused() ? " (пауза)" : ""),
                    state.paused() ? Set.of("status", "resume") : Set.of("status", "pause", "restart"), (action, minutes) -> control(town.getUUID(), action)));
        }
        return List.copyOf(list);
    }
    public String control(UUID town, String action) throws Exception {
        ApiServices.primaryThread(); if (TownyAPI.getInstance().getTown(town) == null) throw new IllegalArgumentException("Город удалён");
        switch (action) {
            case "pause" -> { repository.put(town, state(town).paused(true)); suspend(town, "Пауза администратора; штрафы отключены"); }
            case "resume" -> { repository.put(town, state(town).paused(false)); elapsed.remove(town); refreshView(); }
            case "restart" -> { elapsed.remove(town); refreshView(); }
            case "status" -> { }
            default -> throw new IllegalArgumentException("Доступны status, pause, resume, restart");
        }
        return "Загрязнение " + format(state(town).pollution()) + "/100; " + observation(town).status() + "; сохранённые данные не сброшены";
    }
    public static String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
