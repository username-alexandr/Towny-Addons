package ru.neverland.mintevents.model;

import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** A wave cannot advance while a spawn is pending or a tracked entity is alive/unloaded. */
public final class RaidState {
    public static final int WAVES = 10;
    private final UUID generation = UUID.randomUUID();
    private int wave;
    private int waveSize;
    private final Deque<String> pending = new ArrayDeque<>();
    private final Map<UUID, String> alive = new LinkedHashMap<>();
    public UUID generation() { return generation; }
    public int wave() { return wave; }
    public int remaining() { return pending.size() + alive.size(); }
    public int aliveCount() { return alive.size(); }
    public String next() { return pending.peekFirst(); }
    public boolean clear() { return remaining() == 0; }
    public boolean finished() { return wave == WAVES && clear(); }
    public boolean owns(UUID id) { return alive.containsKey(id); }
    public void begin(List<String> composition) {
        if (!clear() || wave >= WAVES || composition.isEmpty()) throw new IllegalStateException("Wave cannot start");
        wave++; pending.addAll(composition); waveSize = composition.size();
    }
    public void spawned(UUID id) {
        if (alive.containsKey(id) || pending.isEmpty()) throw new IllegalStateException("Unexpected spawn");
        alive.put(id, pending.removeFirst());
    }
    public String died(UUID id) { return alive.remove(id); }
    public double ratio() {
        if (wave == 0) return 0;
        return Math.max(0, Math.min(1, (wave - 1 + (waveSize - remaining()) / (double) Math.max(1, waveSize)) / WAVES));
    }
    public void save(ConfigurationSection out) {
        out.set("wave", wave); out.set("size", waveSize);
        out.set("pending", new ArrayList<>(pending));
        // On restart only unfinished enemies are recreated. A new generation invalidates old bodies.
        out.set("alive", new ArrayList<>(alive.values()));
    }
    public static RaidState load(ConfigurationSection in) {
        RaidState state = new RaidState();
        if (in == null) return state;
        state.wave = Math.max(0, Math.min(WAVES, in.getInt("wave")));
        state.pending.addAll(in.getStringList("pending"));
        state.pending.addAll(in.getStringList("alive"));
        state.waveSize = Math.max(state.remaining(), in.getInt("size"));
        return state;
    }
}
