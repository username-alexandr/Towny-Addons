package ru.neverland.townyachievements.api;

import java.util.*;

/** Immutable JDK-only views. Calls require the primary server thread. */
public interface TownyAchievementsApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities() { return Set.of("achievements", "unlocked", "title", "bonus"); }
    List<Map<String, Object>> achievements(UUID town);
    boolean unlocked(UUID town, String achievement);
    String title(UUID town);
    double bonus(UUID town, String effect);
}
