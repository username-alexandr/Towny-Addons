package ru.neverland.archaeology.model;

import org.bukkit.Material;

import java.util.List;

public record ArtifactDefinition(String id, String name, String rarity, Material material, String itemsAdderIcon, int points, List<String> lore) { }
