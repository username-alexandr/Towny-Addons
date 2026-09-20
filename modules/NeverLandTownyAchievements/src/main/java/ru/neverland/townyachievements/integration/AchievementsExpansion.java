package ru.neverland.townyachievements.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.neverland.townyachievements.service.AchievementService;

public final class AchievementsExpansion extends PlaceholderExpansion {
    private final AchievementService service; private final String version;
    public AchievementsExpansion(AchievementService service, String version) { this.service = service; this.version = version; }
    @Override public String getIdentifier() { return "nltachievements"; }
    @Override public String getAuthor() { return "Alexander Sokolov"; }
    @Override public String getVersion() { return version; }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, String parameter) {
        if (!parameter.equals("title") && !parameter.equals("count")) return null;
        return player == null ? "" : service.placeholder(player.getUniqueId(), parameter);
    }
}
