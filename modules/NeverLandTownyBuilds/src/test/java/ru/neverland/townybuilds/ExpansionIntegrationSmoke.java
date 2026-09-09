package ru.neverland.townybuilds;

import org.bukkit.block.data.Bisected;
import ru.neverland.townybuilds.construction.BlockOffset;
import ru.neverland.townybuilds.construction.BlockRole;
import ru.neverland.townybuilds.construction.BlueprintPlan;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;
import ru.neverland.townybuilds.construction.ExpansionBlueprintGenerator;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ExpansionIntegrationSmoke {
    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        check(generator.supportedBuildings().size() == 80, "Ожидалось 80 городских зданий и объектов");
        check(generator.supportedProjects().size() == 91, "Ожидалось 91 физических проектов");

        Set<String> signatures = new HashSet<>();
        List<String> stagesWithoutResidentBlocks = new ArrayList<>();
        for (String project : ExpansionBlueprintGenerator.PROJECTS) {
            int previousSize = 0;
            BlueprintPlan previous = null;
            for (int stage = 1; stage <= 5; stage++) {
                BlueprintPlan plan = generator.generate(project, stage);
                check(plan != null, project + ": план не создан");
                check(plan.blocks().size() > previousSize, project + ": этап " + stage + " не расширяет модель");
                int currentStage = stage;
                if (plan.blocks().values().stream().noneMatch(block ->
                        block.stage() == currentStage && block.role() == BlockRole.RESIDENT)) {
                    stagesWithoutResidentBlocks.add(project + ":" + stage);
                }
                if (previous != null) {
                    for (var entry : previous.blocks().entrySet()) {
                        check(entry.getValue().equals(plan.blocks().get(entry.getKey())),
                                project + ": новый этап меняет старый блок " + entry.getKey());
                    }
                }
                previous = plan;
                previousSize = plan.blocks().size();
            }

            BlueprintPlan finalPlan = generator.generate(project, 5);
            check(finalPlan.blocks().values().stream().anyMatch(block -> block.role() == BlockRole.DECORATION),
                    project + ": в модели нет автоматической отделки");
            check(generator.generateForArchitecture(project, 5, 5).blocks().equals(finalPlan.blocks()),
                    project + ": источник архитектуры 5 не воспроизводится");
            if (!project.equals("aqueduct")) {
                long lowerDoors = finalPlan.blocks().values().stream()
                        .filter(block -> block.material().name().endsWith("_DOOR"))
                        .filter(block -> block.half() == Bisected.Half.BOTTOM).count();
                long upperDoors = finalPlan.blocks().values().stream()
                        .filter(block -> block.material().name().endsWith("_DOOR"))
                        .filter(block -> block.half() == Bisected.Half.TOP).count();
                check(lowerDoors > 0 && lowerDoors == upperDoors, project + ": нет полноценной двери");
            }

            int minX = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::x).min().orElseThrow();
            int maxX = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::x).max().orElseThrow();
            int minZ = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::z).min().orElseThrow();
            int maxZ = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::z).max().orElseThrow();
            int maxY = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::y).max().orElseThrow();
            String signature = (maxX - minX + 1) + "x" + (maxZ - minZ + 1) + "x" + (maxY + 1)
                    + ":" + finalPlan.blocks().size();
            check(signatures.add(signature), project + ": модель совпала по сигнатуре с другой");
        }

        check(stagesWithoutResidentBlocks.isEmpty(),
                "Жителям нечего строить на этапах: " + stagesWithoutResidentBlocks);
        System.out.println("ExpansionIntegrationSmoke OK: 17 зданий, 85 этапов");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
