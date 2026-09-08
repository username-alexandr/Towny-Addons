package ru.neverland.townybuilds.service;

import java.util.ArrayList;
import java.util.List;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;

/** Stage volume sets the budget; configured quantities retain the material proportions. */
public final class ResourceBudget {
    public record Cost(String material, int amount) {}
    private ResourceBudget() {}

    public static List<Cost> balance(BuildingBlueprintGenerator generator, String project, int level,
                                     boolean wonder, List<Cost> original) {
        int stage = Math.min(level, generator.maximumStage(project));
        var plan = generator.generate(project, stage);
        if (plan == null || original.isEmpty()) return original;
        long blocks = plan.blocks().values().stream().filter(b -> b.stage() == stage).count();
        double budget = Math.max(32, blocks) * (wonder ? 1.35 : 1.15)
                * (1 + 0.25 * Math.max(0, level - stage));
        return scale(original, budget);
    }

    public static List<Cost> scale(List<Cost> original, double budget) {
        double previous = original.stream().mapToDouble(c -> (double) c.amount() * weight(c.material())).sum();
        if (previous <= 0) return original;
        List<Cost> result = new ArrayList<>();
        for (Cost cost : original) {
            if (cost.amount() <= 0) continue;
            double raw = cost.amount() * budget / previous;
            int unit = raw > 64 ? 8 : raw > 8 ? 4 : 1;
            int amount = Math.max(1, (int) Math.ceil(raw / unit) * unit);
            result.add(new Cost(cost.material(), amount));
        }
        return List.copyOf(result);
    }

    public static int weight(String material) {
        return switch (material) {
            case "NETHER_STAR", "CONDUIT" -> 1024;
            case "NETHERITE_BLOCK" -> 2304;
            case "NETHERITE_INGOT" -> 256;
            case "DIAMOND_BLOCK" -> 144;
            case "EMERALD_BLOCK" -> 72;
            case "GOLD_BLOCK" -> 54;
            case "IRON_BLOCK" -> 27;
            case "COPPER_BLOCK", "CUT_COPPER", "OXIDIZED_COPPER" -> 9;
            case "DIAMOND", "GHAST_TEAR", "ECHO_SHARD" -> 16;
            case "EMERALD", "ENDER_EYE", "SEA_LANTERN" -> 8;
            case "GOLD_INGOT", "BLAZE_ROD" -> 6;
            case "OBSIDIAN", "CRYING_OBSIDIAN", "ENDER_PEARL" -> 4;
            case "IRON_INGOT", "QUARTZ_BLOCK", "SMOOTH_QUARTZ", "QUARTZ_BRICKS" -> 3;
            default -> 1;
        };
    }
}
