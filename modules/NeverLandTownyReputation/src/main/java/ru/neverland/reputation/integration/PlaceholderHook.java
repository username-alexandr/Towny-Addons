package ru.neverland.reputation.integration;

import org.bukkit.Bukkit;
import ru.neverland.reputation.TownyReputation;
import ru.neverland.reputation.service.ReputationService;

public final class PlaceholderHook {
    private PlaceholderHook() { }
    public static boolean register(TownyReputation plugin, TownyHook towny, ReputationService reputation) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return false;
        try { return new ReputationExpansion(plugin, towny, reputation).register(); }
        catch (LinkageError error) { plugin.getLogger().warning("PlaceholderAPI несовместим: " + error.getMessage()); return false; }
    }
}
