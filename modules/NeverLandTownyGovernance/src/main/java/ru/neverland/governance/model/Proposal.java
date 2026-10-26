package ru.neverland.governance.model;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Proposal {
    private final UUID id;
    private final UUID townId;
    private final String townName;
    private final String lawId;
    private final ProposalAction action;
    private final UUID proposerId;
    private final String proposerName;
    private final long createdAt;
    private ru.neverland.core.ActivityTimer timer;
    private ProposalStatus status;
    private final Map<UUID, VoteChoice> votes = new LinkedHashMap<>();

    public Proposal(UUID id, UUID townId, String townName, String lawId, ProposalAction action,
                    UUID proposerId, String proposerName, long createdAt, long endsAt, ProposalStatus status) {
        this.id = id; this.townId = townId; this.townName = townName; this.lawId = lawId;
        this.action = action; this.proposerId = proposerId; this.proposerName = proposerName;
        this.createdAt = createdAt; this.timer=ru.neverland.core.ActivityTimer.running(createdAt,endsAt); this.status = status;
    }

    public UUID id() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return id; } }
    public String shortId() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return id.toString().substring(0, 8); } }
    public UUID townId() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return townId; } }
    public String townName() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return townName; } }
    public String lawId() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return lawId; } }
    public ProposalAction action() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return action; } }
    public UUID proposerId() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return proposerId; } }
    public String proposerName() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return proposerName; } }
    public long createdAt() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return createdAt; } }
    public long endsAt() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return timer.deadline(); } }
    public ProposalStatus status() { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return status; } }
    public void status(ProposalStatus status) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { this.status = status; } }
    public Map<UUID, VoteChoice> votes() { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) { return Map.copyOf(votes); } }
    public VoteChoice voteOf(UUID player) { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) { return votes.get(player); } }
    public void vote(UUID player, VoteChoice choice) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) { votes.put(player, choice); } }
    public void loadVotes(Map<UUID, VoteChoice> loaded) { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) { votes.clear(); votes.putAll(loaded); } }

    public Map<VoteChoice, Integer> counts() { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
        Map<VoteChoice, Integer> result = new EnumMap<>(VoteChoice.class);
        for (VoteChoice choice : VoteChoice.values()) result.put(choice, 0);
        votes.values().forEach(choice -> result.merge(choice, 1, Integer::sum));
        return result;
    } }
    public ru.neverland.core.ActivityTimer timer(){ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {return timer;} }
    public void timer(ru.neverland.core.ActivityTimer value){ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {timer=java.util.Objects.requireNonNull(value);} }
}
