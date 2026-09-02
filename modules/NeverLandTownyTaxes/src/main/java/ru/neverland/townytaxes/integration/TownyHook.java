package ru.neverland.townytaxes.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.Account;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townytaxes.model.Domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class TownyHook {
    public static final UUID GLOBAL_ID = new UUID(0, 1);
    public record Party(Domain.Scope scope, UUID id, String name, Account account) {}
    private final JavaPlugin plugin;
    public TownyHook(JavaPlugin plugin) { this.plugin = plugin; }
    public Resident resident(Player player) { return TownyAPI.getInstance().getResident(player); }
    public Resident resident(UUID id) { return TownyAPI.getInstance().getResident(id); }
    public Resident resident(String name) { return TownyAPI.getInstance().getResident(name); }
    public Town town(Player player) { Resident resident = resident(player); return resident == null ? null : resident.getTownOrNull(); }
    public Town town(UUID id) { return TownyAPI.getInstance().getTown(id); }
    public Town town(String name) { return TownyAPI.getInstance().getTown(name); }
    public Nation nation(UUID id) { return TownyAPI.getInstance().getNation(id); }
    public Nation nation(String name) { return TownyAPI.getInstance().getNation(name); }
    public Nation nation(Player player) { Town town=town(player); return town==null?null:town.getNationOrNull(); }
    public Collection<Resident> residents() { return TownyAPI.getInstance().getResidents(); }
    public Collection<Town> towns() { return TownyAPI.getInstance().getTowns(); }
    public boolean isTownManager(Player player, Town town) {
        Resident resident = resident(player); if (resident == null || town == null || !town.equals(resident.getTownOrNull())) return false;
        if (town.isMayor(resident)) return true;
        for (String rank : plugin.getConfig().getStringList("management.allowed-town-ranks")) if (resident.hasTownRank(rank)) return true;
        return player.hasPermission("townytaxes.bypass");
    }
    public boolean isNationManager(Player player, Nation nation) {
        Resident resident = resident(player); if (resident == null || nation == null || !resident.hasNation()) return false;
        if (nation.isKing(resident) || nation.hasAssistant(resident)) return true;
        for (String rank : plugin.getConfig().getStringList("management.allowed-nation-ranks")) if (resident.hasNationRank(rank)) return true;
        return player.hasPermission("townytaxes.bypass");
    }
    public Party resolve(Domain.Scope scope, String value) {
        if (scope == null) return null;
        return switch (scope) {
            case GLOBAL -> new Party(scope, GLOBAL_ID, "Сервер", null);
            case PLAYER -> { Resident r = resident(value); yield r == null ? null : new Party(scope, r.getUUID(), r.getName(), r.getAccount()); }
            case TOWN -> { Town t = town(value); yield t == null ? null : new Party(scope, t.getUUID(), t.getName(), t.getAccount()); }
            case NATION -> { Nation n = nation(value); yield n == null ? null : new Party(scope, n.getUUID(), n.getName(), n.getAccount()); }
        };
    }
    public Party resolve(Domain.Scope scope, UUID id) {
        if (scope == Domain.Scope.GLOBAL) return new Party(scope, GLOBAL_ID, "Сервер", null);
        return resolve(scope, switch (scope) { case PLAYER -> { Resident r=resident(id); yield r==null?"":r.getName(); } case TOWN -> {Town t=town(id);yield t==null?"":t.getName();} case NATION -> {Nation n=nation(id);yield n==null?"":n.getName();} default -> ""; });
    }
    public List<Resident> residents(Town town) { return town == null ? List.of() : town.getResidents(); }
    public List<Town> towns(Nation nation) { return nation == null ? List.of() : nation.getTowns(); }
    public boolean register(String name, CommandExecutor executor) { return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name, executor); }
    public void unregister(String name) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, name); }
    public boolean registerNation(String name, CommandExecutor executor) { return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.NATION, name, executor); }
    public void unregisterNation(String name) { TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.NATION, name); }
}
