package ru.neverland.governance.model;

public record LawEffects(
        Double taxAmount,
        Boolean taxPercentage,
        double constructionCostMultiplier,
        double ideologyCostMultiplier,
        double ideologyExperienceMultiplier,
        int bonusClaimBlocks
) {
    public static LawEffects empty() { synchronized (ru.neverland.core.SuiteState.lock()) { return new LawEffects(null, null, 1.0, 1.0, 1.0, 0); } }
    public boolean changesTax() { synchronized (ru.neverland.core.SuiteState.lock()) { return taxAmount != null && taxPercentage != null; } }
}
