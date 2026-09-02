package ru.neverland.morstownstick.service;

import ru.neverland.morstownstick.model.BorderEdge;
import ru.neverland.morstownstick.model.CellKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BorderGeometry {
    private BorderGeometry() { }

    /** Builds only the outside perimeter: shared edges between adjacent cells are omitted. */
    public static List<BorderEdge> outsideEdges(Collection<CellKey> cells, int cellSize) {
        Set<CellKey> occupied = new HashSet<>(cells);
        List<BorderEdge> edges = new ArrayList<>();
        for (CellKey cell : cells) {
            int minX = cell.minBlockX(cellSize);
            int minZ = cell.minBlockZ(cellSize);
            int maxX = minX + cellSize;
            int maxZ = minZ + cellSize;
            if (!occupied.contains(new CellKey(cell.world(), cell.x(), cell.z() - 1)))
                edges.add(new BorderEdge(cell.world(), minX, minZ, maxX, minZ));
            if (!occupied.contains(new CellKey(cell.world(), cell.x(), cell.z() + 1)))
                edges.add(new BorderEdge(cell.world(), minX, maxZ, maxX, maxZ));
            if (!occupied.contains(new CellKey(cell.world(), cell.x() - 1, cell.z())))
                edges.add(new BorderEdge(cell.world(), minX, minZ, minX, maxZ));
            if (!occupied.contains(new CellKey(cell.world(), cell.x() + 1, cell.z())))
                edges.add(new BorderEdge(cell.world(), maxX, minZ, maxX, maxZ));
        }
        return List.copyOf(edges);
    }

    /** Selected cells are outlined one by one so the player can see every selected TownBlock. */
    public static List<BorderEdge> individualEdges(Collection<CellKey> cells, int cellSize) {
        List<BorderEdge> edges = new ArrayList<>(cells.size() * 4);
        for (CellKey cell : cells) {
            int minX = cell.minBlockX(cellSize);
            int minZ = cell.minBlockZ(cellSize);
            int maxX = minX + cellSize;
            int maxZ = minZ + cellSize;
            edges.add(new BorderEdge(cell.world(), minX, minZ, maxX, minZ));
            edges.add(new BorderEdge(cell.world(), minX, maxZ, maxX, maxZ));
            edges.add(new BorderEdge(cell.world(), minX, minZ, minX, maxZ));
            edges.add(new BorderEdge(cell.world(), maxX, minZ, maxX, maxZ));
        }
        return List.copyOf(edges);
    }
}
