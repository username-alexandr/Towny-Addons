package ru.neverland.archaeology.integration;

import org.bukkit.Bukkit;
import ru.neverland.archaeology.TownyArchaeology;
import ru.neverland.archaeology.service.ArchaeologyRegistry;
import ru.neverland.archaeology.service.ArchaeologyRepository;
import ru.neverland.archaeology.service.MuseumService;

public final class PlaceholderHook {
    private PlaceholderHook() { }
    public static boolean register(TownyArchaeology plugin, TownyHook towny, ArchaeologyRegistry registry, ArchaeologyRepository repository, MuseumService museums) { if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return false; try { return new ArchaeologyExpansion(plugin, towny, registry, repository, museums).register(); } catch (LinkageError error) { plugin.getLogger().warning("PlaceholderAPI несовместим: " + error.getMessage()); return false; } }
}
