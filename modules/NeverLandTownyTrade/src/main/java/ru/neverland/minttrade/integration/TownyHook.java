package ru.neverland.minttrade.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class TownyHook {
    private final JavaPlugin plugin;
    public TownyHook(JavaPlugin plugin) { this.plugin = plugin; }
    public Resident resident(Player player) { synchronized (ru.neverland.core.SuiteState.lock()) { return TownyAPI.getInstance().getResident(player); } }
    public Resident resident(UUID id) { synchronized (ru.neverland.core.SuiteState.lock()) { return TownyAPI.getInstance().getResident(id); } }
    public Town town(Player player) { synchronized (ru.neverland.core.SuiteState.lock()) { Resident resident = resident(player); return resident == null ? null : resident.getTownOrNull(); } }
    public Town town(UUID id) { synchronized (ru.neverland.core.SuiteState.lock()) { return TownyAPI.getInstance().getTown(id); } }
    public Town town(String name) { synchronized (ru.neverland.core.SuiteState.lock()) { return TownyAPI.getInstance().getTown(name); } }
    public Town townAt(Location location) { synchronized (ru.neverland.core.SuiteState.lock()) { return TownyAPI.getInstance().getTown(location); } }
    public Collection<Town> towns() { synchronized (ru.neverland.core.SuiteState.lock()) { return TownyAPI.getInstance().getTowns(); } }
    public boolean isManager(Player player,Town town){ synchronized(ru.neverland.core.SuiteState.lock()) {
        Resident resident=resident(player);if(resident==null||town==null||!town.equals(resident.getTownOrNull()))return false;
        boolean legacy=plugin.getConfig().getStringList("management.allowed-town-ranks").stream().anyMatch(resident::hasTownRank);
        if(town.isMayor(resident))return plugin.getConfig().getBoolean("management.mayor",true)||legacy;
        return ru.neverland.core.CouncilAccess.check(player,town.getUUID(),"trade",legacy);
    } }
    public boolean register(String name, CommandExecutor executor) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor);
    } }
    public void unregister(String name) { synchronized (ru.neverland.core.SuiteState.lock()) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name); } }
}
