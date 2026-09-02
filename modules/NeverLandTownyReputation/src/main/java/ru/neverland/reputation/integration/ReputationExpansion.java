package ru.neverland.reputation.integration;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.reputation.TownyReputation;
import ru.neverland.reputation.model.ReputationScope;
import ru.neverland.reputation.service.ReputationService;
import ru.neverland.reputation.util.ColorUtil;

import java.util.Locale;
import java.util.UUID;

public final class ReputationExpansion extends PlaceholderExpansion {
    private final TownyReputation plugin; private final TownyHook towny; private final ReputationService reputation;
    public ReputationExpansion(TownyReputation plugin, TownyHook towny, ReputationService reputation) { this.plugin = plugin; this.towny = towny; this.reputation = reputation; }
    @Override public @NotNull String getIdentifier() { return "townyreputation"; }
    @Override public @NotNull String getAuthor() { return "Alexander Sokolov"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (!(offline instanceof Player player)) return ""; String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("player_relations")) return String.valueOf(reputation.involving(ReputationScope.PLAYER, player.getUniqueId()).size());
        if (key.equals("town_relations")) { Town town = towny.town(player); return town == null ? "0" : String.valueOf(reputation.involving(ReputationScope.TOWN, town.getUUID()).size()); }
        if (key.equals("nation_relations")) { Nation nation = towny.nation(player); return nation == null ? "0" : String.valueOf(reputation.involving(ReputationScope.NATION, nation.getUUID()).size()); }
        String[] prefixes = {"player_score_", "player_tier_", "town_score_", "town_tier_", "nation_score_", "nation_tier_", "player_feature_", "town_feature_", "nation_feature_"};
        for (String prefix : prefixes) if (key.startsWith(prefix)) return resolve(player, prefix, params.substring(prefix.length())); return null;
    }
    private String resolve(Player player, String prefix, String tail) {
        ReputationScope scope = prefix.startsWith("player") ? ReputationScope.PLAYER : prefix.startsWith("town") ? ReputationScope.TOWN : ReputationScope.NATION;
        Side own = own(player, scope); if (own == null) return "";
        if (prefix.contains("feature")) {
            String lower = tail.toLowerCase(Locale.ROOT);
            for (String feature : reputation.featureIds().stream().sorted((left, right) -> Integer.compare(right.length(), left.length())).toList()) {
                String marker = feature + "_"; if (!lower.startsWith(marker)) continue; Side target = target(scope, tail.substring(marker.length()));
                return target == null ? "false" : String.valueOf(reputation.featureUnlocked(feature, scope, own.id(), target.id()));
            }
            return "false";
        }
        Side target = target(scope, tail); if (target == null || target.id().equals(own.id())) return ""; int score = reputation.score(scope, own.id(), target.id());
        return prefix.contains("tier") ? ColorUtil.color(reputation.tier(score).name()) : String.valueOf(score);
    }
    private Side own(Player player, ReputationScope scope) { if (scope == ReputationScope.PLAYER) return new Side(player.getUniqueId()); if (scope == ReputationScope.TOWN) { Town town = towny.town(player); return town == null ? null : new Side(town.getUUID()); } Nation nation = towny.nation(player); return nation == null ? null : new Side(nation.getUUID()); }
    private Side target(ReputationScope scope, String name) { if (scope == ReputationScope.PLAYER) { Resident resident = towny.resident(name); return resident == null ? null : new Side(resident.getUUID()); } if (scope == ReputationScope.TOWN) { Town town = towny.town(name); return town == null ? null : new Side(town.getUUID()); } Nation nation = towny.nation(name); return nation == null ? null : new Side(nation.getUUID()); }
    private record Side(UUID id) { }
}
