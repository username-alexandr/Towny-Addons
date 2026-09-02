package ru.neverland.governance.api;

import java.util.UUID;

public interface TownyGovernanceApi {
    GovernanceSnapshot snapshot(UUID townId);
    boolean hasLaw(UUID townId, String lawId);
    double constructionCostMultiplier(UUID townId);
    double ideologyCostMultiplier(UUID townId);
    double ideologyExperienceMultiplier(UUID townId);
}
