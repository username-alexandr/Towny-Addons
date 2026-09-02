package ru.neverland.townyideologies.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public final class TownyHook {
    public Resident resident(Player player) {
        return TownyAPI.getInstance().getResident(player);
    }

    public Town town(Player player) {
        Resident resident = resident(player);
        return resident == null ? null : resident.getTownOrNull();
    }

    public Town townAt(Location location) {
        return TownyAPI.getInstance().getTown(location);
    }

    public boolean isMayor(Player player, Town town) {
        Resident resident = resident(player);
        return resident != null && town != null && town.isMayor(resident);
    }

    public boolean registerTownCommand(String name, CommandExecutor executor) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor);
    }

    public void unregisterTownCommand(String name) {
        TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name);
    }
}
