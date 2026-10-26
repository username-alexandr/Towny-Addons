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
    public Resident resident(Player player) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getResident(player); } }
    public Resident resident(String name) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getResident(name); } }
    public Resident resident(UUID id) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getResident(id); } }
    public Collection<Resident> residents() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getResidents(); } }
    public Town town(Player player) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { Resident resident = resident(player); return resident == null ? null : resident.getTownOrNull(); } }
    public Town town(String name) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getTown(name); } }
    public Town town(UUID id) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getTown(id); } }
    public Collection<Town> towns() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getTowns(); } }
    public Nation nation(Player player) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { Town town = town(player); return town == null ? null : TownyAPI.getInstance().getTownNationOrNull(town); } }
    public Nation nation(String name) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getNation(name); } }
    public Nation nation(UUID id) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getNation(id); } }
    public Collection<Nation> nations() { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getNations(); } }
    public boolean registerTown(String name, CommandExecutor executor) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) { return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor); } }
    public boolean registerNation(String name, CommandExecutor executor) { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) { return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.NATION, name, executor); } }
    public void unregisterTown(String name) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name); } }
    public void unregisterNation(String name) { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.NATION, name); } }
}
