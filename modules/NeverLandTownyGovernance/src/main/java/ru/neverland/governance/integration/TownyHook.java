package ru.neverland.governance.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class TownyHook {
    private final JavaPlugin plugin;
    public TownyHook(JavaPlugin plugin) { this.plugin = plugin; }
    public Resident resident(Player player) { return TownyAPI.getInstance().getResident(player); }
    public Resident resident(String name) { return TownyAPI.getInstance().getResident(name); }
    public Resident resident(UUID id) { return TownyAPI.getInstance().getResident(id); }
    public Town town(Player player) { Resident resident = resident(player); return resident == null ? null : resident.getTownOrNull(); }
    public Town town(String name) { return TownyAPI.getInstance().getTown(name); }
    public Town town(UUID id) { return TownyAPI.getInstance().getTown(id); }
    public Collection<Town> towns() { return TownyAPI.getInstance().getTowns(); }
    public List<Player> online(Town town) { return town == null ? List.of() : TownyAPI.getInstance().getOnlinePlayersInTown(town); }
    public boolean isManager(Player player, Town town) {
        Resident resident = resident(player);
        if (resident == null || town == null || !town.equals(resident.getTownOrNull())) return false;
        if (town.isMayor(resident)) return true;
        for (String rank : plugin.getConfig().getStringList("management.allowed-town-ranks"))
            if (resident.hasTownRank(rank)) return true;
        return player.hasPermission("townygovernance.bypass");
    }
    public Set<UUID> townRankCouncil(Town town) {
        Set<UUID> result = new LinkedHashSet<>();
        if (town == null) return result;
        if (plugin.getConfig().getBoolean("council.mayor-is-member", true) && town.getMayor() != null)
            result.add(town.getMayor().getUUID());
        List<String> ranks = plugin.getConfig().getStringList("council.towny-ranks").stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).toList();
        for (Resident resident : town.getResidents())
            for (String rank : ranks) if (resident.hasTownRank(rank)) { result.add(resident.getUUID()); break; }
        return result;
    }
    public boolean register(String name, CommandExecutor executor) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor);
    }
    public void unregister(String name) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name); }
}
