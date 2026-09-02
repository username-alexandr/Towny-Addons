package ru.neverland.archaeology.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public final class TownyHook {
    public Resident resident(Player player) { return TownyAPI.getInstance().getResident(player); } public Town town(Player player) { Resident resident = resident(player); return resident == null ? null : resident.getTownOrNull(); }
    public Town town(String name) { return TownyAPI.getInstance().getTown(name); } public Town town(UUID id) { return TownyAPI.getInstance().getTown(id); } public Town town(Location location) { return TownyAPI.getInstance().getTown(location); } public Collection<Town> towns() { return TownyAPI.getInstance().getTowns(); }
    public boolean register(String name, CommandExecutor executor) { return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor); } public void unregister(String name) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name); }
}
