package ru.neverland.governance.model;

public record LawEffects(
        Double taxAmount,
        Boolean taxPercentage,
        double constructionCostMultiplier,
        double ideologyCostMultiplier,
        double ideologyExperienceMultiplier,
        int bonusClaimBlocks
) {
    public static LawEffects empty() { return new LawEffects(null, null, 1.0, 1.0, 1.0, 0); }
    public boolean changesTax() { return taxAmount != null && taxPercentage != null; }
}
