package ru.neverland.mintcontracts.api;

import java.util.List;
import java.util.UUID;
public interface MintTownyContractsApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("activeContracts", "addProgress", "pendingReward"); }
    List<ContractSnapshot> activeContracts(UUID townId);
    boolean addProgress(UUID contractId, UUID contributorId, int amount, String source);
    double pendingReward(UUID residentId);
}
