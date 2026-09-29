package ru.neverland.core;

/** One ledger monitor shared by all shaded copies of the suite.
 * This protects plugin data only: it never grants ownership of a Bukkit entity or chunk.
 * Never wait for another scheduler while holding this monitor.
 */
public final class SuiteState {
    private static final Object LOCK = "ru.neverland.towny.ledger.v1".intern();
    @SuppressWarnings("unchecked")
    private static final java.util.Set<String> FROZEN=(java.util.Set<String>)System.getProperties().computeIfAbsent(
            "ru.neverland.towny.frozen.v1",ignored->java.util.concurrent.ConcurrentHashMap.<String>newKeySet());
    private SuiteState() {}
    public static Object lock() { return LOCK; }
    public static boolean held() { return Thread.holdsLock(LOCK); }
    public static boolean frozen(String plugin) { return FROZEN.contains(plugin); }
    public static void freeze(String plugin) { FROZEN.add(plugin); }
    public static void thaw(String plugin) { FROZEN.remove(plugin); }
    public static void run(Runnable action) { synchronized (LOCK) { action.run(); } }
}
