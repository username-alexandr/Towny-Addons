package ru.neverland.governance.service;

import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.governance.api.GovernanceSnapshot;
import ru.neverland.governance.api.TownyGovernanceApi;
import ru.neverland.governance.integration.TownyHook;
import ru.neverland.governance.model.TownGovernanceData;

import java.util.Set;
import java.util.UUID;

public final class GovernanceApiService implements TownyGovernanceApi {
    private final TownyHook towny; private final GovernanceService governance;
    public GovernanceApiService(TownyHook towny, GovernanceService governance) { this.towny = towny; this.governance = governance; }
    @Override public GovernanceSnapshot snapshot(UUID townId) {
        Town town = towny.town(townId); TownGovernanceData data = town == null ? null : governance.data(town);
        return new GovernanceSnapshot(townId, data == null ? Set.of() : Set.copyOf(data.activeLaws().keySet()),
                town == null ? 0 : governance.council(town).size(), town == null ? 0 : governance.open(town).size(),
                governance.constructionCost(townId), governance.ideologyCost(townId), governance.ideologyExperience(townId));
    }
    @Override public boolean hasLaw(UUID townId, String lawId) { return governance.hasLaw(townId, lawId); }
    @Override public java.util.Map<String, Integer> officeCatalog() { return governance.officeCatalog(); }
    @Override public java.util.Map<String, java.util.List<UUID>> officeHolders(UUID town) { return governance.officeHolders(town); }
    @Override public String electionReceipt(UUID town) { return governance.electionReceipt(town); }
    @Override public void applyElection(UUID town, UUID election, java.util.Map<String, java.util.List<UUID>> winners) { governance.applyElection(town, election, winners); }
    @Override public double constructionCostMultiplier(UUID townId) { return governance.constructionCost(townId); }
    @Override public double ideologyCostMultiplier(UUID townId) { return governance.ideologyCost(townId); }
    @Override public double ideologyExperienceMultiplier(UUID townId) { return governance.ideologyExperience(townId); }
}
