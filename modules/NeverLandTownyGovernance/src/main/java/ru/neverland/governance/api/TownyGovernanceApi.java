package ru.neverland.governance.api;

import java.util.UUID;

public interface TownyGovernanceApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("constructionCostMultiplier", "hasLaw", "ideologyCostMultiplier", "ideologyExperienceMultiplier", "snapshot"); }
    GovernanceSnapshot snapshot(UUID townId);
    boolean hasLaw(UUID townId, String lawId);
    double constructionCostMultiplier(UUID townId);
    double ideologyCostMultiplier(UUID townId);
    double ideologyExperienceMultiplier(UUID townId);
}
