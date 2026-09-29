package ru.neverland.core;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
/** Cancellable repeating global ledger task. Physical work must be explicitly dispatched. */
public abstract class SuiteRunnable implements Runnable {
    private BukkitTask task;
    public BukkitTask runTaskTimer(Plugin plugin,long delay,long period) {
        if(task!=null)throw new IllegalStateException("Already scheduled");
        return task=SuiteScheduler.get().runTaskTimer(plugin,this,delay,period);
    }
    public void cancel(){if(task!=null)task.cancel();}
}
