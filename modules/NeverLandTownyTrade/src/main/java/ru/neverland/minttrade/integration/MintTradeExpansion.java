package ru.neverland.minttrade.integration;

import com.palmergames.bukkit.towny.object.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.minttrade.MintTownyTrade;
import ru.neverland.minttrade.service.TradeService;
import ru.neverland.minttrade.util.TimeUtil;

public final class MintTradeExpansion extends PlaceholderExpansion {
    private final MintTownyTrade plugin; private final TownyHook towny; private final TradeService trade;
    public MintTradeExpansion(MintTownyTrade plugin, TownyHook towny, TradeService trade) { this.plugin = plugin; this.towny = towny; this.trade = trade; }
    @Override public @NotNull String getIdentifier() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return "minttrade"; } }
    @Override public @NotNull String getAuthor() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return "Alexander Sokolov"; } }
    @Override public @NotNull String getVersion() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return plugin.getPluginMeta().getVersion(); } }
    @Override public boolean persist() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return true; } }
    @Override public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        if (!(offline instanceof Player player)) return ""; Town town = towny.town(player);
        return switch (params.toLowerCase()) {
            case "town" -> town == null ? "" : town.getName(); case "market_level" -> String.valueOf(trade.marketLevel(town));
            case "active_routes" -> String.valueOf(trade.activeCount(town)); case "route_limit" -> String.valueOf(trade.routeLimit(town));
            case "tariff" -> town == null ? "0" : String.valueOf(trade.tariff(town));
            case "incoming" -> String.valueOf(trade.incoming(town).size()); case "outgoing" -> String.valueOf(trade.outgoing(town).size());
            case "next_arrival" -> town == null || trade.caravans(town).isEmpty() ? "—" : TimeUtil.format(trade.caravans(town).stream().mapToLong(value -> value.timer().remaining(System.currentTimeMillis())).min().orElse(0));
            default -> null;
        };
    } }
}
