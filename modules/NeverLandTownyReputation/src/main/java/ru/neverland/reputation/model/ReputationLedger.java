package ru.neverland.reputation.model;

import java.util.*;
import ru.neverland.core.ReputationOutcome;

/** Pure immutable transition engine. No Bukkit, money, or file operations. */
public final class ReputationLedger {
    public static final int MIN = -1000, MAX = 1000;
    public record Subject(ReputationScope scope, UUID id) {
        public Subject { Objects.requireNonNull(scope); Objects.requireNonNull(id); }
        public String key() { return scope + ":" + id; }
        public static Subject parse(String key) { var parts = key.split(":", 2); return new Subject(ReputationScope.valueOf(parts[0]), UUID.fromString(parts[1])); }
    }
    public record Entry(long at, ReputationAspect aspect, int delta, int score, String rule, String context) {
        public Entry { Objects.requireNonNull(aspect); if (at < 0 || score < MIN || score > MAX || Math.abs((long)delta) > 2000 || rule == null || context == null) throw new IllegalArgumentException("История репутации повреждена"); }
    }
    public record Profile(int diplomatic, int trade, int military, List<Entry> history) {
        public Profile { history = List.copyOf(history); for (int v : new int[]{diplomatic,trade,military}) if (v < MIN || v > MAX) throw new IllegalArgumentException("Оценка вне диапазона"); }
        public static Profile empty() { return new Profile(0, 0, 0, List.of()); }
        public int score(ReputationAspect a) { return switch (a) { case DIPLOMATIC -> diplomatic; case TRADE -> trade; case MILITARY -> military; }; }
        public Profile changed(ReputationAspect a, int score, Entry entry) {
            var entries = new ArrayList<>(history); entries.add(entry); if (entries.size() > 60) entries.remove(0);
            return new Profile(a == ReputationAspect.DIPLOMATIC ? score : diplomatic, a == ReputationAspect.TRADE ? score : trade, a == ReputationAspect.MILITARY ? score : military, entries);
        }
    }
    public record Receipt(ReputationOutcome outcome, ReputationAspect aspect, int requested, int applied) { }
    public record State(Map<Subject, Profile> profiles, Map<String, Receipt> receipts, Map<String, Integer> daily) {
        public State { profiles = Map.copyOf(profiles); receipts = Map.copyOf(receipts); daily = Map.copyOf(daily); }
        public static State empty() { return new State(Map.of(), Map.of(), Map.of()); }
        public Profile profile(Subject subject) { return profiles.getOrDefault(subject, Profile.empty()); }
    }
    public record Result(State state, String status) { }
    private ReputationLedger() { }
    public static Result apply(State state, ReputationOutcome outcome, ReputationAspect aspect, int points, int positiveDailyCap) {
        Receipt prior = state.receipts().get(outcome.id());
        if (prior != null) {
            // Configured points can change during a delayed acknowledgement; business payload cannot.
            if (!prior.outcome().equals(outcome)) throw new IllegalArgumentException("ID репутации уже принадлежит другому событию");
            return new Result(state, "DUPLICATE");
        }
        Subject subject = new Subject(ReputationScope.valueOf(outcome.scope()), outcome.subject());
        Profile previous = state.profile(subject);
        String dayKey = subject.key() + ":" + aspect + ":" + (outcome.at() / 86_400_000L);
        int used = state.daily().getOrDefault(dayKey, 0);
        long delta = points > 0 ? Math.min(points, Math.max(0L, (long)positiveDailyCap - used)) : points;
        int next = (int)Math.max(MIN, Math.min(MAX, previous.score(aspect) + delta));
        int applied = next - previous.score(aspect);
        var profiles = new LinkedHashMap<>(state.profiles());
        profiles.put(subject, previous.changed(aspect, next, new Entry(outcome.at(), aspect, applied, next, outcome.rule(), outcome.context())));
        var receipts = new LinkedHashMap<>(state.receipts()); receipts.put(outcome.id(), new Receipt(outcome, aspect, points, applied));
        var daily = new LinkedHashMap<>(state.daily()); if (applied > 0) daily.put(dayKey, Math.addExact(used, applied));
        return new Result(new State(profiles, receipts, daily), "APPLIED");
    }
    public static double feeMultiplier(int trade, double maximum, double discount) {
        if (trade < MIN || trade > MAX || !Double.isFinite(maximum) || maximum < 1 || maximum > 5 || !Double.isFinite(discount) || discount < 0 || discount > 0.5) throw new IllegalArgumentException("Некорректные параметры комиссии");
        return trade < 0 ? 1 + (-trade / 1000.0) * (maximum - 1) : 1 - (trade / 1000.0) * discount;
    }
}
