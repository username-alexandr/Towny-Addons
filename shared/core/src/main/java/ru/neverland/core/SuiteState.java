package ru.neverland.core;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** Shared ledger lock. It does not grant ownership of Bukkit entities or chunks. */
public final class SuiteState {
    // JDK types only: every shaded plugin classloader sees the SAME lock and diagnostics.
    private static final ReentrantLock LOCK = shared("ru.neverland.towny.ledger.v2", new ReentrantLock(true));
    private static final AtomicReference<Object[]> OWNER = shared("ru.neverland.towny.ledger.owner.v2", new AtomicReference<>());
    private static final AtomicLong DEFERRED = shared("ru.neverland.towny.ledger.deferred.v2", new AtomicLong());
    private static final AtomicLong WARNED = shared("ru.neverland.towny.ledger.warned.v2", new AtomicLong());
    private static final java.util.Set<String> FROZEN = shared("ru.neverland.towny.frozen.v1", ConcurrentHashMap.<String>newKeySet());
    private SuiteState() {}
    @SuppressWarnings("unchecked") private static <T> T shared(String key, T value) {
        return (T) System.getProperties().computeIfAbsent(key, ignored -> value);
    }
    public static int protocolVersion() { return 2; }
    public static boolean held() { return LOCK.isHeldByCurrentThread(); }
    public static boolean frozen(String plugin) { return FROZEN.contains(plugin); }
    public static void freeze(String plugin) { FROZEN.add(plugin); }
    public static void thaw(String plugin) { FROZEN.remove(plugin); }

    /** Reentrant scope for existing synchronous APIs. Scheduled callbacks use tryRun instead. */
    public static Guard guard() { LOCK.lock(); return entered("direct call"); }
    private static Guard entered(String label) {
        if (LOCK.getHoldCount() == 1) OWNER.set(new Object[]{Thread.currentThread(), System.nanoTime(), label});
        return new Guard();
    }
    public static final class Guard implements AutoCloseable {
        private final Thread thread = Thread.currentThread();
        private boolean closed;
        private Guard() {}
        @Override public void close() {
            if (closed || thread != Thread.currentThread()) throw new IllegalMonitorStateException("Invalid suite guard release");
            closed = true;
            if (LOCK.getHoldCount() == 1) OWNER.set(null);
            LOCK.unlock();
        }
    }
    public static void run(Runnable action) { try (var guard = guard()) { action.run(); } }
    /** No waiting on the global/region/entity scheduler, even when another region holds the ledger. */
    public static boolean tryRun(String label, Runnable action) {
        if ((!held() && LOCK.hasQueuedThreads()) || !LOCK.tryLock()) { DEFERRED.incrementAndGet(); return false; }
        try (var guard = entered(label)) { action.run(); }
        return true;
    }
    public static long deferredCount() { return DEFERRED.get(); }
    /** Lock-free diagnostics, including the actual owner rather than only the waiting plugin. */
    public static List<String> diagnostics() {
        Object[] value = OWNER.get();
        if (value == null) return List.of("Общая блокировка свободна.", "Отложено попыток: " + deferredCount());
        Thread thread = (Thread) value[0];
        var result = new java.util.ArrayList<String>();
        result.add("Владелец: " + thread.getName() + " (" + thread.getState() + ")");
        result.add("Задача: " + value[2] + "; удержание " + Math.max(0, (System.nanoTime() - (Long) value[1]) / 1_000_000) + " мс");
        result.add("Отложено попыток: " + deferredCount());
        StackTraceElement[] stack = thread.getStackTrace();
        for (int i = 0; i < Math.min(18, stack.length); i++) result.add("  at " + stack[i]);
        return List.copyOf(result);
    }
    public static void warnIfStalled(java.util.logging.Logger logger, String waiting) {
        Object[] value = OWNER.get(); long now = System.nanoTime();
        if (value == null || now - (Long) value[1] < 1_000_000_000L) return;
        long previous = WARNED.get();
        if (previous != 0 && now - previous < 10_000_000_000L || !WARNED.compareAndSet(previous, now)) return;
        logger.warning("[NeverLand ledger] Отложена " + waiting + "; поток Folia не ожидает блокировку.\n" + String.join("\n", diagnostics()));
    }
}
