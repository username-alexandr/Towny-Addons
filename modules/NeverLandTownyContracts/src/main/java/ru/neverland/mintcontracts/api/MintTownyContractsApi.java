package ru.neverland.mintcontracts.api;

import java.util.List;
import java.util.UUID;
public interface MintTownyContractsApi {
    List<ContractSnapshot> activeContracts(UUID townId);
    boolean addProgress(UUID contractId, UUID contributorId, int amount, String source);
    double pendingReward(UUID residentId);
}
