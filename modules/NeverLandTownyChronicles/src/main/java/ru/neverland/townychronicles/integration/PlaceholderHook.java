package ru.neverland.townychronicles.integration;

import org.bukkit.Bukkit;
import ru.neverland.townychronicles.TownyChronicles;
import ru.neverland.townychronicles.service.ChronicleRepository;

public final class PlaceholderHook {private PlaceholderHook(){}public static boolean register(TownyChronicles plugin,TownyHook towny,ChronicleRepository repository){if(!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))return false;try{return new ChroniclesExpansion(plugin,towny,repository).register();}catch(LinkageError error){plugin.getLogger().warning("PlaceholderAPI несовместим: "+error.getMessage());return false;}}}
