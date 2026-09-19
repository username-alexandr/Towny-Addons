package ru.neverland.townyquests.service;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import ru.neverland.core.*;
import ru.neverland.townyquests.api.TownyQuestsApi;
import ru.neverland.townyquests.config.QuestSettings;
import ru.neverland.townyquests.data.QuestRepository;
import ru.neverland.townyquests.integration.CityBridge;
import ru.neverland.townyquests.model.*;

public final class QuestService implements TownyQuestsApi {
    private final Plugin plugin;
    private final QuestRepository repository;
    private final CityBridge bridge = new CityBridge();
    private QuestSettings settings;
    private BukkitTask task;
    private record Last(UUID run, int stage, long nano, long fraction) {}
    private final Map<UUID, Last> observations = new HashMap<>();
    private final Map<UUID, String> details = new HashMap<>();
    private final Map<UUID, Long> warnings = new HashMap<>();
    public QuestService(Plugin plugin, QuestRepository repository, QuestSettings settings) {
        this.plugin = plugin; this.repository = repository; this.settings = settings;
    }
    public QuestSettings settings() { return settings; }
    public Map<String, CityProject> state(UUID town) { return repository.get(town); }
    public String detail(UUID town) { return details.getOrDefault(town, "Проверка условий выполняется автоматически"); }
    public Town town(Player player) { var r = TownyAPI.getInstance().getResident(player); return r == null ? null : r.getTownOrNull(); }
    public boolean manager(Player player, Town town) {
        var r = TownyAPI.getInstance().getResident(player);
        return r != null && town.equals(r.getTownOrNull()) && (town.isMayor(r) || player.hasPermission("neverlandtownyquests.manage"));
    }
    public void start() { ApiServices.primaryThread(); schedule(); }
    private void schedule() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, settings.interval() * 20L); }
    public void stop() { if (task != null) task.cancel(); observations.clear(); details.clear(); }
    public void reload(QuestSettings next) { ApiServices.primaryThread(); stop(); settings = next; schedule(); }
    public void begin(Player player, String id) throws Exception {
        ApiServices.primaryThread(); var town = town(player);
        if (!player.hasPermission("neverlandtownyquests.use") || town == null || !manager(player, town))
            throw new IllegalArgumentException("Начать проект может мэр или уполномоченный своего города");
        UUID city = town.getUUID(); var existing = state(city).get(id);
        if (existing != null) { control(city, id, "resume"); return; }
        var definition = settings.projects().get(id);
        if (definition == null) throw new IllegalArgumentException("Проект не найден");
        for (String required : definition.requires()) {
            var completed = state(city).get(required);
            if (completed == null || completed.status() != CityProject.Status.COMPLETED)
                throw new IllegalArgumentException("Сначала завершите проект: " + settings.projects().get(required).name());
        }
        var run = CityProject.start(definition); repository.put(city, run); observations.remove(city);
        audit(city, run, "start", "Начат проект: " + definition.name());
    }
    public String control(UUID town, String id, String action) throws Exception {
        ApiServices.primaryThread(); var run = state(town).get(id);
        if (run == null || TownyAPI.getInstance().getTown(town) == null) throw new IllegalArgumentException("Город или проект больше не существует");
        if (action.equals("status")) return describe(run) + "; " + detail(town);
        var next = run.control(action); repository.put(town, next); observations.remove(town); details.remove(town);
        return describe(next) + "; завершённые этапы сохранены";
    }
    public void tick() {
        ApiServices.primaryThread(); long nano = System.nanoTime(), now = System.currentTimeMillis();
        for (var city : repository.towns().entrySet()) for (var run : city.getValue().values()) {
            UUID id = city.getKey();
            if (run.status() != CityProject.Status.ACTIVE) continue;
            var town = TownyAPI.getInstance().getTown(id);
            if (town == null) { observations.remove(id); details.put(id, "Город отсутствует в Towny"); continue; }
            var stage = run.definition().stages().get(run.stage()); var observation = bridge.observe(town, stage, settings.resourceMaxAge(), now);
            var last = observations.remove(id);
            long observed = last != null && last.run().equals(run.run()) && last.stage() == run.stage()
                    ? Math.max(0, Math.min(settings.interval() * 1_000_000_000L, nano - last.nano())) + last.fraction() : 0;
            int elapsed = (int) (observed / 1_000_000_000L);
            details.put(id, observation.detail());
            try {
                var next = ProjectEngine.advance(run, observation, elapsed); repository.put(id, next); warnings.remove(id);
                if (next.stage() != run.stage()) {
                    audit(id, next, "stage:" + run.stage(), run.definition().name() + ": завершён этап «" + stage.name() + "»"
                            + (next.status() == CityProject.Status.COMPLETED ? "; открыт результат " + next.definition().unlock() : ""));
                    if (next.status() == CityProject.Status.COMPLETED) announce(town, next);
                } else if (observation.ready() && observation.value() >= stage.target()) observations.put(id, new Last(run.run(), run.stage(), nano, observed % 1_000_000_000L));
            } catch (Exception ex) {
                details.put(id, "Пауза: не удалось сохранить прогресс");
                if (now - warnings.getOrDefault(id, 0L) >= 60000) {
                    warnings.put(id, now); plugin.getLogger().warning("Проекты города " + id + " приостановлены: " + ex.getMessage());
                }
            }
        }
    }
    private void audit(UUID town, CityProject run, String step, String message) {
        AuditTrail.record(plugin, step, run.run().toString(), "CITY_PROJECT", "COMPLETED", AuditRecord.Party.unknown(),
                AuditRecord.Party.unknown(), AuditTrail.town(town), "", 0, "", message);
    }
    private void announce(Town town, CityProject run) {
        for (var player : Bukkit.getOnlinePlayers()) if (town.equals(town(player)))
            player.sendMessage("§aГородской проект завершён: " + run.definition().name() + ". §f" + run.definition().reward());
    }
    public List<ActivityAdmin.Target> adminTargets() {
        ApiServices.primaryThread(); var result = new ArrayList<ActivityAdmin.Target>();
        repository.towns().forEach((town, runs) -> { var city = TownyAPI.getInstance().getTown(town); if (city == null) return;
            runs.forEach((id, run) -> { if (run.status() == CityProject.Status.COMPLETED) return;
                result.add(new ActivityAdmin.Target(town + ":" + id, city.getName() + " — " + describe(run),
                        Set.of("status", "pause", "resume", "restart", "cancel"), (action, minutes) -> control(town, id, action)));
            });
        }); return List.copyOf(result);
    }
    public static String status(CityProject.Status status) { return switch (status) {
        case ACTIVE -> "В работе"; case PAUSED -> "Приостановлен"; case CANCELLED -> "Отменён без провала"; case COMPLETED -> "Завершён";
    }; }
    public static String describe(CityProject run) { return run.definition().name() + " — " + status(run.status()) + "; этапы " + run.stage() + "/" + run.definition().stages().size(); }
    @Override public boolean unlocked(UUID town, String unlock) {
        ApiServices.primaryThread();
        return town != null && TownyAPI.getInstance().getTown(town) != null && state(town).values().stream()
                .anyMatch(r -> r.status() == CityProject.Status.COMPLETED && r.definition().unlock().equals(unlock));
    }
    @Override public List<Map<String, Object>> quests(UUID town) {
        ApiServices.primaryThread(); if (town == null || TownyAPI.getInstance().getTown(town) == null) return List.of();
        return state(town).values().stream().sorted(Comparator.comparing(r -> r.definition().id())).map(r -> Map.<String, Object>of(
                "id", r.definition().id(), "name", r.definition().name(), "run", r.run(), "status", r.status().name(),
                "completedStages", r.stage(), "totalStages", r.definition().stages().size(), "heldSeconds", r.heldSeconds(),
                "unlock", r.definition().unlock(), "reward", r.definition().reward())).toList();
    }
}
