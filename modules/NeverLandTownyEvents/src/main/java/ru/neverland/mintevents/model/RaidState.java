package ru.neverland.mintevents.model;

import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
import ru.neverland.core.SafeYaml;
import ru.neverland.mintevents.service.RaidCatalog;
import ru.neverland.mintevents.service.RaidScaling;

/** A wave cannot advance while a spawn is pending or a tracked entity is alive/unloaded. */
public final class RaidState {
    public static final int WAVES = 10;
    private final UUID generation = UUID.randomUUID();
    private int wave;
    private int waveSize;
    private final Deque<String> pending = new ArrayDeque<>();
    private final Map<UUID, String> alive = new LinkedHashMap<>();
    private final RaidScaling.Profile profile;
    private final List<RaidCatalog.Wave> plannedWaves;
    /** Old saves retain their original unscaled behavior until completion. */
    public RaidState() { profile = RaidScaling.Profile.legacy(); plannedWaves = List.of(); }
    public RaidState(RaidScaling.Profile profile, List<RaidCatalog.Wave> plannedWaves) {
        this.profile = Objects.requireNonNull(profile);
        if (plannedWaves.size() != WAVES) throw new IllegalArgumentException("Нужно десять волн");
        this.plannedWaves = plannedWaves.stream().map(w -> {
            validateWave(w);
            return new RaidCatalog.Wave(List.copyOf(w.mobs()), w.healthMultiplier(), w.damageMultiplier());
        }).toList();
    }
    public RaidScaling.Profile profile() { return profile; }
    public RaidCatalog.Wave waveDefinition(int number, RaidCatalog fallback) {
        return plannedWaves.isEmpty() ? fallback.wave(number) : plannedWaves.get(Math.max(1, Math.min(WAVES, number)) - 1);
    }
    public UUID generation() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return generation; } }
    public int wave() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return wave; } }
    public int remaining() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return pending.size() + alive.size(); } }
    public int aliveCount() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return alive.size(); } }
    public String next() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return pending.peekFirst(); } }
    public boolean clear() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return remaining() == 0; } }
    public boolean finished() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return wave == WAVES && clear(); } }
    public boolean owns(UUID id) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return alive.containsKey(id); } }
    public void begin(List<String> composition) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        if (!clear() || wave >= WAVES || composition.isEmpty()) throw new IllegalStateException("Wave cannot start");
        wave++; pending.addAll(composition); waveSize = composition.size();
    } }
    public void spawned(UUID id) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        if (alive.containsKey(id) || pending.isEmpty()) throw new IllegalStateException("Unexpected spawn");
        alive.put(id, pending.removeFirst());
    } }
    public String died(UUID id) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return alive.remove(id); } }
    public double ratio() { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        if (wave == 0) return 0;
        return Math.max(0, Math.min(1, (wave - 1 + (waveSize - remaining()) / (double) Math.max(1, waveSize)) / WAVES));
    } }
    public void save(ConfigurationSection out) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        out.set("wave", wave); out.set("size", waveSize);
        out.set("pending", new ArrayList<>(pending));
        // On restart only unfinished enemies are recreated. A new generation invalidates old bodies.
        out.set("alive", new ArrayList<>(alive.values()));
        out.set("plan-version", null); out.set("development", null); out.set("planned-waves", null);
        if (!plannedWaves.isEmpty()) {
            out.set("plan-version", 1);
            profile.save(out.createSection("development"));
            for (int i = 0; i < WAVES; i++) {
                var section = out.createSection("planned-waves." + (i + 1));
                var w = plannedWaves.get(i);
                section.set("mobs", w.mobs()); section.set("health", w.healthMultiplier()); section.set("damage", w.damageMultiplier());
            }
        }
    } }
    public static RaidState load(ConfigurationSection in) { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
        if (in == null) return new RaidState();
        RaidState state;
        if (in.contains("plan-version")) {
            if (SafeYaml.intValue(in, "plan-version") != 1) throw new IllegalArgumentException("Неизвестная версия плана набега");
            var development = SafeYaml.section(in, "development");
            if (development == null) throw new IllegalArgumentException("Отсутствуют показатели развития набега");
            var profile = RaidScaling.Profile.load(development);
            var plans = new ArrayList<RaidCatalog.Wave>();
            for (int i = 1; i <= WAVES; i++) {
                var section = SafeYaml.section(in, "planned-waves." + i);
                if (section == null) throw new IllegalArgumentException("Отсутствует сохранённая волна " + i);
                plans.add(new RaidCatalog.Wave(SafeYaml.strings(section, "mobs"), SafeYaml.doubleValue(section, "health"), SafeYaml.doubleValue(section, "damage")));
            }
            state = new RaidState(profile, plans);
        } else {
            if (in.contains("development") || in.contains("planned-waves")) throw new IllegalArgumentException("Отсутствует версия сохранённого плана набега");
            state = new RaidState();
        }
        state.wave = Math.max(0, Math.min(WAVES, in.getInt("wave")));
        state.pending.addAll(in.getStringList("pending"));
        state.pending.addAll(in.getStringList("alive"));
        state.waveSize = Math.max(state.remaining(), in.getInt("size"));
        return state;
    } }
    private static void validateWave(RaidCatalog.Wave wave) {
        if (wave.mobs().isEmpty() || wave.mobs().size() > 200 || wave.mobs().stream().anyMatch(id -> id == null || id.isBlank())
                || !Double.isFinite(wave.healthMultiplier()) || wave.healthMultiplier() <= 0 || wave.healthMultiplier() > 50_000
                || !Double.isFinite(wave.damageMultiplier()) || wave.damageMultiplier() <= 0 || wave.damageMultiplier() > 30_000)
            throw new IllegalArgumentException("Повреждён сохранённый план волны");
    }
}
