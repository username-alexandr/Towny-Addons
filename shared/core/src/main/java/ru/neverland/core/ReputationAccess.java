package ru.neverland.core;

import java.util.UUID;

/** Optional provider: absence is neutral; installed unhealthy providers cannot quote a fee. */
public final class ReputationAccess {
    private static final String PLUGIN = "NeverLandTownyReputation", API = "ru.neverland.reputation.api.TownyReputationApi";
    private ReputationAccess() { }
    public static boolean deliver(ReputationOutcome outcome) {
        var c = ApiServices.connect(PLUGIN, API, 1, "recordOutcome", "healthy");
        if (c.state() == ApiServices.State.NOT_INSTALLED) return true;
        if (!c.ready()) return false;
        try {
            if (!Boolean.TRUE.equals(c.invoke("healthy", new Class<?>[0]))) return false;
            Object result = c.invoke("recordOutcome", new Class<?>[]{String.class, UUID.class, String.class, String.class, long.class, String.class},
                    outcome.scope(), outcome.subject(), outcome.rule(), outcome.id(), outcome.at(), outcome.context());
            return "APPLIED".equals(result) || "DUPLICATE".equals(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { return false; }
    }
    public static double tradeFeeMultiplier(UUID town) {
        var c = ApiServices.connect(PLUGIN, API, 1, "tradeFeeMultiplier", "healthy");
        if (c.state() == ApiServices.State.NOT_INSTALLED) return 1;
        if (!c.ready()) throw new IllegalStateException("Репутация недоступна; новый расчёт отложен");
        try {
            if (!Boolean.TRUE.equals(c.invoke("healthy", new Class<?>[0]))) throw new IllegalStateException("Хранилище репутации недоступно");
            Object value = c.invoke("tradeFeeMultiplier", new Class<?>[]{UUID.class}, town);
            if (value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0 && n.doubleValue() <= 5) return n.doubleValue();
            throw new IllegalStateException("Некорректный коэффициент репутации");
        } catch (ReflectiveOperationException | LinkageError ex) { throw new IllegalStateException("Репутация недоступна", ex); }
    }
}
