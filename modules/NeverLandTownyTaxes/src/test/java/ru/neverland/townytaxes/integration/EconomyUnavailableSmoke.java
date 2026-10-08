package ru.neverland.townytaxes.integration;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townytaxes.data.CivicRepository;
import ru.neverland.townytaxes.model.Domain;
import ru.neverland.townytaxes.model.TaxPolicy;
import ru.neverland.townytaxes.service.FiscalService;

public final class EconomyUnavailableSmoke {
    public static class Fixture extends JavaPlugin {
        FileConfiguration config;
        Logger logger;
        @Override public FileConfiguration getConfig() { return config; }
        @Override public Logger getLogger() { return logger; }
    }
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static TaxPolicy policy(UUID id, long next) {
        return new TaxPolicy(UUID.randomUUID(), Domain.Scope.GLOBAL, id, "Server", Domain.TaxType.FIXED,
                50, Domain.Scope.GLOBAL, TownyHook.GLOBAL_ID, "Server", 60_000, next, true, "test");
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var plugin = (Fixture) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(Fixture.class);
        var directory = Files.createTempDirectory("taxes-economy-");
        var dataFolder = JavaPlugin.class.getDeclaredField("dataFolder");
        dataFolder.setAccessible(true);
        dataFolder.set(plugin, directory.toFile());
        plugin.config = new YamlConfiguration();
        plugin.logger = Logger.getLogger("taxes-economy-test");
        plugin.logger.setUseParentHandlers(false);
        AtomicInteger warnings = new AtomicInteger();
        plugin.logger.addHandler(new Handler() {
            public void publish(LogRecord record) { if (record.getLevel().intValue() >= 900) warnings.incrementAndGet(); }
            public void flush() { }
            public void close() { }
        });

        // Real Towny 0.103.2.0, with no economy initialized: do not even create an account.
        var unavailable = new TownyHook(plugin);
        check(!unavailable.economyAvailable(), "Towny without an adapter must not be active");
        check(unavailable.account(() -> { throw new AssertionError("getAccount called without provider"); }) == null,
                "cached balance constructor is never entered while unavailable");

        var repository = new CivicRepository(plugin);
        repository.load();
        UUID player = UUID.randomUUID();
        var overdue = policy(player, 1);
        var future = policy(player, System.currentTimeMillis() + 86_400_000);
        repository.put(overdue);
        repository.put(future);
        repository.debt(player, 25);
        repository.addServerBank(100);
        repository.save();
        AtomicBoolean active = new AtomicBoolean(false);
        var hook = new TownyHook(plugin, active::get);
        var fiscal = new FiscalService(plugin, hook, repository, null);
        var snapshotsField = FiscalService.class.getDeclaredField("balanceSnapshots");
        snapshotsField.setAccessible(true);
        var snapshots = (Map<UUID, Double>) snapshotsField.get(fiscal);
        snapshots.put(player, 10D);

        // No Bukkit server or Towny universe: any accidental scan/withdraw would fail this test.
        for (int i = 0; i < 3; i++) fiscal.collectNow();
        fiscal.onTransaction(null);
        check(!fiscal.payDebt(new TownyHook.Party(Domain.Scope.PLAYER, player, "Player", null)), "debt payments pause");
        check(snapshots.isEmpty(), "discard pre-outage balances, never calculate outage income");
        check(repository.debt(player) == 25 && repository.serverBank() == 100, "no debt or reserve changes during outage");
        check(repository.ledger().isEmpty(), "no failed-payment entries during outage");
        check(repository.policy(overdue.id().toString()).nextCollection() == 1, "no collections during outage");
        check(warnings.get() == 1, "one warning per outage, not one per resident or tick");

        var pausedReload = new CivicRepository(plugin);
        pausedReload.load();
        check(pausedReload.economyPaused(), "pause survives a server restart");
        active.set(true);
        var restarted = new FiscalService(plugin, hook, pausedReload, null);
        check(restarted.economyReady() && !pausedReload.economyPaused(), "restart with restored provider resumes safely");
        check(pausedReload.policy(overdue.id().toString()).nextCollection() > System.currentTimeMillis(), "restart never catches up missed collections");
        AtomicInteger accountReads = new AtomicInteger();
        hook.account(() -> { accountReads.incrementAndGet(); return null; });
        check(accountReads.get() == 1, "available adapter permits normal account access");
        long beforeRecovery = System.currentTimeMillis();
        check(fiscal.economyReady(), "resume when provider becomes available");
        long next = repository.policy(overdue.id().toString()).nextCollection();
        check(next >= beforeRecovery + 60_000, "missed fixed collection starts a fresh interval");
        check(repository.policy(future.id().toString()).nextCollection() == future.nextCollection(), "future deadline unchanged");
        snapshots.put(player, 500D);
        check(fiscal.economyReady() && snapshots.get(player) == 500D, "healthy checks retain normal income baseline");
        check(repository.policy(overdue.id().toString()).nextCollection() == next, "healthy checks never postpone collections");
        var reloaded = new CivicRepository(plugin);
        reloaded.load();
        check(reloaded.policy(overdue.id().toString()).nextCollection() == next, "recovery deadline is persisted");
        check(reloaded.debt(player) == 25 && reloaded.serverBank() == 100, "existing financial state survives recovery");

        active.set(false);
        fiscal.collectNow();
        check(snapshots.isEmpty() && warnings.get() == 2, "a second outage clears its baseline and warns once");
        check(repository.ledger().isEmpty() && repository.debt(player) == 25, "repeated outages create no penalties");
        System.out.println("EconomyUnavailableSmoke PASS: " + checks + " assertions");
    }
}
