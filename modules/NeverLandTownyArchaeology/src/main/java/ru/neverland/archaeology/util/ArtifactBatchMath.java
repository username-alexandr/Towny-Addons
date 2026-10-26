package ru.neverland.archaeology.util;

public final class ArtifactBatchMath {
    private ArtifactBatchMath() { }

    public static int remaining(int batchSize, int accepted) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        int capacity = Math.max(1, batchSize);
        if (accepted >= capacity) return 0;
        return capacity - Math.max(0, accepted);
    } }

    public static int acceptedNow(int batchSize, int accepted, int stackAmount) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return Math.min(Math.max(0, stackAmount), remaining(batchSize, accepted));
    } }
}
