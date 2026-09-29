package ru.neverland.townybuilds.construction;

public record ConstructionProgress(boolean active, int targetStage, int placed, int total, String stageName) {
    public int percent() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return total <= 0 ? 100 : Math.min(100, (int) Math.floor(placed * 100.0 / total));
    } }
}
