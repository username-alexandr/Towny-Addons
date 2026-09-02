package ru.neverland.morstownstick.service;

import ru.neverland.morstownstick.model.CellKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class BfsPlanner {
    public record Plan(List<CellKey> ordered, List<CellKey> unreachable) { }

    private BfsPlanner() { }

    public static Plan plan(Collection<CellKey> selectedCells, Collection<CellKey> townCells) {
        LinkedHashSet<CellKey> selected = new LinkedHashSet<>(selectedCells);
        Set<CellKey> town = new HashSet<>(townCells);
        Set<CellKey> visited = new HashSet<>();
        Queue<CellKey> queue = new ArrayDeque<>();

        for (CellKey candidate : selected) {
            if (candidate.neighbors().stream().anyMatch(town::contains) && visited.add(candidate)) queue.add(candidate);
        }

        List<CellKey> ordered = new ArrayList<>(selected.size());
        while (!queue.isEmpty()) {
            CellKey current = queue.remove();
            ordered.add(current);
            for (CellKey neighbor : current.neighbors()) {
                if (selected.contains(neighbor) && visited.add(neighbor)) queue.add(neighbor);
            }
        }

        List<CellKey> unreachable = selected.stream().filter(cell -> !visited.contains(cell)).toList();
        return new Plan(List.copyOf(ordered), unreachable);
    }
}
