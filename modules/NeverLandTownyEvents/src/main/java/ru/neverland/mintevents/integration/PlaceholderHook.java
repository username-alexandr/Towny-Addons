package ru.neverland.mintevents.integration;

import org.bukkit.Bukkit;
import ru.neverland.mintevents.MintTownyEvents;
import ru.neverland.mintevents.service.EventService;

public final class PlaceholderHook {
    private PlaceholderHook() {}

    public static boolean register(MintTownyEvents plugin, TownyHook towny, EventService events) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return false;
        try { return new MintEventsExpansion(plugin, towny, events).register(); }
        catch (LinkageError error) { plugin.getLogger().warning("PlaceholderAPI несовместим: " + error.getMessage()); return false; }
    }
}
