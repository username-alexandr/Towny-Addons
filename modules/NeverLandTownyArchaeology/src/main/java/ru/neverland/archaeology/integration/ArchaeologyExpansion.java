package ru.neverland.archaeology.integration;

import com.palmergames.bukkit.towny.object.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.archaeology.TownyArchaeology;
import ru.neverland.archaeology.api.MuseumSnapshot;
import ru.neverland.archaeology.model.PlayerJournal;
import ru.neverland.archaeology.service.ArchaeologyRegistry;
import ru.neverland.archaeology.service.ArchaeologyRepository;
import ru.neverland.archaeology.service.MuseumService;

import java.util.Locale;

public final class ArchaeologyExpansion extends PlaceholderExpansion {
    private final TownyArchaeology plugin; private final TownyHook towny; private final ArchaeologyRegistry registry; private final ArchaeologyRepository repository; private final MuseumService museums;
    public ArchaeologyExpansion(TownyArchaeology plugin, TownyHook towny, ArchaeologyRegistry registry, ArchaeologyRepository repository, MuseumService museums) { this.plugin = plugin; this.towny = towny; this.registry = registry; this.repository = repository; this.museums = museums; }
    @Override public @NotNull String getIdentifier() { return "townyarchaeology"; } @Override public @NotNull String getAuthor() { return "Alexander Sokolov"; } @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); } @Override public boolean persist() { return true; }
    @Override public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) { if (!(offline instanceof Player player)) return ""; String key = params.toLowerCase(Locale.ROOT); PlayerJournal journal = repository.journal(player.getUniqueId(), player.getName()); if (key.equals("player_found")) return String.valueOf(journal.totalFound()); if (key.equals("player_unique")) return String.valueOf(journal.discoveries().size()); Town town = towny.town(player); if (town == null) return ""; MuseumSnapshot museum = museums.snapshot(town.getUUID()); if (key.equals("town_points")) return String.valueOf(museum.points()); if (key.equals("town_unique")) return String.valueOf(museum.donated().size()); if (key.equals("town_available")) return String.valueOf(museum.available().values().stream().mapToInt(Integer::intValue).sum()); if (key.equals("town_collections")) return String.valueOf(museum.completedCollections().size()); if (key.startsWith("artifact_")) return String.valueOf(museums.available(town.getUUID(), key.substring(9))); if (key.startsWith("wonder_ready_")) return String.valueOf(museums.missing(town.getUUID(), key.substring(13)).isEmpty() && !registry.wonderRequirements(key.substring(13)).isEmpty()); return null; }
}
