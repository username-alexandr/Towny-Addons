package ru.neverland.mintespionage.integration;

import com.palmergames.bukkit.towny.object.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.mintespionage.MintTownyEspionage;
import ru.neverland.mintespionage.service.EspionageService;

public final class EspionageExpansion extends PlaceholderExpansion {
    private final MintTownyEspionage plugin;private final TownyHook towny;private final EspionageService service;
    public EspionageExpansion(MintTownyEspionage plugin,TownyHook towny,EspionageService service){this.plugin=plugin;this.towny=towny;this.service=service;}
    @Override public @NotNull String getIdentifier(){return "mintespionage";}@Override public @NotNull String getAuthor(){return "Alexander Sokolov";}@Override public @NotNull String getVersion(){return plugin.getPluginMeta().getVersion();}@Override public boolean persist(){return true;}
    @Override public @Nullable String onRequest(OfflinePlayer player,@NotNull String params){if(player==null)return "";Town town=towny.townOf(player.getUniqueId());if(town==null)return "0";var data=service.data(town);return switch(params.toLowerCase()){case "network_level"->String.valueOf(data.networkLevel());case "defense_level"->String.valueOf(data.defenseLevel());case "active_operations"->String.valueOf(service.repository().active(town.getUUID()).size());case "operation_limit"->String.valueOf(service.activeLimit(town));case "unread_reports"->String.valueOf(service.unread(town.getUUID()));case "defense_strength"->String.valueOf(Math.round(service.defenseStrength(town)*100));default->null;};}
}
