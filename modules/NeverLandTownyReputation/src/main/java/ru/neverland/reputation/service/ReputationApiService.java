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
    @Override public ReputationSnapshot snapshot(ReputationScope scope, UUID first, UUID second) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return sync(() -> service.snapshot(scope, first, second)); } }
    @Override public int score(ReputationScope scope, UUID first, UUID second) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return sync(() -> service.score(scope, first, second)); } }
    @Override public boolean featureUnlocked(String feature, ReputationScope scope, UUID first, UUID second) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return sync(() -> service.featureUnlocked(feature, scope, first, second)); } }
    @Override public ReputationChangeResult change(ReputationScope scope, UUID first, String firstName, UUID second, String secondName, int delta, String source, String reason, String uniqueKey, UUID actorId, String actorName) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return sync(() -> service.change(scope, first, firstName, second, secondName, delta, source, reason, uniqueKey, actorId, actorName)); } }
    @Override public ReputationChangeResult set(ReputationScope scope, UUID first, String firstName, UUID second, String secondName, int score, String source, String reason, String uniqueKey, UUID actorId, String actorName) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return sync(() -> service.set(scope, first, firstName, second, secondName, score, source, reason, uniqueKey, actorId, actorName)); } }
    @Override public java.util.Map<String,Object> explainProfile(String scope,UUID subject,String aspect,int page){try(var guard=ru.neverland.core.SuiteState.guard()){ru.neverland.core.ApiServices.primaryThread();if(!plugin.getConfig().getBoolean("profiles.explanations.enabled",true))return java.util.Map.of("available",false,"reason","Объяснение отключено администрацией");return service.profiles().explanation(scope,subject,aspect,page);}}
    @Override public boolean healthy() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { ru.neverland.core.ApiServices.primaryThread(); return plugin.isEnabled() && service.profiles().healthy(); } }
    @Override public java.util.Map<String,Object> profile(String scope, UUID subject) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { ru.neverland.core.ApiServices.primaryThread(); return service.profiles().snapshot(scope, subject); } }
    @Override public double tradeFeeMultiplier(UUID town) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { ru.neverland.core.ApiServices.primaryThread(); return service.profiles().tradeFeeMultiplier(town); } }
    @Override public String recordOutcome(String scope, UUID subject, String rule, String receipt, long at, String context) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { ru.neverland.core.ApiServices.primaryThread(); return service.profiles().record(scope, subject, rule, receipt, at, context); } }
    private <T> T sync(Callable<T> operation) {
        try { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return operation.call(); } }
        catch (Exception exception) { throw new IllegalStateException("NeverLandTownyReputation API operation failed", exception); }
    }
}
