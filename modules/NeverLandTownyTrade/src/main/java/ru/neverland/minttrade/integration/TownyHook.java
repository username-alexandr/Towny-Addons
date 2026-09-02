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
    public Resident resident(Player player) { return TownyAPI.getInstance().getResident(player); }
    public Resident resident(UUID id) { return TownyAPI.getInstance().getResident(id); }
    public Town town(Player player) { Resident resident = resident(player); return resident == null ? null : resident.getTownOrNull(); }
    public Town town(UUID id) { return TownyAPI.getInstance().getTown(id); }
    public Town town(String name) { return TownyAPI.getInstance().getTown(name); }
    public Town townAt(Location location) { return TownyAPI.getInstance().getTown(location); }
    public Collection<Town> towns() { return TownyAPI.getInstance().getTowns(); }
    public boolean isManager(Player player, Town town) {
        Resident resident = resident(player);
        if (resident == null || town == null || !town.equals(resident.getTownOrNull())) return false;
        if (plugin.getConfig().getBoolean("management.mayor", true) && town.isMayor(resident)) return true;
        List<String> ranks = plugin.getConfig().getStringList("management.allowed-town-ranks");
        for (String rank : ranks) if (resident.hasTownRank(rank)) return true;
        return false;
    }
    public boolean register(String name, CommandExecutor executor) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor);
    }
    public void unregister(String name) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name); }
}
