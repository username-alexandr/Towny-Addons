package ru.neverland.townyachievements.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.neverland.townyachievements.service.AchievementService;

public final class AchievementsExpansion extends PlaceholderExpansion {
    private final AchievementService service; private final String version;
    public AchievementsExpansion(AchievementService service, String version) { this.service = service; this.version = version; }
    @Override public String getIdentifier() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return "nltachievements"; } }
    @Override public String getAuthor() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return "Alexander Sokolov"; } }
    @Override public String getVersion() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return version; } }
    @Override public boolean persist() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return true; } }
    @Override public String onRequest(OfflinePlayer player, String parameter) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        if (!parameter.equals("title") && !parameter.equals("count")) return null;
        return player == null ? "" : service.placeholder(player.getUniqueId(), parameter);
    } }
}
