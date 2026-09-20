package ru.neverland.townyenvironment.model;

/** No wall-clock deadlines: disabled/offline time never produces ecological debt. */
public record EnvironmentState(double pollution, long cycles, boolean paused) {
    public EnvironmentState {
        if (!Double.isFinite(pollution) || pollution < 0 || pollution > 100 || cycles < 0)
            throw new IllegalArgumentException("Некорректное состояние экологии");
    }
    public static EnvironmentState clean() { return new EnvironmentState(0, 0, false); }
    public EnvironmentState paused(boolean value) { return new EnvironmentState(pollution, cycles, value); }
}
