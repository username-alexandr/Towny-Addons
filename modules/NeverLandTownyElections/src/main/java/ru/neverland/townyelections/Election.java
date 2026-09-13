package ru.neverland.townyelections;

import java.util.*;

/** Server-independent state. Mutations are made on a copy and published only after durable storage. */
public final class Election {
    public enum Phase { WAITING, NOMINATION, VOTING, APPLYING, REVIEW, COMPLETE, CANCELLED }
    UUID id, town, originalMayor;
    Phase phase = Phase.WAITING;
    long start, nominationEnd, votingEnd, next, interval, votingDuration;
    double quorum;
    String detail = "";
    String governanceBefore = "";
    final Set<UUID> electorate = new LinkedHashSet<>();
    final Map<String, Integer> seats = new LinkedHashMap<>();
    final Map<UUID, String> candidates = new LinkedHashMap<>();
    final Map<String, Map<UUID, List<UUID>>> ballots = new LinkedHashMap<>();
    final Map<String, List<UUID>> winners = new LinkedHashMap<>();
    final Map<String, String> results = new LinkedHashMap<>();
    final List<String> history = new ArrayList<>();
    public Election(UUID town, long next) { this.id = UUID.randomUUID(); this.town = town; this.next = next; }
    public Election copy() {
        var e = new Election(town, next); e.id=id; e.originalMayor=originalMayor; e.phase=phase;
        e.start=start; e.nominationEnd=nominationEnd; e.votingEnd=votingEnd; e.interval=interval; e.votingDuration=votingDuration; e.quorum=quorum; e.detail=detail; e.governanceBefore=governanceBefore;
        e.electorate.addAll(electorate); e.seats.putAll(seats); e.candidates.putAll(candidates);
        ballots.forEach((r, votes) -> { var map = new LinkedHashMap<UUID,List<UUID>>(); votes.forEach((v, choices) -> map.put(v, List.copyOf(choices))); e.ballots.put(r, map); });
        winners.forEach((r, list) -> e.winners.put(r, List.copyOf(list))); e.results.putAll(results); e.history.addAll(history); return e;
    }
    public boolean active() { return phase == Phase.NOMINATION || phase == Phase.VOTING || phase == Phase.APPLYING || phase == Phase.REVIEW; }
    public void nominate(UUID resident, String race, long now) {
        if (phase != Phase.NOMINATION || now >= nominationEnd) throw new IllegalArgumentException("Приём кандидатов закрыт.");
        if (!seats.containsKey(race)) throw new IllegalArgumentException("Неизвестная должность.");
        if (candidates.containsKey(resident)) throw new IllegalArgumentException("Можно выдвинуться только на одну должность. Сначала withdraw.");
        candidates.put(resident, race);
    }
    public void withdraw(UUID resident, long now) {
        if (phase != Phase.NOMINATION || now >= nominationEnd) throw new IllegalArgumentException("Снятие кандидатуры уже закрыто.");
        if (candidates.remove(resident) == null) throw new IllegalArgumentException("Вы не выдвигались.");
    }
    public void vote(UUID voter, String race, List<UUID> choices, long now) {
        if (phase != Phase.VOTING || now >= votingEnd) throw new IllegalArgumentException("Голосование закрыто.");
        if (!electorate.contains(voter)) throw new IllegalArgumentException("Вы не вошли в список избирателей на старте кампании.");
        if (!seats.containsKey(race) || choices.isEmpty() || choices.size() > seats.get(race) || new HashSet<>(choices).size() != choices.size()) throw new IllegalArgumentException("Неверное число выбранных кандидатов.");
        for (UUID candidate : choices) if (!race.equals(candidates.get(candidate))) throw new IllegalArgumentException("Кандидат не участвует в этих выборах.");
        ballots.computeIfAbsent(race, key -> new LinkedHashMap<>()).put(voter, List.copyOf(choices));
    }
    /** Approval voting: up to N distinct candidates for N seats. Ties at the boundary preserve incumbents. */
    public void tally(Set<UUID> eligibleVoters, Set<UUID> eligibleCandidates) {
        winners.clear(); results.clear();
        var voters = new HashSet<>(electorate); voters.retainAll(eligibleVoters);
        for (var race : seats.entrySet()) {
            var scores = new HashMap<UUID,Integer>();
            candidates.forEach((id, office) -> { if (office.equals(race.getKey()) && eligibleCandidates.contains(id)) scores.put(id, 0); });
            int turnout = 0;
            for (var vote : ballots.getOrDefault(race.getKey(), Map.of()).entrySet()) if (voters.contains(vote.getKey())) {
                // A ballot containing only departed/ineligible candidates is invalid, not turnout.
                if (vote.getValue().stream().noneMatch(scores::containsKey)) continue;
                turnout++;
                vote.getValue().forEach(id -> { if (scores.containsKey(id)) scores.compute(id, (k, v) -> v + 1); });
            }
            String reason;
            var ranking = scores.keySet().stream().filter(id -> scores.get(id) > 0)
                    .sorted(Comparator.<UUID>comparingInt(scores::get).reversed().thenComparing(UUID::toString)).toList();
            int count = Math.min(race.getValue(), ranking.size());
            if (voters.isEmpty() || turnout < Math.max(1, (int)Math.ceil(voters.size() * quorum))) reason = "Нет кворума";
            else if (count == 0) reason = "Нет кандидатов с голосами";
            else if (ranking.size() > count && scores.get(ranking.get(count - 1)).equals(scores.get(ranking.get(count)))) reason = "Ничья на границе мест";
            else { winners.put(race.getKey(), List.copyOf(ranking.subList(0, count))); reason = "Избрано: " + count; }
            results.put(race.getKey(), reason + "; явка " + turnout + "/" + voters.size());
        }
    }
    void validate() {
        Objects.requireNonNull(id); Objects.requireNonNull(town); Objects.requireNonNull(phase);
        if (next < 0 || start < 0 || nominationEnd < 0 || votingEnd < 0 || !Double.isFinite(quorum) || quorum < 0 || quorum > 1) throw new IllegalArgumentException("Invalid election time/quorum");
        if (phase != Phase.WAITING && (originalMayor == null || start <= 0 || nominationEnd <= start || votingDuration <= 0 || interval <= 0 || seats.isEmpty())) throw new IllegalArgumentException("Incomplete campaign");
        if (phase == Phase.VOTING && votingEnd <= nominationEnd) throw new IllegalArgumentException("Invalid voting deadline");
        seats.forEach((r, n) -> { if (!r.matches("[a-z][a-z0-9_]{0,39}") || n < 1 || n > 20 || r.equals("mayor") && n != 1) throw new IllegalArgumentException("Invalid seats"); });
        candidates.forEach((id, r) -> { if (!seats.containsKey(r)) throw new IllegalArgumentException("Unknown race"); });
        ballots.forEach((r, votes) -> votes.forEach((v, choices) -> {
            if (!electorate.contains(v) || !seats.containsKey(r) || choices.isEmpty() || choices.size() > seats.get(r) || new HashSet<>(choices).size() != choices.size()
                    || choices.stream().anyMatch(id -> !r.equals(candidates.get(id)))) throw new IllegalArgumentException("Invalid saved ballot");
        }));
        var selected = new HashSet<UUID>();
        winners.forEach((r, ids) -> { if (!seats.containsKey(r) || ids.isEmpty() || ids.size() > seats.get(r)
                || ids.stream().anyMatch(id -> !r.equals(candidates.get(id)) || !selected.add(id))) throw new IllegalArgumentException("Invalid saved winners"); });
        if (!Set.of(Phase.APPLYING, Phase.REVIEW, Phase.COMPLETE).contains(phase) && !winners.isEmpty()) throw new IllegalArgumentException("Premature winners");
    }
}
