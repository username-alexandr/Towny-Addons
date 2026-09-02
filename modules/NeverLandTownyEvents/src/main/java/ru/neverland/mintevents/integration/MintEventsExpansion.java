package ru.neverland.mintevents.integration;

import com.palmergames.bukkit.towny.object.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.mintevents.MintTownyEvents;
import ru.neverland.mintevents.api.EventSnapshot;
import ru.neverland.mintevents.service.EventService;
import ru.neverland.mintevents.util.TimeUtil;

import java.util.Optional;

public final class MintEventsExpansion extends PlaceholderExpansion {
    private final MintTownyEvents plugin;
    private final TownyHook towny;
    private final EventService events;

    public MintEventsExpansion(MintTownyEvents plugin, TownyHook towny, EventService events) {
        this.plugin = plugin;
        this.towny = towny;
        this.events = events;
    }

    @Override public @NotNull String getIdentifier() { return "mintevents"; }
    @Override public @NotNull String getAuthor() { return "Alexander Sokolov"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (!(offline instanceof Player player)) return "";
        Town town = towny.town(player);
        if (town == null) return defaultValue(params);
        Optional<EventSnapshot> optional = events.activeEvent(town.getUUID());
        if (params.equalsIgnoreCase("town")) return town.getName();
        if (optional.isEmpty()) return defaultValue(params);
        EventSnapshot snapshot = optional.get();
        return switch (params.toLowerCase()) {
            case "active" -> "true";
            case "id" -> snapshot.eventId();
            case "name" -> snapshot.name();
            case "time_left" -> TimeUtil.format(Math.max(0, (snapshot.endsAt() - System.currentTimeMillis()) / 1000));
            case "progress" -> String.valueOf(snapshot.progress());
            case "goal" -> String.valueOf(snapshot.goal());
            case "progress_percent" -> String.valueOf(Math.round(snapshot.progress() * 100.0 / snapshot.goal()));
            case "protection" -> String.valueOf(Math.round(snapshot.protection() * 100));
            default -> null;
        };
    }

    private String defaultValue(String params) {
        return switch (params.toLowerCase()) {
            case "active" -> "false";
            case "progress", "goal", "progress_percent", "protection" -> "0";
            default -> "";
        };
    }
}
