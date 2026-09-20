package ru.neverland.core;

import java.util.UUID;

/** Absent addon is neutral; an installed but unavailable provider pauses population simulation. */
public final class AchievementBonuses {
    private AchievementBonuses() { }
    public static double happiness(UUID town) throws ReflectiveOperationException {
        var c = ApiServices.connect("NeverLandTownyAchievements", "ru.neverland.townyachievements.api.TownyAchievementsApi", 1, "bonus");
        if (c.state() == ApiServices.State.NOT_INSTALLED) return 0;
        Object value = c.invoke("bonus", new Class<?>[]{UUID.class, String.class}, town, "happiness");
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0 || n.doubleValue() > 10)
            throw new IllegalStateException("Некорректный бонус достижений");
        return n.doubleValue();
    }
}
