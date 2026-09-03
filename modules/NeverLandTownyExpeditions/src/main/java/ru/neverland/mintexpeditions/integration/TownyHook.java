package ru.neverland.mintexpeditions.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class TownyHook {
    public UUID townId(Player player) {
        Resident resident = TownyAPI.getInstance().getResident(player);
        Town town = resident == null ? null : resident.getTownOrNull();
        return town == null ? null : town.getUUID();
    }

    public boolean wilderness(Location location) {
        return TownyAPI.getInstance().getTown(location) == null;
    }

    public boolean reward(Player player, double amount) {
        if (amount <= 0) return true;
        try {
            Resident resident = TownyAPI.getInstance().getResident(player);
            return resident != null && resident.getAccount().deposit(amount, "Награда за экспедицию");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean register(String name, CommandExecutor executor) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor);
    }

    public void unregister(String name) {
        TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name);
    }
}
