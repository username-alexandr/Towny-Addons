package ru.neverland.townypopulation.model;

public record PopulationState(int population, double remainder, int lastChange, long lastCycle) {
    public PopulationState {
        if (population < 0 || population > 1000000 || !Double.isFinite(remainder)
                || Math.abs(remainder) >= 1 || lastCycle < 0)
            throw new IllegalArgumentException("Повреждённая запись населения");
    }
    public PopulationState rebase(long now) { return new PopulationState(population, remainder, lastChange, now); }
}
