package ru.neverland.reputation;

import ru.neverland.reputation.model.RelationKey;
import ru.neverland.reputation.model.ReputationScope;

import java.util.UUID;

public final class RelationKeySmoke {
    public static void main(String[] args) {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001"); UUID high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        if (RelationKey.of(ReputationScope.PLAYER, low, high).equals(RelationKey.of(ReputationScope.PLAYER, high, low))) throw new AssertionError("Player relation must be directional");
        if (!RelationKey.of(ReputationScope.TOWN, low, high).equals(RelationKey.of(ReputationScope.TOWN, high, low))) throw new AssertionError("Town relation must be symmetric");
        if (!RelationKey.of(ReputationScope.NATION, low, high).equals(RelationKey.of(ReputationScope.NATION, high, low))) throw new AssertionError("Nation relation must be symmetric");
    }
}
