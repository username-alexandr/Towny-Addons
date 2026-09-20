package ru.neverland.townyachievements.service;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import ru.neverland.core.*;
import ru.neverland.townyachievements.api.TownyAchievementsApi;
import ru.neverland.townyachievements.config.AchievementSettings;
import ru.neverland.townyachievements.data.AchievementRepository;
import ru.neverland.townyachievements.integration.CityMetrics;
import ru.neverland.townyachievements.model.*;

public final class AchievementService implements TownyAchievementsApi {
    private final Plugin plugin; private final AchievementRepository repository; private final CityMetrics metrics;
    private AchievementSettings settings; private BukkitTask task, cosmeticTask;
    private final Map<UUID, Map<String, String>> details = new HashMap<>();
    private final Map<UUID, Long> warnings = new HashMap<>();
    private final Set<String> audited = new HashSet<>();
    private volatile Map<UUID, Map<String, String>> placeholders = Map.of();
    public AchievementService(Plugin plugin, AchievementRepository repository, AchievementSettings settings) { this(plugin, repository, settings, new CityMetrics()); }
    public AchievementService(Plugin plugin, AchievementRepository repository, AchievementSettings settings, CityMetrics metrics) {
        this.plugin = plugin; this.repository = repository; this.settings = settings; this.metrics = metrics;
    }
    public AchievementSettings settings() { return settings; }
    public CityAchievements state(UUID town) { return repository.get(town); }
    public Town town(Player player) { var r = TownyAPI.getInstance().getResident(player); return r == null ? null : r.getTownOrNull(); }
    public Town requireTown(Player player) {
        var city = town(player); if (city == null || !player.hasPermission("neverlandtownyachievements.use")) throw new IllegalArgumentException("Нужен свой город и доступ к достижениям"); return city;
    }
    public boolean manager(Player player, Town town) {
        var r = TownyAPI.getInstance().getResident(player);
        return r != null && town.equals(r.getTownOrNull()) && (town.isMayor(r) || player.hasPermission("neverlandtownyachievements.manage"));
    }
    public String detail(UUID town, String id) { return state(town).paused() ? "Проверки приостановлены администратором" : details.getOrDefault(town, Map.of()).getOrDefault(id, "Ожидается автоматическая проверка"); }
    public Map<String, Progress> catalogue(UUID town) {
        var result = new LinkedHashMap<String, Progress>(); settings.definitions().forEach((id, a) -> result.put(id, Progress.begin(a)));
        result.putAll(state(town).progress()); return Collections.unmodifiableMap(result);
    }
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, settings.interval() * 20L);
        cosmeticTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cosmetics, 20, 20);
    }
    public void stop() { if (task != null) task.cancel(); if (cosmeticTask != null) cosmeticTask.cancel(); placeholders = Map.of(); }
    public void reload(AchievementSettings next) { ApiServices.primaryThread(); stop(); settings = next; start(); }
    public void tick() {
        ApiServices.primaryThread();
        for (Town town : List.copyOf(TownyAPI.getInstance().getTowns())) {
            try { check(town); warnings.remove(town.getUUID()); }
            catch (Exception ex) {
                long now = System.currentTimeMillis();
                if (now - warnings.getOrDefault(town.getUUID(), 0L) >= 60_000) {
                    warnings.put(town.getUUID(), now); plugin.getLogger().warning("Достижения " + town.getUUID() + ": сохранение приостановлено — " + ex.getMessage());
                }
            }
        }
        publishPlaceholders();
    }
    public void check(Town town) throws Exception {
        ApiServices.primaryThread(); UUID id = town.getUUID(); var old = state(id); var next = old;
        Map<String, String> observed = new HashMap<>();
        if (!old.paused()) for (var entry : catalogue(id).entrySet()) {
            var progress = entry.getValue();
            if (!progress.earned()) {
                var observation = metrics.observe(town, progress.definition()); observed.put(entry.getKey(), observation.detail());
                if (observation.ready()) progress = progress.observe(observation.value(), System.currentTimeMillis());
            }
            next = next.put(progress);
        }
        repository.put(id, next); details.put(id, Map.copyOf(observed));
        for (var p : next.progress().values()) if (p.earned()) {
            String operation = id + ":" + p.definition().id();
            if (!audited.contains(operation) && AuditTrail.record(plugin, "unlock", operation, "CITY_ACHIEVEMENT", "COMPLETED",
                    AuditRecord.Party.unknown(), AuditRecord.Party.unknown(), AuditTrail.town(id), p.definition().id(), 1, "", p.definition().name() + "; открыто " + p.earnedAt())) audited.add(operation);
            if (!old.progress().containsKey(p.definition().id()) || !old.progress().get(p.definition().id()).earned())
                for (Player player : Bukkit.getOnlinePlayers()) if (town.equals(town(player))) player.sendMessage("§6Достижение города: §f" + p.definition().name() + "§6! Награды: §e/t achievements");
        }
    }
    public Progress reward(Player player, String id) {
        ApiServices.primaryThread(); var city = requireTown(player); var p = state(city.getUUID()).progress().get(id);
        if (p == null || !p.earned()) throw new IllegalArgumentException("Город ещё не открыл эту награду"); return p;
    }
    public void selectTitle(Player player, String id) throws Exception {
        ApiServices.primaryThread(); var city = requireTown(player);
        if (!manager(player, city)) throw new IllegalArgumentException("Титул города выбирает мэр или уполномоченный");
        if (!id.equals("none")) reward(player, id);
        repository.put(city.getUUID(), state(city.getUUID()).title(id.equals("none") ? "" : id)); publishPlaceholders();
    }
    public void selectCosmetic(Player player, String id) throws Exception {
        ApiServices.primaryThread(); var city = requireTown(player);
        if (id.equals("none")) { repository.cosmetic(player.getUniqueId(), null); return; }
        if (reward(player, id).definition().reward().cosmetic().isEmpty()) throw new IllegalArgumentException("У достижения нет эффекта частиц");
        repository.cosmetic(player.getUniqueId(), new AchievementRepository.Cosmetic(city.getUUID(), id));
    }
    private void cosmetics() {
        if (!settings.cosmetics()) return;
        for (var player : Bukkit.getOnlinePlayers()) {
            var selected = repository.cosmetics().get(player.getUniqueId()); if (selected == null) continue;
            var town = town(player);
            if (town == null || !selected.town().equals(town.getUUID()) || !player.hasPermission("neverlandtownyachievements.use") || player.getGameMode() == GameMode.SPECTATOR || player.isInvisible()) continue;
            var p = state(selected.town()).progress().get(selected.achievement()); if (p == null || !p.earned()) continue;
            Particle particle = switch (p.definition().reward().cosmetic()) {
                case "spark", "architect" -> Particle.END_ROD;
                case "celebration" -> Particle.HAPPY_VILLAGER;
                case "guard" -> Particle.CRIT;
                case "wonder" -> Particle.ENCHANT;
                default -> null;
            };
            // Visible to the owner only: does not reveal vanished players or add packet load to neighbours.
            if (particle != null) player.spawnParticle(particle, player.getLocation().add(0, 0.3, 0), 5, 0.35, 0.2, 0.35, 0.01);
        }
    }
    private void publishPlaceholders() {
        Map<UUID, Map<String, String>> values = new HashMap<>();
        for (Town town : TownyAPI.getInstance().getTowns()) {
            var city = state(town.getUUID()); String count = Long.toString(city.progress().values().stream().filter(Progress::earned).count());
            var view = Map.of("title", title(town.getUUID()), "count", count);
            for (var resident : town.getResidents()) values.put(resident.getUUID(), view);
        }
        placeholders = Map.copyOf(values);
    }
    public String placeholder(UUID player, String key) { return placeholders.getOrDefault(player, Map.of()).getOrDefault(key, ""); }
    public List<ActivityAdmin.Target> adminTargets() {
        ApiServices.primaryThread(); var result = new ArrayList<ActivityAdmin.Target>();
        for (Town town : TownyAPI.getInstance().getTowns()) {
            var city = state(town.getUUID());
            result.add(new ActivityAdmin.Target(town.getUUID().toString(), town.getName() + " — достижения: " + (city.paused() ? "пауза" : "учёт включён"),
                    city.paused() ? Set.of("status", "resume") : Set.of("status", "pause", "restart"), (action, minutes) -> control(town.getUUID(), action)));
        }
        return List.copyOf(result);
    }
    public String control(UUID townId, String action) throws Exception {
        ApiServices.primaryThread(); Town town = TownyAPI.getInstance().getTown(townId); if (town == null) throw new IllegalArgumentException("Город удалён");
        var city = state(townId);
        switch (action) {
            case "pause" -> repository.put(townId, city.paused(true));
            case "resume" -> repository.put(townId, city.paused(false));
            case "restart" -> { if (city.paused()) throw new IllegalArgumentException("Сначала возобновите учёт достижений"); check(town); }
            case "status" -> { }
            default -> throw new IllegalArgumentException("Доступны status, pause, resume, restart (повторная проверка)");
        }
        return town.getName() + ": открыто " + state(townId).progress().values().stream().filter(Progress::earned).count() + "; " + (state(townId).paused() ? "пауза" : "учёт включён") + "; награды сохранены";
    }
    @Override public boolean unlocked(UUID town, String id) { ApiServices.primaryThread(); var p = valid(town) ? state(town).progress().get(id) : null; return p != null && p.earned(); }
    @Override public String title(UUID town) { ApiServices.primaryThread(); if (!valid(town)) return ""; var city = state(town); var p = city.progress().get(city.title()); return p == null ? "" : p.definition().reward().title(); }
    @Override public double bonus(UUID town, String effect) { ApiServices.primaryThread(); return valid(town) && "happiness".equals(effect) ? state(town).happiness() : 0; }
    private boolean valid(UUID town) { return town != null && TownyAPI.getInstance().getTown(town) != null; }
    @Override public List<Map<String, Object>> achievements(UUID town) {
        ApiServices.primaryThread(); if (!valid(town)) return List.of();
        return catalogue(town).values().stream().map(p -> Map.<String, Object>of("id", p.definition().id(), "name", p.definition().name(), "progress", p.best(), "target", p.definition().target(), "earned", p.earned(), "earnedAt", p.earnedAt(), "paused", state(town).paused(), "title", p.definition().reward().title())).toList();
    }
}
