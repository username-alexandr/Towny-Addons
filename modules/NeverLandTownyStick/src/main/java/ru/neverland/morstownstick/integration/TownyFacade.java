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
    public Resident resident(Player player) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return TownyAPI.getInstance().getResident(player);
    } }

    public Town town(Player player) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Resident resident = resident(player);
        return resident == null ? null : resident.getTownOrNull();
    } }

    public boolean isMayorOrAssistant(Player player) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Resident resident = resident(player);
        return resident != null && resident.getTownOrNull() != null
                && (resident.isMayor() || resident.hasTownRank("assistant"));
    } }

    public CellKey cell(Block block) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return CellKey.from(WorldCoord.parseWorldCoord(block));
    } }

    public int cellSize() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return Coord.getCellSize();
    } }

    public Set<CellKey> ownedCells(Town town) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Set<CellKey> result = new LinkedHashSet<>();
        for (TownBlock block : town.getTownBlocks()) result.add(CellKey.from(block.getWorldCoord()));
        return result;
    } }

    public boolean isClaimableWorld(CellKey cell) { synchronized (ru.neverland.core.SuiteState.lock()) {
        TownyWorld world = cell.worldCoord().getTownyWorld();
        return world != null && world.isUsingTowny() && world.isClaimable();
    } }

    public boolean isWilderness(CellKey cell) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return cell.worldCoord().isWilderness();
    } }

    public boolean isOwnedBy(CellKey cell, Town town) { synchronized (ru.neverland.core.SuiteState.lock()) {
        WorldCoord coord = cell.worldCoord();
        return coord.hasTownBlock() && town.equals(coord.getTownOrNull());
    } }

    public List<Town> towns() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return TownyAPI.getInstance().getTowns();
    } }

    public boolean registerStickCommand(CommandExecutor executor) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "stick", executor);
    } }

    public void unregisterStickCommand() { synchronized (ru.neverland.core.SuiteState.lock()) {
        TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "stick");
    } }
}
