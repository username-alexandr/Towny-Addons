package ru.neverland.townyquests.model;

import java.util.*;

/** Districts count once and only when every cell connects to the aqueduct through owned land. */
public final class WaterCoverage {
    private WaterCoverage() {}
    public record Cell(UUID world, int x, int z) { public Cell { Objects.requireNonNull(world); } }
    public static Set<String> supplied(Set<Cell> owned, Set<Cell> aqueduct, Map<String, Set<Cell>> districts) {
        if (aqueduct.isEmpty() || !owned.containsAll(aqueduct)) return Set.of();
        Set<Cell> reached = new HashSet<>(aqueduct);
        var pending = new ArrayDeque<>(aqueduct);
        while (!pending.isEmpty()) {
            var c = pending.removeFirst();
            for (var n : List.of(new Cell(c.world(), c.x() + 1, c.z()), new Cell(c.world(), c.x() - 1, c.z()),
                    new Cell(c.world(), c.x(), c.z() + 1), new Cell(c.world(), c.x(), c.z() - 1)))
                if (owned.contains(n) && reached.add(n)) pending.addLast(n);
        }
        Set<String> result = new TreeSet<>();
        districts.forEach((id, cells) -> { if (!cells.isEmpty() && reached.containsAll(cells)) result.add(id); });
        return Set.copyOf(result);
    }
}
