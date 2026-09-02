package ru.neverland.townybuilds.construction;

/** Проверяет опору, которая появится внутри того же активируемого чертежа. */
public final class FoundationSupportPolicy {
    private FoundationSupportPolicy() {
    }

    public static boolean hasPlannedSupport(BlueprintPlan plan, BlockOffset foundation, int fromStage) {
        if (plan == null || foundation == null || foundation.y() != 0) return false;
        BlueprintBlock support = plan.blocks().get(new BlockOffset(
                foundation.x(), foundation.y() - 1, foundation.z()));
        return support != null && support.stage() >= fromStage && support.stage() <= plan.level();
    }
}
