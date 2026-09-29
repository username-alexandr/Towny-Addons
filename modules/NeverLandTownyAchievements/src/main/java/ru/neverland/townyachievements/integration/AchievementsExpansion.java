package ru.neverland.townyachievements.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.neverland.townyachievements.service.AchievementService;

public final class AchievementsExpansion extends PlaceholderExpansion {
    private final AchievementService service; private final String version;
    public AchievementsExpansion(AchievementService service, String version) { this.service = service; this.version = version; }
    @Override public String getIdentifier() { synchronized (ru.neverland.core.SuiteState.lock()) { return "nltachievements"; } }
    @Override public String getAuthor() { synchronized (ru.neverland.core.SuiteState.lock()) { return "Alexander Sokolov"; } }
    @Override public String getVersion() { synchronized (ru.neverland.core.SuiteState.lock()) { return version; } }
    @Override public boolean persist() { synchronized (ru.neverland.core.SuiteState.lock()) { return true; } }
    @Override public String onRequest(OfflinePlayer player, String parameter) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (!parameter.equals("title") && !parameter.equals("count")) return null;
        return player == null ? "" : service.placeholder(player.getUniqueId(), parameter);
    } }
}
