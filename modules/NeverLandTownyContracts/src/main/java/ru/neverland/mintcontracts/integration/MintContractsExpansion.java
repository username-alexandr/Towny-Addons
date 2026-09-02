package ru.neverland.mintcontracts.integration;

import com.palmergames.bukkit.towny.object.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.mintcontracts.MintTownyContracts;
import ru.neverland.mintcontracts.service.ContractService;

public final class MintContractsExpansion extends PlaceholderExpansion {
    private final MintTownyContracts plugin; private final TownyHook towny; private final ContractService contracts;
    public MintContractsExpansion(MintTownyContracts plugin, TownyHook towny, ContractService contracts) {
        this.plugin = plugin; this.towny = towny; this.contracts = contracts;
    }
    @Override public @NotNull String getIdentifier() { return "mintcontracts"; }
    @Override public @NotNull String getAuthor() { return "Alexander Sokolov"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (!(offline instanceof Player player)) return "";
        Town town = towny.town(player);
        return switch (params.toLowerCase()) {
            case "active_count" -> String.valueOf(town == null ? 0 : contracts.active(town.getUUID()).size());
            case "pending_reward" -> contracts.economy().format(contracts.pending(player.getUniqueId()));
            case "town" -> town == null ? "" : town.getName();
            case "total_progress" -> String.valueOf(town == null ? 0 : contracts.active(town.getUUID()).stream().mapToInt(c -> c.progress()).sum());
            default -> null;
        };
    }
}
