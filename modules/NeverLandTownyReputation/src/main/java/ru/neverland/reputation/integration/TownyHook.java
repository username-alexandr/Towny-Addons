package ru.neverland.reputation.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public final class TownyHook {
    public Resident resident(Player player) { return TownyAPI.getInstance().getResident(player); }
    public Resident resident(String name) { return TownyAPI.getInstance().getResident(name); }
    public Resident resident(UUID id) { return TownyAPI.getInstance().getResident(id); }
    public Collection<Resident> residents() { return TownyAPI.getInstance().getResidents(); }
    public Town town(Player player) { Resident resident = resident(player); return resident == null ? null : resident.getTownOrNull(); }
    public Town town(String name) { return TownyAPI.getInstance().getTown(name); }
    public Town town(UUID id) { return TownyAPI.getInstance().getTown(id); }
    public Collection<Town> towns() { return TownyAPI.getInstance().getTowns(); }
    public Nation nation(Player player) { Town town = town(player); return town == null ? null : TownyAPI.getInstance().getTownNationOrNull(town); }
    public Nation nation(String name) { return TownyAPI.getInstance().getNation(name); }
    public Nation nation(UUID id) { return TownyAPI.getInstance().getNation(id); }
    public Collection<Nation> nations() { return TownyAPI.getInstance().getNations(); }
    public boolean registerTown(String name, CommandExecutor executor) { return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor); }
    public boolean registerNation(String name, CommandExecutor executor) { return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.NATION, name, executor); }
    public void unregisterTown(String name) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name); }
    public void unregisterNation(String name) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.NATION, name); }
}
