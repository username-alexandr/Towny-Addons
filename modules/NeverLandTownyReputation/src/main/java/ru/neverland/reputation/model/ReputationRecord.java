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
    public RelationKey key() { return key; }
    public String firstName() { return firstName; }
    public String secondName() { return secondName; }
    public void names(String first, String second) { if (first != null && !first.isBlank()) firstName = first; if (second != null && !second.isBlank()) secondName = second; }
    public int score() { return score; }
    public void score(int value) { score = value; }
    public long lastChanged() { return lastChanged; }
    public void lastChanged(long value) { lastChanged = value; }
    public List<ReputationHistory> history() { return history; }
    public String nameOf(java.util.UUID id) { return key.first().equals(id) ? firstName : secondName; }
}
