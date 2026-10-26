package ru.neverland.mintcontracts.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

public final class TownyHook {
    private final JavaPlugin plugin;
    public TownyHook(JavaPlugin plugin) { this.plugin = plugin; }
    public Resident resident(Player player) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getResident(player); } }
    public Resident resident(java.util.UUID id) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getResident(id); } }
    public Town town(Player player) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { Resident resident = resident(player); return resident == null ? null : resident.getTownOrNull(); } }
    public Town town(String name) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getTown(name); } }
    public Town town(java.util.UUID id) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getTown(id); } }
    public Collection<Town> towns() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return TownyAPI.getInstance().getTowns(); } }
    public boolean isManager(Player player,Town town){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        Resident resident=resident(player);if(resident==null||town==null||!town.equals(resident.getTownOrNull()))return false;
        boolean legacy=plugin.getConfig().getStringList("management.allowed-town-ranks").stream().anyMatch(resident::hasTownRank);
        if(town.isMayor(resident))return plugin.getConfig().getBoolean("management.mayor",true)||legacy;
        return ru.neverland.core.CouncilAccess.check(player,town.getUUID(),"contracts",legacy);
    } }
    public boolean register(String name, CommandExecutor executor) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor);
    } }
    public void unregister(String name) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name); } }
}
