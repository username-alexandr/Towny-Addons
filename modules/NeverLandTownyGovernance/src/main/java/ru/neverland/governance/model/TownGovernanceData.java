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

    public TownGovernanceData(UUID townId, String townName) { this.townId = townId; this.townName = townName; }
    public UUID townId() { return townId; }
    public String townName() { return townName; }
    public void townName(String townName) { this.townName = townName; }
    public Map<String, ActiveLaw> activeLaws() { return activeLaws; }
    public Map<String, List<OfficeHolder>> offices() { return offices; }
    public List<HistoryEntry> history() { return history; }
    public Double baselineTax() { return baselineTax; }
    public Boolean baselineTaxPercentage() { return baselineTaxPercentage; }
    public void baselineTax(Double amount, Boolean percentage) { baselineTax = amount; baselineTaxPercentage = percentage; }
    public void clearBaselineTax() { baselineTax = null; baselineTaxPercentage = null; }
    public long lastProposalAt() { return lastProposalAt; }
    public void lastProposalAt(long value) { lastProposalAt = value; }
}
