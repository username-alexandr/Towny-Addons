package ru.neverland.mintexpeditions.model;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import java.util.List;
public record ExpeditionDefinition(String id, String name, ExpeditionType type, ObjectiveType objective,
                                   Material icon, int slot, int minCampLevel, long durationSeconds,
                                   int minDistance, int maxDistance, int goal, Material objectiveMaterial,
                                   EntityType mob, int mobCount, List<ItemAmount> costs, List<ItemAmount> rewards,
                                   double moneyPerParticipant, List<String> description) {}
