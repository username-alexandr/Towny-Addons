package ru.neverland.townybuilds.construction;

/** Отделяет физические стадии чертежа от последующих логических улучшений проекта. */
public final class ConstructionStagePolicy {
    private ConstructionStagePolicy() {
    }

    public static boolean requiresConstruction(int maximumPhysicalStage, int targetLevel) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return maximumPhysicalStage > 0 && targetLevel > 0 && targetLevel <= maximumPhysicalStage;
    } }
}
