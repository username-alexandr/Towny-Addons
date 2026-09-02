package ru.neverland.mintespionage.integration;

import org.bukkit.Bukkit;
import ru.neverland.mintespionage.MintTownyEspionage;
import ru.neverland.mintespionage.service.EspionageService;

public final class PlaceholderHook {
    private PlaceholderHook(){}
    public static boolean register(MintTownyEspionage plugin,TownyHook towny,EspionageService service){if(!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))return false;try{return new EspionageExpansion(plugin,towny,service).register();}catch(LinkageError error){plugin.getLogger().warning("PlaceholderAPI несовместим: "+error.getMessage());return false;}}
}
