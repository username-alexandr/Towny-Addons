package ru.neverland.mintcamps.model;

import org.bukkit.Material;

import java.util.Map;

public record StyleDefinition(String id, String name, Map<String, Material> materials) {
    public Material material(String role) {
        return materials.getOrDefault(role, Material.OAK_PLANKS);
    }

    public boolean nether() {
        return id.equalsIgnoreCase("nether");
    }
}
