package ru.neverland.archaeology.api;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public interface TownyArchaeologyApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("artifactName", "available", "consumeForWonder", "createArtifact", "isAuthenticArtifact", "missingForWonder", "museum", "wonderRequirements"); }
    MuseumSnapshot museum(UUID townId);
    int available(UUID townId, String artifactId);
    Map<String, Integer> wonderRequirements(String wonderId);
    Map<String, Integer> missingForWonder(UUID townId, String wonderId);
    boolean consumeForWonder(UUID townId, String wonderId, UUID actorId);
    String artifactName(String artifactId);
    ItemStack createArtifact(String artifactId);
    boolean isAuthenticArtifact(ItemStack item);
}
