package ru.neverland.reputation.model;

import java.util.Set;

public record FeatureDefinition(String id, Set<ReputationScope> scopes, int minimumScore) {
    public boolean supports(ReputationScope scope) { synchronized (ru.neverland.core.SuiteState.lock()) { return scopes.contains(scope); } }
}
