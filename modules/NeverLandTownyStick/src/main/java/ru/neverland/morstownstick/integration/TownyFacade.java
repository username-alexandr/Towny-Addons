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
    public Resident resident(Player player) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return TownyAPI.getInstance().getResident(player);
    } }

    public Town town(Player player) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        Resident resident = resident(player);
        return resident == null ? null : resident.getTownOrNull();
    } }

    public boolean isMayorOrAssistant(Player player) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        Resident resident = resident(player);
        return resident != null && resident.getTownOrNull() != null
                && (resident.isMayor() || resident.hasTownRank("assistant"));
    } }

    public CellKey cell(Block block) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        return CellKey.from(WorldCoord.parseWorldCoord(block));
    } }

    public int cellSize() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        return Coord.getCellSize();
    } }

    public Set<CellKey> ownedCells(Town town) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        Set<CellKey> result = new LinkedHashSet<>();
        for (TownBlock block : town.getTownBlocks()) result.add(CellKey.from(block.getWorldCoord()));
        return result;
    } }

    public boolean isClaimableWorld(CellKey cell) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        TownyWorld world = cell.worldCoord().getTownyWorld();
        return world != null && world.isUsingTowny() && world.isClaimable();
    } }

    public boolean isWilderness(CellKey cell) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        return cell.worldCoord().isWilderness();
    } }

    public boolean isOwnedBy(CellKey cell, Town town) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        WorldCoord coord = cell.worldCoord();
        return coord.hasTownBlock() && town.equals(coord.getTownOrNull());
    } }

    public List<Town> towns() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        return TownyAPI.getInstance().getTowns();
    } }

    public boolean registerStickCommand(CommandExecutor executor) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "stick", executor);
    } }

    public void unregisterStickCommand() { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "stick");
    } }
}
