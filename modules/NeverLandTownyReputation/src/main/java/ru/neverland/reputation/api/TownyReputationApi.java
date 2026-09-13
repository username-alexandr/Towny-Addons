package ru.neverland.reputation.api;

import ru.neverland.reputation.model.ReputationScope;
import java.util.UUID;

public interface TownyReputationApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("change", "featureUnlocked", "score", "set", "snapshot", "healthy", "profile", "tradeFeeMultiplier", "recordOutcome"); }
    /** Additive profile contract. Old pairwise relationships retain their original meaning. */
    default boolean healthy() { return false; }
    default java.util.Map<String,Object> profile(String scope, UUID subject) { throw new UnsupportedOperationException("profiles"); }
    default double tradeFeeMultiplier(UUID town) { throw new UnsupportedOperationException("profiles"); }
    /** Returns APPLIED or DUPLICATE only after durable acceptance; conflicting receipt IDs fail. */
    default String recordOutcome(String scope, UUID subject, String rule, String receipt, long at, String context) { throw new UnsupportedOperationException("profiles"); }
    ReputationSnapshot snapshot(ReputationScope scope, UUID first, UUID second);
    int score(ReputationScope scope, UUID first, UUID second);
    boolean featureUnlocked(String feature, ReputationScope scope, UUID first, UUID second);
    ReputationChangeResult change(ReputationScope scope, UUID first, String firstName, UUID second, String secondName,
                                  int delta, String source, String reason, String uniqueKey, UUID actorId, String actorName);
    ReputationChangeResult set(ReputationScope scope, UUID first, String firstName, UUID second, String secondName,
                               int score, String source, String reason, String uniqueKey, UUID actorId, String actorName);
}
