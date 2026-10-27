package ru.neverland.mintcontracts.orders;

import java.math.BigDecimal;
import org.bukkit.configuration.ConfigurationSection;

/** Daily budgets are totals across payouts, not the maximum size of one reward. */
public record PayoutLimits(long playerCents, long serverCents) {
    public static final long MAX_COINS = 1_000_000_000_000L;
    private static final BigDecimal MAXIMUM = BigDecimal.valueOf(MAX_COINS);

    public static PayoutLimits read(ConfigurationSection config) {
        long player = budget(config, "limits.player-24h", OrderCatalog.DEFAULT_PLAYER_LIMIT);
        long server = budget(config, "limits.server-24h", 100000);
        if (server < player) throw new IllegalArgumentException(
                "orders.yml: limits.server-24h (общий лимит) должен быть не меньше limits.player-24h (лимит игрока)");
        return new PayoutLimits(player, server);
    }

    private static long budget(ConfigurationSection config, String key, long fallback) {
        Object raw = config.get(key);
        if (raw == null) raw = fallback;
        try {
            if (!(raw instanceof Number)) throw new NumberFormatException();
            BigDecimal coins = new BigDecimal(raw.toString());
            if (coins.signum() <= 0 || coins.compareTo(MAXIMUM) > 0) throw new ArithmeticException();
            return coins.movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("orders.yml: " + key
                    + " — нужно положительное число от 0.01 до " + MAX_COINS
                    + " монет, максимум 2 знака после точки. Например: 5000000."
                    + " Записывайте без кавычек, пробелов и обозначения валюты; 0 и -1 не отключают лимит.", invalid);
        }
    }
}
