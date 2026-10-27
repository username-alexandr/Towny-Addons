package ru.neverland.mintcontracts.orders;

import java.nio.file.Files;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.earnings.RewardLedger;

public final class PayoutLimitsSmoke {
    private static int checks;
    private static void ok(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++;
    }
    private static YamlConfiguration config(Object player, Object server) {
        var c = new YamlConfiguration();
        c.set("limits.player-24h", player); c.set("limits.server-24h", server);
        return c;
    }
    public static void main(String[] args) throws Exception {
        var c = config(5000, 5_000_000);
        var limits = PayoutLimits.read(c);
        ok(limits.playerCents() == 500_000L && limits.serverCents() == 500_000_000L,
                "five million global budget must not stop orders");
        ok(c.getInt("limits.player-24h") == 5000 && c.getInt("limits.server-24h") == 5_000_000,
                "configured limits never rewritten");
        ok(PayoutLimits.read(config(0.01, 1_234_567.89)).serverCents() == 123_456_789L,
                "cent conversion exact above former cap");
        ok(PayoutLimits.read(config(0.29, PayoutLimits.MAX_COINS)).playerCents() == 29,
                "ordinary decimal does not fail on binary rounding");
        ok(PayoutLimits.read(config(5000, PayoutLimits.MAX_COINS)).serverCents() == 100_000_000_000_000L,
                "largest budget remains well inside long and exact double cents");
        ok(PayoutLimits.read(new YamlConfiguration()).serverCents() == 10_000_000,
                "existing defaults retained");
        var yaml = new YamlConfiguration();
        yaml.loadFromString("limits:\n  player-24h: 5000\n  server-24h: 5000000\n");
        ok(PayoutLimits.read(yaml).equals(limits), "real YAML numeric types accepted");
        for (Object bad : new Object[]{0, -1, 0.001, 12.345, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Long.MAX_VALUE, PayoutLimits.MAX_COINS + 1,
                "5000000", "5 000 000", "5к", true}) {
            try { PayoutLimits.read(config(5000, bad)); throw new AssertionError("accepted " + bad); }
            catch (IllegalArgumentException expected) {
                ok(expected.getMessage().contains("orders.yml: limits.server-24h")
                        && expected.getMessage().contains("Например: 5000000"), "actionable invalid-value error");
            }
        }
        try { PayoutLimits.read(config(5000, 4000)); throw new AssertionError("lower server budget accepted"); }
        catch (IllegalArgumentException expected) {
            ok(expected.getMessage().contains("не меньше"), "budget relation explained");
        }
        try { PublicOrders.money(config(null, null), "reward", 1_000_001); throw new AssertionError("reward cap removed"); }
        catch (IllegalArgumentException expected) { checks++; }

        var path = Files.createTempDirectory("larger-order-budget");
        var large = PayoutLimits.read(config(1_000_000, 2_500_000));
        var ledger = new RewardLedger(path, large.playerCents(), large.serverCents(), () -> 1000L);
        ledger.load();
        UUID first = UUID.randomUUID();
        UUID receipt = UUID.randomUUID();
        ledger.reserve(receipt, first, 100_000_000, "first", false);
        ledger.reserve(UUID.randomUUID(), UUID.randomUUID(), 100_000_000, "second", false);
        ledger.reserve(UUID.randomUUID(), UUID.randomUUID(), 50_000_000, "third", false);
        ok(ledger.used(null) == 250_000_000, "large global budget enforced across ordinary rewards");
        ok(ledger.remaining(first) == 0, "personal budget still enforced");
        try { ledger.reserve(UUID.randomUUID(), UUID.randomUUID(), 1, "overflow", false); throw new AssertionError("over budget"); }
        catch (IllegalArgumentException expected) { checks++; }
        ledger.reserve(receipt, first, 100_000_000, "first", false);
        ok(ledger.all().size() == 3, "replayed receipt cannot pay twice");
        var restarted = new RewardLedger(path, large.playerCents(), large.serverCents(), () -> 1000L);
        restarted.load();
        ok(restarted.used(null) == 250_000_000 && restarted.remaining(UUID.randomUUID()) == 0,
                "restart retains obligations and global budget");
        System.out.println("PASS payout limits: " + checks + " checks");
    }
}
