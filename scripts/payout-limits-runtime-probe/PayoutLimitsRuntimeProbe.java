package ru.neverland.validation;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Never install on a live server. Marked loopback fixture, no money provider or real players. */
public final class PayoutLimitsRuntimeProbe extends JavaPlugin {
    private final List<String> checks = new ArrayList<>();
    private final String phase = System.getProperty("neverland.probe.phase", "initial");
    private final UUID player = UUID.fromString("d1440110-3586-44b1-a413-0efb3395e72d");
    private final UUID receipt = UUID.fromString("f9053d6c-9a30-47c2-a162-301dbd4c14ed");
    private Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private Object call(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }
    private void check(boolean value, String label) {
        checks.add((value ? "PASS " : "FAIL ") + label);
        if (!value) throw new AssertionError(label);
    }
    @Override public void onEnable() {
        if (!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_PAYOUT_LIMITS_PROBE"))
                || !getServer().getIp().equals("127.0.0.1")) {
            getServer().getPluginManager().disablePlugin(this); return;
        }
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> run(), 60L);
    }
    private void run() {
        Throwable failure = null;
        try {
            var plugin = Bukkit.getPluginManager().getPlugin("NeverLandTownyContracts");
            check(plugin != null && plugin.isEnabled(), "Contracts enabled on actual Folia 26.2");
            check(plugin.getPluginMeta().getVersion().equals("0.4.2"), "exact new plugin version loaded");
            var orders = field(plugin, "publicOrders");
            check(orders != null, "five-million global budget does not suspend public orders");
            var income = field(orders, "income");
            check((boolean) call(income, "enabled"), "public income runtime enabled");
            var ledger = call(income, "ledger");
            check((long) field(ledger, "playerLimit") == 500_000L, "personal 5000 cap retained in live ledger");
            check((long) field(ledger, "serverLimit") == 500_000_000L, "global 5000000 cap retained in live ledger");
            var yaml = new YamlConfiguration();
            yaml.load(plugin.getDataFolder().toPath().resolve("orders.yml").toFile());
            check(yaml.getInt("limits.player-24h") == 5000 && yaml.getLong("limits.server-24h") == 5_000_000,
                    "custom configuration not reset during startup");
            check(yaml.getInt("catalog-version") == 5 && yaml.getConfigurationSection("templates").getKeys(false).size() == 670,
                    "all 670 catalogue templates preserved");
            check(plugin.getServer().getPluginCommand("orders").getExecutor() == orders, "orders command uses active board executor");
            check(plugin.getClass().getMethod("companyContracts").invoke(plugin) != null, "existing city/company contract service retained");
            var materials = field(orders, "materials");
            check((boolean) call(materials, "available"), "real SQLite material warehouse opened");
            @SuppressWarnings("unchecked") Map<UUID, ?> running = (Map<UUID, ?>) field(orders, "orders");
            var accepted = running.get(UUID.fromString("79bd28b6-000f-4b51-9577-16ddae145a12"));
            check(accepted != null && (int) call(accepted, "progress") == 1,
                    "accepted order and existing progress preserved");
            Method get = ledger.getClass().getMethod("get", UUID.class);
            if (phase.equals("initial")) {
                check(get.invoke(ledger, receipt) == null, "fixture starts without test receipt");
                ledger.getClass().getMethod("reserve", UUID.class, UUID.class, long.class, String.class, boolean.class)
                        .invoke(ledger, receipt, player, 12_345L, "native-budget-proof", true);
            }
            var entry = get.invoke(ledger, receipt);
            check(entry != null && (long) call(entry, "cents") == 12_345L && call(entry, "state").toString().equals("HELD"),
                    "unpaid test receipt retained without making any bank payment");
            ledger.getClass().getMethod("reserve", UUID.class, UUID.class, long.class, String.class, boolean.class)
                    .invoke(ledger, receipt, player, 12_345L, "native-budget-proof", true);
            check(((List<?>) call(ledger, "all")).size() == 1, "same receipt cannot create a second reward");
            check((long) ledger.getClass().getMethod("remaining", UUID.class).invoke(ledger, player) == 487_655L,
                    "reserved reward reduces personal allowance once");
        } catch (Throwable error) {
            failure = error; checks.add("FAIL " + error);
            getLogger().log(java.util.logging.Level.SEVERE, "PAYOUT_LIMITS_PROBE_FAIL", error);
        }
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.write(getDataFolder().toPath().resolve(phase + "-checks.txt"), checks);
            Files.writeString(getDataFolder().toPath().resolve(phase + "-result.txt"),
                    "phase=" + phase + "\nfailed=" + (failure == null ? 0 : 1) + "\nchecks=" + checks.size() + "\n");
        } catch (Exception error) { getLogger().log(java.util.logging.Level.SEVERE, "PROOF_WRITE_FAIL", error); }
        Bukkit.shutdown();
    }
}
