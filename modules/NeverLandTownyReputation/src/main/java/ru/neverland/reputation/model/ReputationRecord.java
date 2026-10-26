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
    public RelationKey key() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return key; } }
    public String firstName() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return firstName; } }
    public String secondName() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return secondName; } }
    public void names(String first, String second) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { if (first != null && !first.isBlank()) firstName = first; if (second != null && !second.isBlank()) secondName = second; } }
    public int score() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return score; } }
    public void score(int value) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { score = value; } }
    public long lastChanged() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return lastChanged; } }
    public void lastChanged(long value) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { lastChanged = value; } }
    public List<ReputationHistory> history() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return history; } }
    public String nameOf(java.util.UUID id) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return key.first().equals(id) ? firstName : secondName; } }
}
