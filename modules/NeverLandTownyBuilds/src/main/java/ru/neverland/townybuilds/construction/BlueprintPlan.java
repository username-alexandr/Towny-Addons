package ru.neverland.townybuilds.construction;

import java.util.LinkedHashMap;
import java.util.Map;

public record BlueprintPlan(String projectId, int level, String stageName,
                            Map<BlockOffset, BlueprintBlock> blocks) {
    public BlueprintPlan {
        blocks = Map.copyOf(new LinkedHashMap<>(blocks));
    }

    public long residentBlocks(int fromStage) {
        return blocks.values().stream()
                .filter(block -> block.role() == BlockRole.RESIDENT && block.stage() >= fromStage)
                .count();
    }

    public long decorationBlocks(int fromStage) {
        return blocks.values().stream()
                .filter(block -> block.role() == BlockRole.DECORATION && block.stage() >= fromStage)
                .count();
    }
}
