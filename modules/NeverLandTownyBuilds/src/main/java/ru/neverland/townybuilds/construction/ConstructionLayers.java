package ru.neverland.townybuilds.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiPredicate;

/** Derived from the real blocks, so restart, upgrades and damage need no separate progress ledger. */
public final class ConstructionLayers {
    private ConstructionLayers() {}

    public record Cell(BlockOffset offset, BlueprintBlock block) {}
    public record Layer(int y, int number, int layers, int placed, int total, List<Cell> missing) {
        public Layer { missing = List.copyOf(missing); }
        public boolean complete() { return missing.isEmpty(); }
    }

    /** The caller must own and have loaded ALL relevant chunks before invoking the world predicate. */
    public static Layer inspect(BlueprintPlan plan, int fromStage, int toStage,
                                BiPredicate<BlockOffset, BlueprintBlock> matches) {
        Map<Integer, List<Cell>> layers = new TreeMap<>();
        plan.blocks().forEach((offset, block) -> {
            if (block.role() == BlockRole.RESIDENT && block.stage() >= fromStage && block.stage() <= toStage)
                layers.computeIfAbsent(offset.y(), ignored -> new ArrayList<>()).add(new Cell(offset, block));
        });
        int number = 0;
        for (var entry : layers.entrySet()) {
            number++;
            List<Cell> missing = new ArrayList<>();
            for (Cell cell : entry.getValue()) if (!matches.test(cell.offset(), cell.block())) missing.add(cell);
            if (!missing.isEmpty()) {
                missing.sort(java.util.Comparator.comparingInt((Cell c) -> c.offset().x())
                        .thenComparingInt(c -> c.offset().z()));
                return new Layer(entry.getKey(), number, layers.size(), entry.getValue().size() - missing.size(),
                        entry.getValue().size(), missing);
            }
        }
        return new Layer(0, layers.size(), layers.size(), 0, 0, List.of());
    }
}
