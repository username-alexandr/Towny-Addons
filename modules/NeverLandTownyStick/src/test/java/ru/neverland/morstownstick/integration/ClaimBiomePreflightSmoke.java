package ru.neverland.morstownstick.integration;

/** Towny's biome thresholds reject equality, not only values above the limit. */
public final class ClaimBiomePreflightSmoke {
    public static void main(String[] args) {
        if (!ClaimBiomePreflight.belowThreshold(1, 16, 0.10))
            throw new AssertionError("A plot below Towny's threshold must be allowed");
        if (ClaimBiomePreflight.belowThreshold(2, 16, 0.125))
            throw new AssertionError("A plot exactly at Towny's threshold must be rejected");
        if (ClaimBiomePreflight.belowThreshold(3, 16, 0.125))
            throw new AssertionError("A plot over Towny's threshold must be rejected");
        if (!ClaimBiomePreflight.belowThreshold(0, 256, 0.1))
            throw new AssertionError("A plot without blocked biomes must be allowed");
        System.out.println("Folia biome threshold semantics passed");
    }
}
