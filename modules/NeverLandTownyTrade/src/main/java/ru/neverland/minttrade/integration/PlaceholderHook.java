package ru.neverland.minttrade.integration;

import org.bukkit.Bukkit;
import ru.neverland.minttrade.MintTownyTrade;
import ru.neverland.minttrade.service.TradeService;

public final class PlaceholderHook {
    private PlaceholderHook() {}
    public static boolean register(MintTownyTrade plugin, TownyHook towny, TradeService trade) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return false;
        try { return new MintTradeExpansion(plugin, towny, trade).register(); }
        catch (LinkageError error) { plugin.getLogger().warning("PlaceholderAPI несовместим: " + error.getMessage()); return false; }
    }
}
