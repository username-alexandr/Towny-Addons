package ru.neverland.archaeology.service;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.archaeology.api.MuseumSnapshot;
import ru.neverland.archaeology.api.TownyArchaeologyApi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

public final class ArchaeologyApiService implements TownyArchaeologyApi {
    private final JavaPlugin plugin; private final ArchaeologyRegistry registry; private final ArtifactService artifacts; private final MuseumService museums;
    public ArchaeologyApiService(JavaPlugin plugin, ArchaeologyRegistry registry, ArtifactService artifacts, MuseumService museums) { this.plugin = plugin; this.registry = registry; this.artifacts = artifacts; this.museums = museums; }
    @Override public MuseumSnapshot museum(UUID townId) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return sync(() -> museums.snapshot(townId)); } } @Override public int available(UUID townId, String artifactId) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return sync(() -> museums.available(townId, artifactId)); } }
    @Override public Map<String, Integer> wonderRequirements(String wonderId) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return sync(() -> registry.wonderRequirements(wonderId)); } } @Override public Map<String, Integer> missingForWonder(UUID townId, String wonderId) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return sync(() -> museums.missing(townId, wonderId)); } }
    @Override public boolean consumeForWonder(UUID townId, String wonderId, UUID actorId) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return sync(() -> museums.consumeForWonder(townId, wonderId, actorId)); } } @Override public String artifactName(String artifactId) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return sync(() -> artifacts.displayName(artifactId)); } }
    @Override public ItemStack createArtifact(String artifactId) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return sync(() -> artifacts.create(artifactId)); } } @Override public boolean isAuthenticArtifact(ItemStack item) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return sync(() -> artifacts.authentic(item)); } }
    private <T> T sync(Callable<T> operation) { try { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return operation.call(); } } catch (Exception exception) { throw new IllegalStateException("NeverLandTownyArchaeology API operation failed", exception); } }
}
