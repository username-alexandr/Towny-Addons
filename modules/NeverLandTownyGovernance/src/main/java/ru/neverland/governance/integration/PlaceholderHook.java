package ru.neverland.governance.integration;

import org.bukkit.Bukkit;
import ru.neverland.governance.TownyGovernance;
import ru.neverland.governance.service.GovernanceService;

public final class PlaceholderHook {
    private PlaceholderHook() { }
    public static boolean register(TownyGovernance plugin, TownyHook towny, GovernanceService governance) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return false;
        try { return new GovernanceExpansion(plugin, towny, governance).register(); }
        catch (LinkageError error) { plugin.getLogger().warning("PlaceholderAPI несовместим: " + error.getMessage()); return false; }
    }
}
