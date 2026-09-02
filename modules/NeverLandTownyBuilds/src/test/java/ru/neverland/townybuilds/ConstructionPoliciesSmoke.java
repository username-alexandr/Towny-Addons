package ru.neverland.townybuilds;

import org.bukkit.Material;
import ru.neverland.townybuilds.construction.BlockOffset;
import ru.neverland.townybuilds.construction.BlockRole;
import ru.neverland.townybuilds.construction.BlueprintBlock;
import ru.neverland.townybuilds.construction.BlueprintPlan;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;
import ru.neverland.townybuilds.construction.ConstructionStagePolicy;
import ru.neverland.townybuilds.construction.FoundationSupportPolicy;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConstructionPoliciesSmoke {
    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        check(generator.maximumStage("warehouse") == 5, "У склада должно быть пять физических стадий");
        check(ConstructionStagePolicy.requiresConstruction(generator.maximumStage("warehouse"), 5),
                "Пятый уровень должен оставаться физической стройкой");
        check(!ConstructionStagePolicy.requiresConstruction(generator.maximumStage("warehouse"), 9),
                "Девятый уровень не должен повторно запускать пятый чертёж");
        check(ConstructionStagePolicy.requiresConstruction(generator.maximumStage("great_colosseum"), 1),
                "Чудо должно строиться на первом уровне");
        check(!ConstructionStagePolicy.requiresConstruction(generator.maximumStage("great_colosseum"), 2),
                "Чудо не должно повторно строиться после завершения");

        BlockOffset foundation = new BlockOffset(0, 0, 0);
        Map<BlockOffset, BlueprintBlock> blocks = new LinkedHashMap<>();
        blocks.put(foundation, new BlueprintBlock(Material.STONE_BRICKS, BlockRole.RESIDENT, 1));
        blocks.put(new BlockOffset(0, -1, 0),
                new BlueprintBlock(Material.STONE_BRICKS, BlockRole.RESIDENT, 2));
        BlueprintPlan plan = new BlueprintPlan("underground_test", 2, "Подземный ярус", blocks);
        check(FoundationSupportPolicy.hasPlannedSupport(plan, foundation, 1),
                "Опора следующей стадии того же чертежа должна учитываться");
        check(!FoundationSupportPolicy.hasPlannedSupport(plan, foundation, 3),
                "Завершённая ранее стадия не должна маскировать разрушенную опору");
        check(!FoundationSupportPolicy.hasPlannedSupport(
                        new BlueprintPlan("plain", 1, "Основание", Map.of(
                                foundation, new BlueprintBlock(Material.STONE_BRICKS, BlockRole.RESIDENT, 1))),
                        foundation, 1),
                "Пустота без запланированной опоры должна оставаться ошибкой рельефа");
        System.out.println("ConstructionPoliciesSmoke OK: физические стадии и опоры проверены");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
