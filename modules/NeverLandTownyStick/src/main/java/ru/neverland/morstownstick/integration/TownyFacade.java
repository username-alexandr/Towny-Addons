package ru.neverland.morstownstick.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.block.Block;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import ru.neverland.morstownstick.model.CellKey;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TownyFacade {
    public Resident resident(Player player) {
        return TownyAPI.getInstance().getResident(player);
    }

    public Town town(Player player) {
        Resident resident = resident(player);
        return resident == null ? null : resident.getTownOrNull();
    }

    public boolean isMayorOrAssistant(Player player) {
        Resident resident = resident(player);
        return resident != null && resident.getTownOrNull() != null
                && (resident.isMayor() || resident.hasTownRank("assistant"));
    }

    public CellKey cell(Block block) {
        return CellKey.from(WorldCoord.parseWorldCoord(block));
    }

    public int cellSize() {
        return Coord.getCellSize();
    }

    public Set<CellKey> ownedCells(Town town) {
        Set<CellKey> result = new LinkedHashSet<>();
        for (TownBlock block : town.getTownBlocks()) result.add(CellKey.from(block.getWorldCoord()));
        return result;
    }

    public boolean isClaimableWorld(CellKey cell) {
        TownyWorld world = cell.worldCoord().getTownyWorld();
        return world != null && world.isUsingTowny() && world.isClaimable();
    }

    public boolean isWilderness(CellKey cell) {
        return cell.worldCoord().isWilderness();
    }

    public boolean isOwnedBy(CellKey cell, Town town) {
        WorldCoord coord = cell.worldCoord();
        return coord.hasTownBlock() && town.equals(coord.getTownOrNull());
    }

    public List<Town> towns() {
        return TownyAPI.getInstance().getTowns();
    }

    public boolean registerStickCommand(CommandExecutor executor) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "stick", executor);
    }

    public void unregisterStickCommand() {
        TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "stick");
    }
}
