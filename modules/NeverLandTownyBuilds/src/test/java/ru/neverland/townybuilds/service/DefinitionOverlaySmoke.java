package ru.neverland.townybuilds.service;

import java.util.Map;

public final class DefinitionOverlaySmoke {
    private DefinitionOverlaySmoke() {
    }

    public static void main(String[] args) {
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
