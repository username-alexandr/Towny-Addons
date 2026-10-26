package ru.neverland.core;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Busy ledger callbacks defer on the SAME scheduler instead of waiting. */
public final class SuiteScheduler {
    private static final SuiteScheduler INSTANCE = new SuiteScheduler();
    private static final Set<Task> TASKS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger IDS = new AtomicInteger();
    private SuiteScheduler() {}
    public static SuiteScheduler get() { return INSTANCE; }
    public BukkitTask runTask(Plugin plugin, Runnable action) { return runTaskLater(plugin, action, 1); }
    public BukkitTask runTaskLater(Plugin plugin, Runnable action, long delay) {
        return new Task(plugin, action, false, 0, (ticks, run, retired) ->
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> run.run(), ticks), null).start(delay);
    }
    public BukkitTask runTaskTimer(Plugin plugin, Runnable action, long delay, long period) {
        return new Task(plugin, action, false, Math.max(1, period), (ticks, run, retired) ->
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> run.run(), ticks), null).start(delay);
    }
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable action) {
        return new Task(plugin, action, true, 0, (ticks, run, retired) ->
                Bukkit.getAsyncScheduler().runNow(plugin, ignored -> run.run()), null).start(1);
    }
    public static BukkitTask entity(Plugin plugin, Entity entity, Runnable action) { return entityLater(plugin, entity, action, null, 1); }
    public static BukkitTask entityLater(Plugin plugin, Entity entity, Runnable action, Runnable retired, long delay) {
        return new Task(plugin, action, false, 0, (ticks, run, gone) -> entity == null ? null :
                entity.getScheduler().runDelayed(plugin, ignored -> run.run(), gone, ticks), retired).start(delay);
    }
    public static BukkitTask region(Plugin plugin, Location location, Runnable action) { return regionLater(plugin, location, action, 1); }
    public static BukkitTask regionLater(Plugin plugin, Location location, Runnable action, long delay) {
        if (location == null || location.getWorld() == null) throw new IllegalArgumentException("World location required");
        Location anchor = location.clone();
        return new Task(plugin, action, false, 0, (ticks, run, retired) ->
                Bukkit.getRegionScheduler().runDelayed(plugin, anchor, ignored -> run.run(), ticks), null).start(delay);
    }
    public static void player(Plugin plugin, Player player, Consumer<Player> action) {
        if (plugin.isEnabled() && !SuiteState.frozen(plugin.getName())) entity(plugin, player, () -> { if (player.isOnline()) action.accept(player); });
    }
    public static void cleanupPlayer(Plugin plugin, Player player, Consumer<Player> action) {
        if (owns(player)) { action.accept(player); return; }
        Plugin owner = WorldTasks.cleanupOwner(plugin);
        if (owner != null) entity(owner, player, () -> action.accept(player));
    }
    public static void cleanupPlayers(Plugin plugin, Consumer<Player> action) {
        for (Player player : Bukkit.getOnlinePlayers()) cleanupPlayer(plugin, player, action);
    }
    public static void players(Plugin plugin, Consumer<Player> action) {
        for (Player player : Bukkit.getOnlinePlayers()) player(plugin, player, action);
    }
    public static void reply(Plugin plugin, CommandSender sender, Runnable action) {
        if (sender instanceof Entity entity) entity(plugin, entity, action); else get().runTask(plugin, action);
    }
    public static boolean owns(Entity entity) { return entity != null && Bukkit.isOwnedByCurrentRegion(entity); }
    public static boolean owns(Location location) { return location != null && location.getWorld() != null && Bukkit.isOwnedByCurrentRegion(location); }
    public void cancelTasks(Plugin plugin) { for (Task task : TASKS) if (task.owner == plugin) task.cancel(); }

    @FunctionalInterface interface Schedule { ScheduledTask later(long delay, Runnable action, Runnable retired); }
    /** Package-visible for deterministic tests with injected scheduler queues. */
    static final class Task implements BukkitTask {
        private final int id = IDS.incrementAndGet();
        private final Plugin owner;
        private final boolean async;
        private final long period;
        private final Runnable retiredCallback;
        private Runnable action;
        private Schedule schedule;
        private ScheduledTask scheduled;
        private long generation;
        private volatile boolean cancelled;
        private boolean finished, running, retired;
        Task(Plugin owner, Runnable action, boolean async, long period, Schedule schedule, Runnable retiredCallback) {
            this.owner=owner; this.action=action; this.async=async; this.period=period; this.schedule=schedule; this.retiredCallback=retiredCallback;
            TASKS.add(this);
        }
        Task start(long delay) { arm(delay); return this; }
        private void arm(long delay) {
            final long ticket; final Schedule target;
            synchronized (this) { if (cancelled || finished) return; ticket=++generation; target=schedule; }
            ScheduledTask value;
            try { value=target.later(Math.max(1, delay), () -> execute(ticket), () -> retire(ticket)); }
            catch (RuntimeException | Error ex) { cancel(); throw ex; }
            synchronized (this) {
                if (cancelled || finished || generation != ticket) { if (value != null) value.cancel(); return; }
                scheduled=value;
            }
            if (value == null) retire(ticket);
        }
        private void execute(long ticket) {
            synchronized (this) {
                if (cancelled || finished || running || generation != ticket) return;
                running=true; scheduled=null;
            }
            boolean busy=false;
            try {
                if ((!retired && !owner.isEnabled()) || cancelled) { finish(); return; }
                if (!retired && SuiteState.frozen(owner.getName())) return;
                Runnable guarded=() -> {
                    if (!cancelled && (retired || owner.isEnabled() && !SuiteState.frozen(owner.getName()))) action.run();
                };
                if (async) guarded.run();
                else if (!SuiteState.tryRun(owner.getName()+" task="+id+(retired?" retired":""), guarded)) {
                    busy=true; SuiteState.warnIfStalled(owner.getLogger(), owner.getName()+" task="+id);
                }
            } finally {
                synchronized (this) { running=false; }
                // At most one continuation. Slow tasks never accumulate missed timer invocations.
                if (busy) arm(1);
                else if (period > 0 && !retired) arm(period);
                else finish();
            }
        }
        private void retire(long ticket) {
            synchronized (this) {
                if (cancelled || finished || running || retired || generation != ticket) return;
                retired=true; generation++; scheduled=null;
                if (retiredCallback == null) { finish(); return; }
                Plugin schedulerOwner=WorldTasks.cleanupOwner(owner);
                if (schedulerOwner == null) { finish(); return; }
                action=retiredCallback;
                // Retired callbacks cannot touch the entity. Their data cleanup can run globally.
                schedule=(ticks, run, gone) -> Bukkit.getGlobalRegionScheduler().runDelayed(schedulerOwner, ignored -> run.run(), ticks);
            }
            arm(1);
        }
        private synchronized void finish() { finished=true; TASKS.remove(this); }
        public int getTaskId() { return id; }
        public Plugin getOwner() { return owner; }
        public boolean isSync() { return !async; }
        public synchronized boolean isCancelled() { return cancelled; }
        public void cancel() {
            ScheduledTask value;
            synchronized (this) { cancelled=true; generation++; value=scheduled; scheduled=null; TASKS.remove(this); }
            if (value != null) value.cancel();
        }
    }
}
