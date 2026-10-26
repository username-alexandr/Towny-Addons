package ru.neverland.governance.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TownGovernanceData {
    private final UUID townId;
    private String townName;
    private final Map<String, ActiveLaw> activeLaws = new LinkedHashMap<>();
    private final Map<String, List<OfficeHolder>> offices = new LinkedHashMap<>();
    private final List<HistoryEntry> history = new ArrayList<>();
    private Double baselineTax;
    private Boolean baselineTaxPercentage;
    private long lastProposalAt;
    private String electionReceipt = "";
    private final java.util.Set<String> electedOffices = new java.util.LinkedHashSet<>();
    public String electionReceipt() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return electionReceipt; } }
    public void electionReceipt(String value) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { electionReceipt = value; } }
    public java.util.Set<String> electedOffices() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return electedOffices; } }

    public TownGovernanceData(UUID townId, String townName) { this.townId = townId; this.townName = townName; }
    public UUID townId() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return townId; } }
    public String townName() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return townName; } }
    public void townName(String townName) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { this.townName = townName; } }
    public Map<String, ActiveLaw> activeLaws() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return activeLaws; } }
    public Map<String, List<OfficeHolder>> offices() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return offices; } }
    public List<HistoryEntry> history() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return history; } }
    public Double baselineTax() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return baselineTax; } }
    public Boolean baselineTaxPercentage() { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return baselineTaxPercentage; } }
    public void baselineTax(Double amount, Boolean percentage) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { baselineTax = amount; baselineTaxPercentage = percentage; } }
    public void clearBaselineTax() { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) { baselineTax = null; baselineTaxPercentage = null; } }
    public long lastProposalAt() { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) { return lastProposalAt; } }
    public void lastProposalAt(long value) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) { lastProposalAt = value; } }
}
