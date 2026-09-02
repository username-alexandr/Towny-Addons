package ru.neverland.townybuilds;

import org.bukkit.Axis;
import ru.neverland.townybuilds.construction.BlockOffset;
import ru.neverland.townybuilds.construction.BlockRole;
import ru.neverland.townybuilds.construction.BlueprintPlan;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;

import java.util.Map;
import java.util.Set;

public final class BlueprintSeamSmoke {
    private static final Set<String> CORE_BUILDINGS = Set.of(
            "town_hall", "forge", "barracks", "market", "miners_guild", "temple",
            "great_library", "agrarian_complex");
    private static final Map<String, int[][]> SEAMS = Map.of(
            "town_hall", new int[][]{{5, 0}, {-5, 0}},
            "forge", new int[][]{{5, 0}, {-5, 0}},
            "barracks", new int[][]{{6, 0}},
            "miners_guild", new int[][]{{5, 0}, {-5, 0}},
            "temple", new int[][]{{4, 0}, {-4, 0}},
            "great_library", new int[][]{{5, 0}, {-5, 0}},
            "agrarian_complex", new int[][]{{5, 0}}
    );

    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        for (Map.Entry<String, int[][]> project : SEAMS.entrySet()) {
            BlueprintPlan plan = generator.generate(project.getKey(), 5);
            for (int[] seam : project.getValue()) {
                for (int y = 1; y <= 3; y++) {
                    var block = plan.blocks().get(new BlockOffset(seam[0], y, seam[1]));
                    check(block != null, project.getKey() + ": щель не закрыта в " + seam[0] + "," + y + "," + seam[1]);
                    check(block.role() == BlockRole.DECORATION,
                            project.getKey() + ": стык ошибочно требует ручной установки");
                }
            }
        }

        for (String project : CORE_BUILDINGS) {
            BlueprintPlan previous = generator.generateArchitecture3(project, 1);
            BlueprintPlan current = generator.generate(project, 1);
            long addedRidgeEnds = current.blocks().entrySet().stream()
                    .filter(entry -> !previous.blocks().containsKey(entry.getKey()))
                    .filter(entry -> entry.getValue().role() == BlockRole.DECORATION)
                    .filter(entry -> entry.getValue().axis() == Axis.Z)
                    .count();
            check(addedRidgeEnds >= 2, project + ": край конька всё ещё содержит выемку");
        }

        System.out.println("BlueprintSeamSmoke OK: стыки закрыты, края коньков заполнены");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
