package ru.neverland.townybuilds;

import org.bukkit.block.data.Bisected;
import ru.neverland.townybuilds.civic.CivicArea;
import ru.neverland.townybuilds.civic.CivicLine;
import ru.neverland.townybuilds.construction.BlockOffset;
import ru.neverland.townybuilds.construction.BlockRole;
import ru.neverland.townybuilds.construction.BlueprintPlan;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;
import ru.neverland.townybuilds.construction.CivicBlueprintGenerator;
import ru.neverland.townybuilds.model.ProjectCategory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Регрессия 16 муниципальных проектов, категорий и геометрии выделений. */
public final class CivicExpansionSmoke {
    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        CivicBlueprintGenerator civic = new CivicBlueprintGenerator();
        check(civic.supportedProjects().size() == 18, "Ожидалось 18 новых физических проектов");
        check(generator.supportedBuildings().size() == 83, "Ожидалось 83 городских проектов");
        check(generator.supportedProjects().size() == 94, "Ожидалось 94 проектов вместе с Чудесами");

        Set<String> signatures = new HashSet<>();
        for (String project : CivicBlueprintGenerator.PROJECTS) {
            BlueprintPlan previous = null;
            for (int stage = 1; stage <= 5; stage++) {
                BlueprintPlan plan = generator.generate(project, stage);
                check(plan != null, project + ": план не создан");
                int current = stage;
                check(plan.blocks().values().stream().anyMatch(block -> block.stage() == current
                                && block.role() == BlockRole.RESIDENT),
                        project + ": этап " + stage + " не содержит работы жителей");
                if (previous != null) {
                    for (var entry : previous.blocks().entrySet()) {
                        check(entry.getValue().equals(plan.blocks().get(entry.getKey())),
                                project + ": этап изменил готовый блок " + entry.getKey());
                    }
                }
                previous = plan;
            }
            BlueprintPlan finalPlan = generator.generate(project, 5);
            int minX = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::x).min().orElseThrow();
            int maxX = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::x).max().orElseThrow();
            int minZ = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::z).min().orElseThrow();
            int maxZ = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::z).max().orElseThrow();
            int maxY = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::y).max().orElseThrow();
            signatures.add((maxX - minX + 1) + "x" + (maxZ - minZ + 1) + "x" + (maxY + 1)
                    + ":" + finalPlan.blocks().size());
            if (!CivicBlueprintGenerator.LINE_PROJECTS.contains(project)) {
                long lower = finalPlan.blocks().values().stream().filter(block -> block.material().name().endsWith("_DOOR"))
                        .filter(block -> block.half() == Bisected.Half.BOTTOM).count();
                long upper = finalPlan.blocks().values().stream().filter(block -> block.material().name().endsWith("_DOOR"))
                        .filter(block -> block.half() == Bisected.Half.TOP).count();
                check(lower == 1 && upper == 1, project + ": вход должен содержать одну полную дверь");
            }
            check(ProjectCategory.parse(null, project) != ProjectCategory.OTHER,
                    project + ": не назначена категория");
        }
        check(signatures.size() == CivicBlueprintGenerator.PROJECTS.size(), "Новые модели должны иметь разные сигнатуры");

        UUID world = UUID.randomUUID();
        CivicArea area = new CivicArea(world, -1, 31, -1, 31);
        check(area.chunkCount() == 9 && area.contains(0, 0) && !area.contains(40, 0), "Ошибка геометрии территории");
        CivicLine line = new CivicLine(world, 0, 64, 0, 31, 64, 0);
        check(line.length() == 32, "Ошибка длины линейного проекта");
        System.out.println("CivicExpansionSmoke OK: 18 моделей, 90 этапов, 7 категорий");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
