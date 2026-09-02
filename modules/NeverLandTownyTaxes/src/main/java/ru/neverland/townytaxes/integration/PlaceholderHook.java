package ru.neverland.townytaxes.integration;

import org.bukkit.Bukkit;
import ru.neverland.townytaxes.NeverLandTownyTaxes;
import ru.neverland.townytaxes.service.FiscalService;

public final class PlaceholderHook {private PlaceholderHook(){}public static boolean register(NeverLandTownyTaxes plugin,TownyHook towny,FiscalService fiscal){if(!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))return false;try{return new TaxesExpansion(plugin,towny,fiscal).register();}catch(LinkageError error){plugin.getLogger().warning("PlaceholderAPI несовместим: "+error.getMessage());return false;}}}
