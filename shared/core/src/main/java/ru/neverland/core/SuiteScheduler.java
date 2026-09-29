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

/** Explicit ownership scheduling, valid on both Paper and Folia. No blocking handoffs. */
public final class SuiteScheduler {
    private static final SuiteScheduler INSTANCE = new SuiteScheduler();
    private static final Set<Task> TASKS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger IDS = new AtomicInteger();
    private SuiteScheduler() {}
    public static SuiteScheduler get() { return INSTANCE; }
    public BukkitTask runTask(Plugin plugin, Runnable action) { return runTaskLater(plugin, action, 1); }
    public BukkitTask runTaskLater(Plugin plugin, Runnable action, long delay) {
        Task task = new Task(plugin, false, false);
        task.bind(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(action), Math.max(1, delay)));
        return task;
    }
    public BukkitTask runTaskTimer(Plugin plugin, Runnable action, long delay, long period) {
        Task task = new Task(plugin, false, true);
        task.bind(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> task.run(action), Math.max(1, delay), Math.max(1, period)));
        return task;
    }
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable action) {
        Task task = new Task(plugin, true, false);
        task.bind(Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run(action)));
        return task;
    }
    public static BukkitTask entity(Plugin plugin, Entity entity, Runnable action) { return entityLater(plugin, entity, action, null, 1); }
    public static BukkitTask entityLater(Plugin plugin, Entity entity, Runnable action, Runnable retired, long delay) {
        Task task = new Task(plugin, false, false);
        if (entity == null) { task.retire(retired); return task; }
        ScheduledTask scheduled = entity.getScheduler().runDelayed(plugin, ignored -> task.run(action), () -> task.retire(retired), Math.max(1, delay));
        task.bind(scheduled);
        if (scheduled == null) task.retire(retired);
        return task;
    }
    public static BukkitTask region(Plugin plugin, Location location, Runnable action) { return regionLater(plugin, location, action, 1); }
    public static BukkitTask regionLater(Plugin plugin, Location location, Runnable action, long delay) {
        if (location == null || location.getWorld() == null) throw new IllegalArgumentException("World location required");
        Task task = new Task(plugin, false, false);
        task.bind(Bukkit.getRegionScheduler().runDelayed(plugin, location.clone(), ignored -> task.run(action), Math.max(1, delay)));
        return task;
    }
    public static void player(Plugin plugin, Player player, Consumer<Player> action) {
        Plugin owner=WorldTasks.cleanupOwner(plugin);
        if(owner!=null)entity(owner, player, () -> { if (player.isOnline()) action.accept(player); });
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

    private static final class Task implements BukkitTask {
        private final int id = IDS.incrementAndGet();
        private final Plugin owner;
        private final boolean async, repeating;
        private volatile ScheduledTask scheduled;
        private volatile boolean cancelled, finished;
        Task(Plugin owner, boolean async, boolean repeating) { this.owner=owner;this.async=async;this.repeating=repeating;TASKS.add(this); }
        void bind(ScheduledTask value) { scheduled=value; if (cancelled && value!=null) value.cancel(); }
        void run(Runnable action) {
            try { if (!cancelled && owner.isEnabled() && !SuiteState.frozen(owner.getName())) {
                if (async) action.run(); else SuiteState.run(() -> { if (!cancelled && owner.isEnabled() && !SuiteState.frozen(owner.getName())) action.run(); });
            } } finally { if (!repeating) { finished=true;TASKS.remove(this); } }
        }
        void retire(Runnable callback) {
            synchronized (this) { if (finished || cancelled) return;finished=true;TASKS.remove(this); }
            if (callback != null) SuiteState.run(callback);
        }
        public int getTaskId() { return id; }
        public Plugin getOwner() { return owner; }
        public boolean isSync() { return !async; }
        public boolean isCancelled() { return cancelled || scheduled!=null && scheduled.isCancelled(); }
        public void cancel() { cancelled=true;ScheduledTask value=scheduled;if(value!=null)value.cancel();TASKS.remove(this); }
    }
}
