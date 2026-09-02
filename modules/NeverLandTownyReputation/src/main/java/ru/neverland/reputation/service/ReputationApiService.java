package ru.neverland.reputation.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.reputation.api.ReputationChangeResult;
import ru.neverland.reputation.api.ReputationSnapshot;
import ru.neverland.reputation.api.TownyReputationApi;
import ru.neverland.reputation.model.ReputationScope;

import java.util.UUID;
import java.util.concurrent.Callable;

public final class ReputationApiService implements TownyReputationApi {
    private final JavaPlugin plugin; private final ReputationService service;
    public ReputationApiService(JavaPlugin plugin, ReputationService service) { this.plugin = plugin; this.service = service; }
    @Override public ReputationSnapshot snapshot(ReputationScope scope, UUID first, UUID second) { return sync(() -> service.snapshot(scope, first, second)); }
    @Override public int score(ReputationScope scope, UUID first, UUID second) { return sync(() -> service.score(scope, first, second)); }
    @Override public boolean featureUnlocked(String feature, ReputationScope scope, UUID first, UUID second) { return sync(() -> service.featureUnlocked(feature, scope, first, second)); }
    @Override public ReputationChangeResult change(ReputationScope scope, UUID first, String firstName, UUID second, String secondName, int delta, String source, String reason, String uniqueKey, UUID actorId, String actorName) { return sync(() -> service.change(scope, first, firstName, second, secondName, delta, source, reason, uniqueKey, actorId, actorName)); }
    @Override public ReputationChangeResult set(ReputationScope scope, UUID first, String firstName, UUID second, String secondName, int score, String source, String reason, String uniqueKey, UUID actorId, String actorName) { return sync(() -> service.set(scope, first, firstName, second, secondName, score, source, reason, uniqueKey, actorId, actorName)); }
    private <T> T sync(Callable<T> operation) {
        try { if (Bukkit.isPrimaryThread()) return operation.call(); return Bukkit.getScheduler().callSyncMethod(plugin, operation).get(); }
        catch (Exception exception) { throw new IllegalStateException("NeverLandTownyReputation API operation failed", exception); }
    }
}
