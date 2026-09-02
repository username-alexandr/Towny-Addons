package ru.neverland.mintexpeditions.model;
import org.bukkit.Material;
import java.util.Map;
import java.util.Set;
public record SitePlan(Map<BlockPos, Material> blocks, Set<BlockPos> objectives) {}
