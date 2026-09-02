package ru.neverland.archaeology.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record SiteDefinition(String id, String name, Set<String> biomes, Material suspiciousBlock, Set<Material> replaceable,
                             int minimumBlocks, int maximumBlocks, Map<String, Integer> artifactWeights) {
    public int totalWeight() { return artifactWeights.values().stream().mapToInt(Integer::intValue).sum(); }
    public List<String> artifacts() { return List.copyOf(artifactWeights.keySet()); }
}
