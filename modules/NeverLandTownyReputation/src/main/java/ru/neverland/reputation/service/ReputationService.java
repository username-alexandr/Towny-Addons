package ru.neverland.reputation.service;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.reputation.api.ReputationChangeResult;
import ru.neverland.reputation.api.ReputationSnapshot;
import ru.neverland.reputation.event.ReputationChangeEvent;
import ru.neverland.reputation.model.ChangeStatus;
import ru.neverland.reputation.model.FeatureDefinition;
import ru.neverland.reputation.model.RelationKey;
import ru.neverland.reputation.model.ReputationHistory;
import ru.neverland.reputation.model.ReputationRecord;
import ru.neverland.reputation.model.ReputationScope;
import ru.neverland.reputation.model.ReputationTier;
import ru.neverland.reputation.util.ReputationMath;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ReputationService {
    public enum FeedbackStatus { SUCCESS, DISABLED, SELF_TARGET, PLAYTIME, COOLDOWN, DAILY_LIMIT, CHANGE_FAILED }
    public record FeedbackResult(FeedbackStatus status, ReputationChangeResult change, long remainingMillis) { }

    private final JavaPlugin plugin; private final ReputationRegistry registry; private final ReputationRepository repository;
    private final ZoneId zone = ZoneId.systemDefault(); private BukkitTask autosaveTask; private BukkitTask decayTask;
    private int minimum; private int maximum; private int historyLimit;
    public ReputationService(JavaPlugin plugin, ReputationRegistry registry, ReputationRepository repository) {
        this.plugin = plugin; this.registry = registry; this.repository = repository; reloadSettings();
    }
    public void reloadSettings() {
        minimum = plugin.getConfig().getInt("score.minimum", -1000); maximum = plugin.getConfig().getInt("score.maximum", 1000);
        if (minimum > maximum) { int swap = minimum; minimum = maximum; maximum = swap; }
        historyLimit = Math.max(1, plugin.getConfig().getInt("storage.history-per-relation", 30));
    }
    public void start() {
        stopTasks(); reloadSettings();
        long saveTicks = Math.max(20L, plugin.getConfig().getLong("storage.autosave-seconds", 60) * 20L);
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, repository::save, saveTicks, saveTicks);
        long decayTicks = Math.max(1200L, plugin.getConfig().getLong("decay.check-interval-minutes", 30) * 1200L);
        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runDecayIfNeeded, 20L, decayTicks);
    }
    public void shutdown() { stopTasks(); repository.save(); }
    private void stopTasks() { if (autosaveTask != null) autosaveTask.cancel(); if (decayTask != null) decayTask.cancel(); autosaveTask = null; decayTask = null; }

    public int score(ReputationScope scope, UUID first, UUID second) { ReputationRecord record = repository.get(RelationKey.of(scope, first, second)); return record == null ? 0 : record.score(); }
    public ReputationSnapshot snapshot(ReputationScope scope, UUID first, UUID second) {
        int score = score(scope, first, second); ReputationTier tier = registry.tier(score);
        return new ReputationSnapshot(scope, first, second, score, tier.id(), tier.name(), Set.copyOf(tier.privileges()), tier.tradeDiscountPercent(), tier.rewardMultiplier());
    }
    public boolean featureUnlocked(String featureId, ReputationScope scope, UUID first, UUID second) {
        FeatureDefinition feature = registry.feature(featureId); return feature != null && feature.supports(scope) && score(scope, first, second) >= feature.minimumScore();
    }
    public ReputationRecord record(ReputationScope scope, UUID first, UUID second) { return repository.get(RelationKey.of(scope, first, second)); }
    public List<ReputationRecord> involving(ReputationScope scope, UUID id) { return repository.involving(scope, id); }
    public List<ReputationRecord> top(ReputationScope scope, int limit) { return repository.all().stream().filter(record -> record.key().scope() == scope).sorted(Comparator.comparingInt((ReputationRecord record) -> record.score()).reversed()).limit(Math.max(1, limit)).toList(); }
    public ReputationTier tier(int score) { return registry.tier(score); }
    public List<ReputationTier> tiers() { return registry.tiers(); }
    public Set<String> featureIds() { return registry.features().keySet(); }

    public ReputationChangeResult change(ReputationScope scope, UUID first, String firstName, UUID second, String secondName, int delta,
                                         String source, String reason, String uniqueKey, UUID actorId, String actorName) {
        if (scope == null || first == null || second == null || first.equals(second)) return new ReputationChangeResult(ChangeStatus.SELF_TARGET, 0, 0, delta, 0);
        String normalizedSource = normalize(source, "default"); RelationKey key = RelationKey.of(scope, first, second);
        ReputationRecord existing = repository.get(key); int oldScore = existing == null ? 0 : existing.score();
        if (delta == 0) return new ReputationChangeResult(ChangeStatus.NO_CHANGE, oldScore, oldScore, 0, 0);
        String fullUniqueKey = uniqueKey == null || uniqueKey.isBlank() ? "" : normalizedSource + "|" + scope + "|" + key.storageKey() + "|" + uniqueKey;
        if (!fullUniqueKey.isBlank() && repository.processed(fullUniqueKey)) return new ReputationChangeResult(ChangeStatus.DUPLICATE, oldScore, oldScore, delta, 0);
        String day = LocalDate.now(zone).toString(); String usageKey = day + "|" + normalizedSource + "|" + scope + "|" + key.storageKey();
        int cap = plugin.getConfig().getInt("sources." + normalizedSource + ".daily-cap", plugin.getConfig().getInt("sources.default.daily-cap", 100));
        int already = repository.dailyUsage(usageKey); int appliedRequest = delta;
        if (cap >= 0) {
            int remaining = Math.max(0, cap - already); if (remaining == 0) return new ReputationChangeResult(ChangeStatus.RATE_LIMITED, oldScore, oldScore, delta, 0);
            if (Math.abs(appliedRequest) > remaining) appliedRequest = Integer.signum(appliedRequest) * remaining;
        }
        String normalizedFirstName = key.first().equals(first) ? firstName : secondName; String normalizedSecondName = key.second().equals(second) ? secondName : firstName;
        ReputationChangeResult result = apply(key, normalizedFirstName, normalizedSecondName, appliedRequest, normalizedSource, reason, actorId, actorName, true);
        if (result.status() == ChangeStatus.SUCCESS) {
            repository.dailyUsage(usageKey, already + Math.abs(result.appliedDelta())); repository.markProcessed(fullUniqueKey, System.currentTimeMillis());
            if (Math.abs(appliedRequest) < Math.abs(delta)) return new ReputationChangeResult(ChangeStatus.RATE_LIMITED, result.oldScore(), result.newScore(), delta, result.appliedDelta());
        }
        return result;
    }

    public ReputationChangeResult set(ReputationScope scope, UUID first, String firstName, UUID second, String secondName, int desired,
                                      String source, String reason, String uniqueKey, UUID actorId, String actorName) {
        int current = scope == null || first == null || second == null ? 0 : score(scope, first, second);
        return change(scope, first, firstName, second, secondName, ReputationMath.clamp(desired, minimum, maximum) - current, source, reason, uniqueKey, actorId, actorName);
    }

    private ReputationChangeResult apply(RelationKey key, String firstName, String secondName, int delta, String source, String reason, UUID actorId, String actorName, boolean touchActivity) {
        ReputationRecord record = repository.get(key); int oldScore = record == null ? 0 : record.score(); int requestedScore = ReputationMath.clamp(oldScore + delta, minimum, maximum);
        if (requestedScore == oldScore) return new ReputationChangeResult(ChangeStatus.NO_CHANGE, oldScore, oldScore, delta, 0);
        ReputationChangeEvent event = new ReputationChangeEvent(key.scope(), key.first(), key.second(), oldScore, requestedScore, source, reason == null ? "" : reason);
        Bukkit.getPluginManager().callEvent(event); if (event.isCancelled()) return new ReputationChangeResult(ChangeStatus.CANCELLED, oldScore, oldScore, delta, 0);
        if (record == null) record = repository.getOrCreate(key, safeName(firstName, key.first()), safeName(secondName, key.second())); else record.names(firstName, secondName);
        long now = System.currentTimeMillis(); record.score(requestedScore); if (touchActivity) record.lastChanged(now);
        record.history().add(0, new ReputationHistory(now, oldScore, requestedScore, requestedScore - oldScore, source, reason == null ? "" : reason, actorId, safeActor(actorName)));
        while (record.history().size() > historyLimit) record.history().remove(record.history().size() - 1); repository.dirty();
        return new ReputationChangeResult(ChangeStatus.SUCCESS, oldScore, requestedScore, delta, requestedScore - oldScore);
    }

    public FeedbackResult feedback(Player actor, Player target, boolean positive) {
        if (!plugin.getConfig().getBoolean("feedback.enabled", true)) return new FeedbackResult(FeedbackStatus.DISABLED, null, 0);
        if (actor.getUniqueId().equals(target.getUniqueId())) return new FeedbackResult(FeedbackStatus.SELF_TARGET, null, 0);
        int requiredHours = Math.max(0, plugin.getConfig().getInt("feedback.minimum-playtime-hours", 2));
        long playedTicks = actor.getStatistic(Statistic.PLAY_ONE_MINUTE); if (playedTicks < requiredHours * 72_000L) return new FeedbackResult(FeedbackStatus.PLAYTIME, null, 0);
        long now = System.currentTimeMillis(); String pair = actor.getUniqueId() + ">" + target.getUniqueId(); long cooldown = Math.max(0, plugin.getConfig().getLong("feedback.cooldown-hours-per-target", 24)) * 3_600_000L;
        long remaining = repository.feedbackCooldown(pair) + cooldown - now; if (remaining > 0) return new FeedbackResult(FeedbackStatus.COOLDOWN, null, remaining);
        String dayKey = LocalDate.now(zone) + "|" + actor.getUniqueId(); int maximumActions = Math.max(0, plugin.getConfig().getInt("feedback.maximum-actions-per-day", 5)); int used = repository.feedbackDaily(dayKey);
        if (used >= maximumActions) return new FeedbackResult(FeedbackStatus.DAILY_LIMIT, null, 0);
        int delta = plugin.getConfig().getInt(positive ? "feedback.endorse-points" : "feedback.denounce-points", positive ? 5 : -3);
        ReputationChangeResult change = change(ReputationScope.PLAYER, actor.getUniqueId(), actor.getName(), target.getUniqueId(), target.getName(), delta,
                "player_feedback", positive ? "Положительный отзыв игрока" : "Отрицательный отзыв игрока", "feedback:" + pair + ":" + LocalDate.now(zone), actor.getUniqueId(), actor.getName());
        if (change.status() != ChangeStatus.SUCCESS && change.appliedDelta() == 0) return new FeedbackResult(FeedbackStatus.CHANGE_FAILED, change, 0);
        repository.feedbackCooldown(pair, now); repository.feedbackDaily(dayKey, used + 1); return new FeedbackResult(FeedbackStatus.SUCCESS, change, 0);
    }

    public int runDecayNow() { return runDecay(true); }
    private void runDecayIfNeeded() { runDecay(false); }
    private int runDecay(boolean force) {
        if (!plugin.getConfig().getBoolean("decay.enabled", true) && !force) return 0;
        String today = LocalDate.now(zone).toString(); if (!force && today.equals(repository.lastDecay())) return 0;
        int graceDays = Math.max(0, plugin.getConfig().getInt("decay.grace-days", 7)); long cutoff = System.currentTimeMillis() - graceDays * 86_400_000L;
        double percent = Math.max(0, plugin.getConfig().getDouble("decay.percent-per-day", 1)); int minimumStep = Math.max(1, plugin.getConfig().getInt("decay.minimum-step", 1)); int changed = 0;
        for (ReputationRecord record : new ArrayList<>(repository.all())) {
            if (record.score() == 0 || record.lastChanged() > cutoff) continue; int next = ReputationMath.decay(record.score(), percent, minimumStep); int delta = next - record.score();
            ReputationChangeResult result = apply(record.key(), record.firstName(), record.secondName(), delta, "decay", "Ежедневное естественное снижение", null, "Система", false); if (result.status() == ChangeStatus.SUCCESS) changed++;
        }
        repository.lastDecay(today); int days = Math.max(1, plugin.getConfig().getInt("storage.processed-key-days", 30)); int limit = Math.max(1, plugin.getConfig().getInt("storage.processed-key-limit", 5000));
        repository.cleanup(System.currentTimeMillis() - days * 86_400_000L, limit, today); repository.save(); return changed;
    }
    private String normalize(String value, String fallback) { if (value == null || value.isBlank()) return fallback; return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_:-]", "_"); }
    private String safeName(String value, UUID fallback) { return value == null || value.isBlank() ? fallback.toString() : value; }
    private String safeActor(String value) { return value == null || value.isBlank() ? "Система" : value; }
}
