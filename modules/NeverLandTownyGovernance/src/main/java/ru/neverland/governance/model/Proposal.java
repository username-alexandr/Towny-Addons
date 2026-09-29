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

    public UUID id() { synchronized (ru.neverland.core.SuiteState.lock()) { return id; } }
    public String shortId() { synchronized (ru.neverland.core.SuiteState.lock()) { return id.toString().substring(0, 8); } }
    public UUID townId() { synchronized (ru.neverland.core.SuiteState.lock()) { return townId; } }
    public String townName() { synchronized (ru.neverland.core.SuiteState.lock()) { return townName; } }
    public String lawId() { synchronized (ru.neverland.core.SuiteState.lock()) { return lawId; } }
    public ProposalAction action() { synchronized (ru.neverland.core.SuiteState.lock()) { return action; } }
    public UUID proposerId() { synchronized (ru.neverland.core.SuiteState.lock()) { return proposerId; } }
    public String proposerName() { synchronized (ru.neverland.core.SuiteState.lock()) { return proposerName; } }
    public long createdAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return createdAt; } }
    public long endsAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return timer.deadline(); } }
    public ProposalStatus status() { synchronized (ru.neverland.core.SuiteState.lock()) { return status; } }
    public void status(ProposalStatus status) { synchronized (ru.neverland.core.SuiteState.lock()) { this.status = status; } }
    public Map<UUID, VoteChoice> votes() { synchronized (ru.neverland.core.SuiteState.lock()) { return Map.copyOf(votes); } }
    public VoteChoice voteOf(UUID player) { synchronized (ru.neverland.core.SuiteState.lock()) { return votes.get(player); } }
    public void vote(UUID player, VoteChoice choice) { synchronized (ru.neverland.core.SuiteState.lock()) { votes.put(player, choice); } }
    public void loadVotes(Map<UUID, VoteChoice> loaded) { synchronized (ru.neverland.core.SuiteState.lock()) { votes.clear(); votes.putAll(loaded); } }

    public Map<VoteChoice, Integer> counts() { synchronized (ru.neverland.core.SuiteState.lock()) {
        Map<VoteChoice, Integer> result = new EnumMap<>(VoteChoice.class);
        for (VoteChoice choice : VoteChoice.values()) result.put(choice, 0);
        votes.values().forEach(choice -> result.merge(choice, 1, Integer::sum));
        return result;
    } }
    public ru.neverland.core.ActivityTimer timer(){ synchronized (ru.neverland.core.SuiteState.lock()) {return timer;} }
    public void timer(ru.neverland.core.ActivityTimer value){ synchronized (ru.neverland.core.SuiteState.lock()) {timer=java.util.Objects.requireNonNull(value);} }
}
