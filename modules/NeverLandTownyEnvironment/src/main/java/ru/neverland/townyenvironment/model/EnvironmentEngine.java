package ru.neverland.townyenvironment.model;

import java.util.*;
import ru.neverland.townyenvironment.config.EnvironmentSettings;

public final class EnvironmentEngine {
    private EnvironmentEngine() { }
    public record Pressure(double emissions, double cleaning, Map<String, Double> contributions) {
        public Pressure {
            if (!Double.isFinite(emissions) || !Double.isFinite(cleaning) || emissions < 0 || cleaning < 0)
                throw new IllegalArgumentException("Некорректная экологическая нагрузка");
            contributions = Map.copyOf(contributions);
        }
        public double change() { return emissions - cleaning; }
    }
    public record Effects(double happiness, double agriculture) { }
    public static Pressure pressure(Map<String, Integer> active, EnvironmentSettings settings) {
        double emissions = 0, cleaning = settings.naturalRecovery(); var contributions = new TreeMap<String, Double>();
        for (var entry : active.entrySet()) {
            int level = entry.getValue(); if (level < 0 || level > 5) throw new IllegalArgumentException("Неверный уровень здания");
            var profile = settings.buildings().get(entry.getKey()); if (profile == null || level == 0) continue;
            emissions += profile.emission() * level; cleaning += profile.cleaning() * level;
            contributions.put(entry.getKey(), (profile.emission() - profile.cleaning()) * level);
        }
        return new Pressure(emissions, cleaning, contributions);
    }
    public static EnvironmentState advance(EnvironmentState state, Pressure pressure) {
        return state.paused() ? state : new EnvironmentState(Math.max(0, Math.min(100, state.pollution() + pressure.change())), Math.addExact(state.cycles(), 1), false);
    }
    public static Effects effects(EnvironmentState state, EnvironmentSettings settings, boolean ready) {
        if (!ready || state.paused()) return new Effects(0, 1);
        double severity = Math.max(0, (state.pollution() - settings.threshold()) / (100 - settings.threshold()));
        return new Effects(-settings.maximumHappinessPenalty() * severity, 1 - settings.maximumAgriculturePenalty() * severity);
    }
}
