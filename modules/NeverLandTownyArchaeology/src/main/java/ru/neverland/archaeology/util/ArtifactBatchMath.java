package ru.neverland.archaeology.util;

public final class ArtifactBatchMath {
    private ArtifactBatchMath() { }

    public static int remaining(int batchSize, int accepted) { synchronized (ru.neverland.core.SuiteState.lock()) {
        int capacity = Math.max(1, batchSize);
        if (accepted >= capacity) return 0;
        return capacity - Math.max(0, accepted);
    } }

    public static int acceptedNow(int batchSize, int accepted, int stackAmount) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return Math.min(Math.max(0, stackAmount), remaining(batchSize, accepted));
    } }
}
