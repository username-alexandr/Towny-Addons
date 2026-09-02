package ru.neverland.mintespionage.model;

import org.bukkit.Material;
import java.util.List;

public record OperationDefinition(String id, String name, Material icon, String itemsAdderIcon, int slot,
                                  List<String> description, double cost, long durationMillis, long cooldownMillis,
                                  long reportLifetimeMillis, double successChance, double detectionChance) { }
