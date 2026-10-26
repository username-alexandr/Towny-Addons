package ru.neverland.reputation.model;

import java.util.Set;

public record FeatureDefinition(String id, Set<ReputationScope> scopes, int minimumScore) {
    public boolean supports(ReputationScope scope) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return scopes.contains(scope); } }
}
