package ru.neverland.mintcamps.model;

import org.bukkit.Material;

import java.util.Map;

public record StyleDefinition(String id, String name, Map<String, Material> materials) {
    public Material material(String role) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return materials.getOrDefault(role, Material.OAK_PLANKS);
    } }

    public boolean nether() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return id.equalsIgnoreCase("nether");
    } }
}
