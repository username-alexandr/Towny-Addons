package ru.neverland.mintcontracts.integration;

import org.bukkit.Bukkit;
import ru.neverland.mintcontracts.MintTownyContracts;
import ru.neverland.mintcontracts.service.ContractService;

public final class PlaceholderHook {
    private PlaceholderHook() {}
    public static boolean register(MintTownyContracts plugin, TownyHook towny, ContractService contracts) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return false;
        try { return new MintContractsExpansion(plugin, towny, contracts).register(); }
        catch (LinkageError error) { plugin.getLogger().warning("PlaceholderAPI несовместим: " + error.getMessage()); return false; }
    }
}
