package ru.neverland.mintcontracts.api;

import java.util.List;
import java.util.UUID;
public interface MintTownyContractsApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("activeContracts", "addProgress", "pendingReward", "companyOffers", "companyActiveCount", "takeCompanyContract", "releaseCompanyContract"); }
    List<ContractSnapshot> activeContracts(UUID townId);
    boolean addProgress(UUID contractId, UUID contributorId, int amount, String source);
    double pendingReward(UUID residentId);
    /** Since Contracts 0.3.4; main-thread operations with live company authority checks. */
    default List<java.util.Map<String,Object>> companyOffers(UUID town) { throw new UnsupportedOperationException("Company contracts unavailable"); }
    default int companyActiveCount(UUID company) { throw new UnsupportedOperationException("Company contracts unavailable"); }
    default boolean takeCompanyContract(org.bukkit.entity.Player player,UUID company,UUID contract) { return false; }
    default boolean releaseCompanyContract(org.bukkit.entity.Player player,UUID company,UUID contract) { return false; }
}
