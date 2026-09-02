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
    private final long endsAt;
    private ProposalStatus status;
    private final Map<UUID, VoteChoice> votes = new LinkedHashMap<>();

    public Proposal(UUID id, UUID townId, String townName, String lawId, ProposalAction action,
                    UUID proposerId, String proposerName, long createdAt, long endsAt, ProposalStatus status) {
        this.id = id; this.townId = townId; this.townName = townName; this.lawId = lawId;
        this.action = action; this.proposerId = proposerId; this.proposerName = proposerName;
        this.createdAt = createdAt; this.endsAt = endsAt; this.status = status;
    }

    public UUID id() { return id; }
    public String shortId() { return id.toString().substring(0, 8); }
    public UUID townId() { return townId; }
    public String townName() { return townName; }
    public String lawId() { return lawId; }
    public ProposalAction action() { return action; }
    public UUID proposerId() { return proposerId; }
    public String proposerName() { return proposerName; }
    public long createdAt() { return createdAt; }
    public long endsAt() { return endsAt; }
    public ProposalStatus status() { return status; }
    public void status(ProposalStatus status) { this.status = status; }
    public Map<UUID, VoteChoice> votes() { return Map.copyOf(votes); }
    public VoteChoice voteOf(UUID player) { return votes.get(player); }
    public void vote(UUID player, VoteChoice choice) { votes.put(player, choice); }
    public void loadVotes(Map<UUID, VoteChoice> loaded) { votes.clear(); votes.putAll(loaded); }

    public Map<VoteChoice, Integer> counts() {
        Map<VoteChoice, Integer> result = new EnumMap<>(VoteChoice.class);
        for (VoteChoice choice : VoteChoice.values()) result.put(choice, 0);
        votes.values().forEach(choice -> result.merge(choice, 1, Integer::sum));
        return result;
    }
}
