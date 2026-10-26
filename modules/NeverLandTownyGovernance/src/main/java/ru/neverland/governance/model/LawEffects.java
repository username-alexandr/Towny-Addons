package ru.neverland.governance.model;

public record LawEffects(
        Double taxAmount,
        Boolean taxPercentage,
        double constructionCostMultiplier,
        double ideologyCostMultiplier,
        double ideologyExperienceMultiplier,
        int bonusClaimBlocks
) {
    public static LawEffects empty() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return new LawEffects(null, null, 1.0, 1.0, 1.0, 0); } }
    public boolean changesTax() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return taxAmount != null && taxPercentage != null; } }
}
