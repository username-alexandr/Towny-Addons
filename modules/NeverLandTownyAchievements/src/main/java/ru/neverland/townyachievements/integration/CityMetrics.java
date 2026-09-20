package ru.neverland.townyachievements.integration;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import ru.neverland.core.ApiServices;
import ru.neverland.townyachievements.model.Achievement;
import java.util.*;

/** Missing or paused providers are unavailable observations, never zero measurements. */
public class CityMetrics {
    public record Observation(boolean ready, long value, String detail) {
        public Observation { if (value < 0) throw new IllegalArgumentException("Отрицательное значение"); }
        public static Observation waiting(String detail) { return new Observation(false, 0, detail); }
    }
    public Observation observe(Town town, Achievement achievement) {
        try {
            UUID id = town.getUUID(); long value;
            switch (achievement.kind()) {
                case BALANCE -> {
                    if (!TownyEconomyHandler.isActive()) return Observation.waiting("Экономика временно недоступна");
                    double balance = town.getAccount().getHoldingBalance(true);
                    if (!Double.isFinite(balance)) return Observation.waiting("Баланс не подтверждён");
                    value = (long) Math.max(0, Math.min(achievement.target(), balance));
                }
                case POPULATION -> {
                    var snapshot = (Optional<?>) ApiServices.call("NeverLandTownyPopulation", "ru.neverland.townypopulation.api.TownyPopulationApi", "population", new Class<?>[]{UUID.class}, id);
                    if (snapshot.isEmpty()) return Observation.waiting("Ожидается расчёт населения");
                    Object p = snapshot.get();
                    if (!id.equals(read(p, "townId")) || Boolean.TRUE.equals(read(p, "paused"))) return Observation.waiting("Расчёт населения приостановлен");
                    value = ((Number) read(p, "population")).longValue();
                }
                case RAID_VICTORIES -> value = ((Number) ApiServices.call("NeverLandTownyEvents", "ru.neverland.mintevents.api.MintTownyEventsApi", "raidVictories", new Class<?>[]{UUID.class}, id)).longValue();
                case ALL_BUILDINGS, FIRST_WONDER -> {
                    var c = ApiServices.require("NeverLandTownyBuilds", "ru.neverland.townybuilds.api.TownyBuildsApi", "projectLevel", "supportedProjects");
                    var supported = (Set<?>) c.invoke("supportedProjects", new Class<?>[]{});
                    if (!supported.containsAll(achievement.projects())) return Observation.waiting("Не все постройки доступны в установленном Builds");
                    value = 0; int minimum = achievement.kind() == Achievement.Kind.ALL_BUILDINGS ? 5 : 1;
                    for (String project : achievement.projects()) {
                        int level = ((Number) c.invoke("projectLevel", new Class<?>[]{UUID.class, String.class}, id, project)).intValue();
                        if (level >= minimum) value++;
                    }
                }
                default -> throw new IllegalArgumentException("Неизвестный показатель");
            }
            return new Observation(true, value, "Сейчас: " + Math.min(value, achievement.target()) + "/" + achievement.target());
        } catch (Exception | LinkageError ex) { return Observation.waiting("Ожидается поставщик данных: " + ex.getClass().getSimpleName()); }
    }
    private static Object read(Object target, String method) throws Exception { return target.getClass().getMethod(method).invoke(target); }
}
