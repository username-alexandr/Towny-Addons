package ru.neverland.governance.api;

import java.util.UUID;

public interface TownyGovernanceApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("constructionCostMultiplier", "hasLaw", "ideologyCostMultiplier", "ideologyExperienceMultiplier", "snapshot", "officeCatalog", "officeHolders", "electionReceipt", "applyElection"); }
    java.util.Map<String, Integer> officeCatalog();
    java.util.Map<String, java.util.List<UUID>> officeHolders(UUID townId);
    String electionReceipt(UUID townId);
    /** Atomic, idempotent replacement of elected offices. Main server thread only. */
    void applyElection(UUID townId, UUID electionId, java.util.Map<String, java.util.List<UUID>> winners);
    GovernanceSnapshot snapshot(UUID townId);
    boolean hasLaw(UUID townId, String lawId);
    double constructionCostMultiplier(UUID townId);
    double ideologyCostMultiplier(UUID townId);
    double ideologyExperienceMultiplier(UUID townId);
}
