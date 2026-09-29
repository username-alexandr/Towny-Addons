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
    public String electionReceipt() { synchronized (ru.neverland.core.SuiteState.lock()) { return electionReceipt; } }
    public void electionReceipt(String value) { synchronized (ru.neverland.core.SuiteState.lock()) { electionReceipt = value; } }
    public java.util.Set<String> electedOffices() { synchronized (ru.neverland.core.SuiteState.lock()) { return electedOffices; } }

    public TownGovernanceData(UUID townId, String townName) { this.townId = townId; this.townName = townName; }
    public UUID townId() { synchronized (ru.neverland.core.SuiteState.lock()) { return townId; } }
    public String townName() { synchronized (ru.neverland.core.SuiteState.lock()) { return townName; } }
    public void townName(String townName) { synchronized (ru.neverland.core.SuiteState.lock()) { this.townName = townName; } }
    public Map<String, ActiveLaw> activeLaws() { synchronized (ru.neverland.core.SuiteState.lock()) { return activeLaws; } }
    public Map<String, List<OfficeHolder>> offices() { synchronized (ru.neverland.core.SuiteState.lock()) { return offices; } }
    public List<HistoryEntry> history() { synchronized (ru.neverland.core.SuiteState.lock()) { return history; } }
    public Double baselineTax() { synchronized (ru.neverland.core.SuiteState.lock()) { return baselineTax; } }
    public Boolean baselineTaxPercentage() { synchronized (ru.neverland.core.SuiteState.lock()) { return baselineTaxPercentage; } }
    public void baselineTax(Double amount, Boolean percentage) { synchronized (ru.neverland.core.SuiteState.lock()) { baselineTax = amount; baselineTaxPercentage = percentage; } }
    public void clearBaselineTax() { synchronized (ru.neverland.core.SuiteState.lock()) { baselineTax = null; baselineTaxPercentage = null; } }
    public long lastProposalAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return lastProposalAt; } }
    public void lastProposalAt(long value) { synchronized (ru.neverland.core.SuiteState.lock()) { lastProposalAt = value; } }
}
