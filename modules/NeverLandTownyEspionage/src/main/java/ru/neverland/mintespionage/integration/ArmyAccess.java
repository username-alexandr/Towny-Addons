package ru.neverland.mintespionage.integration;
import java.util.*;
import ru.neverland.core.ApiServices;
/** Optional army; a broken installed provider must not erase paid-operation defense. */
public final class ArmyAccess {
    private ArmyAccess() { }
    public static Optional<Map<String, Object>> garrison(UUID town) {
        var api = ApiServices.connect("NeverLandTownyArmy", "ru.neverland.townyarmy.api.TownyArmyApi", 1, "healthy", "garrison");
        if (api.state() == ApiServices.State.NOT_INSTALLED) return Optional.empty();
        try {
            if (!api.ready() || !Boolean.TRUE.equals(api.invoke("healthy", new Class<?>[]{}))) throw new IllegalStateException("Армия временно недоступна");
            Object raw = api.invoke("garrison", new Class<?>[]{UUID.class}, town);
            if (!(raw instanceof Optional<?> result)) throw new IllegalStateException("Несовместимый ответ Army API");
            if (result.isEmpty()) return Optional.empty();
            if (!(result.get() instanceof Map<?, ?> map)) throw new IllegalStateException("Несовместимый гарнизон Army API");
            var out = new HashMap<String, Object>(); for (var e : map.entrySet()) if (e.getKey() instanceof String key) out.put(key, e.getValue());
            double defense = ((Number)out.get("defenseBonus")).doubleValue();
            if (!Double.isFinite(defense) || defense < 0 || defense > .5) throw new IllegalStateException("Неверный бонус армии");
            return Optional.of(Map.copyOf(out));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { throw new IllegalStateException("Разведка приостановлена: Army API недоступен", e); }
    }
    public static double defense(UUID town) { return garrison(town).map(g -> ((Number)g.get("defenseBonus")).doubleValue()).orElse(0.0); }
}
