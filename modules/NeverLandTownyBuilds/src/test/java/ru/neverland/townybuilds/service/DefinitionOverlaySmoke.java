package ru.neverland.townybuilds.service;

import java.util.Map;
import org.bukkit.Material;

public final class DefinitionOverlaySmoke {
    private DefinitionOverlaySmoke() {
    }

    public static void main(String[] args) {
        check(DefinitionRegistry.resolveResourceMaterial("CHAIN") == Material.IRON_CHAIN,
                "legacy chain requirements must remain payable after updating");
        check(DefinitionRegistry.resolveResourceMaterial("chain") == Material.IRON_CHAIN,
                "legacy material lookup must remain case-insensitive");
        check(DefinitionRegistry.resolveResourceMaterial("IRON_CHAIN") == Material.IRON_CHAIN,
                "current chain requirements must remain unchanged");
        check(DefinitionRegistry.resolveResourceMaterial("STONE") == Material.STONE,
                "unrelated requirements must retain their material");
        check(DefinitionRegistry.resolveResourceMaterial("NOT_A_MATERIAL") == null,
                "unknown requirements must not silently become another material");
        Map<String, Integer> defaults = Map.of("quarry", 5, "foundry", 4, "mint", 3);
        Map<String, Integer> custom = Map.of("town_hall", 2);

        check(DefinitionRegistry.resolveRequirements(false, Map.of(), defaults).equals(defaults),
                "old projects.yml must inherit newly introduced prerequisites");
        check(DefinitionRegistry.resolveRequirements(true, custom, defaults).equals(custom),
                "explicit administrator prerequisites must override defaults");
        check(DefinitionRegistry.resolveRequirements(true, Map.of(), defaults).isEmpty(),
                "an explicitly empty requirement map must be able to disable defaults");
        check(DefinitionRegistry.resolveRequirements(false, custom, Map.of()).equals(custom),
                "custom projects without built-in defaults must retain parsed prerequisites");

        System.out.println("Definition overlay smoke test passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
