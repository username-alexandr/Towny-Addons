package ru.neverland.reputation.api;

import ru.neverland.reputation.model.ReputationScope;
import java.util.Set;
import java.util.UUID;

public record ReputationSnapshot(
        ReputationScope scope,
        UUID first,
        UUID second,
        int score,
        String tierId,
        String tierName,
        Set<String> privileges,
        double tradeDiscountPercent,
        double rewardMultiplier
) { }
