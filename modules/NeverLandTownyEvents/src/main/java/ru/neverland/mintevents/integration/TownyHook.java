package ru.neverland.mintevents.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

import java.util.Collection;

public final class TownyHook {
    public Resident resident(Player player) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getResident(player); } }

    public Town town(Player player) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        Resident resident = resident(player);
        return resident == null ? null : resident.getTownOrNull();
    } }

    public Town townAt(Location location) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getTown(location); } }
    public Town town(String name) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getTown(name); } }
    public Collection<Town> towns() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getTowns(); } }

    public boolean registerTownCommand(String name, CommandExecutor executor) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor);
    } }

    public void unregisterTownCommand(String name) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name);
    } }
}
