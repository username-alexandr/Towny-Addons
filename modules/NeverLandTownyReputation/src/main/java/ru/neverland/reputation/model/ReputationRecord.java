package ru.neverland.reputation.model;

import java.util.ArrayList;
import java.util.List;

public final class ReputationRecord {
    private final RelationKey key;
    private String firstName;
    private String secondName;
    private int score;
    private long lastChanged;
    private final List<ReputationHistory> history = new ArrayList<>();
    public ReputationRecord(RelationKey key, String firstName, String secondName) { this.key = key; this.firstName = firstName; this.secondName = secondName; }
    public RelationKey key() { synchronized (ru.neverland.core.SuiteState.lock()) { return key; } }
    public String firstName() { synchronized (ru.neverland.core.SuiteState.lock()) { return firstName; } }
    public String secondName() { synchronized (ru.neverland.core.SuiteState.lock()) { return secondName; } }
    public void names(String first, String second) { synchronized (ru.neverland.core.SuiteState.lock()) { if (first != null && !first.isBlank()) firstName = first; if (second != null && !second.isBlank()) secondName = second; } }
    public int score() { synchronized (ru.neverland.core.SuiteState.lock()) { return score; } }
    public void score(int value) { synchronized (ru.neverland.core.SuiteState.lock()) { score = value; } }
    public long lastChanged() { synchronized (ru.neverland.core.SuiteState.lock()) { return lastChanged; } }
    public void lastChanged(long value) { synchronized (ru.neverland.core.SuiteState.lock()) { lastChanged = value; } }
    public List<ReputationHistory> history() { synchronized (ru.neverland.core.SuiteState.lock()) { return history; } }
    public String nameOf(java.util.UUID id) { synchronized (ru.neverland.core.SuiteState.lock()) { return key.first().equals(id) ? firstName : secondName; } }
}
