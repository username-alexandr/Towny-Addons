package ru.neverland.reputation.model;

import org.bukkit.Material;
import java.util.List;
import java.util.Set;

public record ReputationTier(
        String id,
        String name,
        int minimumScore,
        Material material,
        String itemsAdderIcon,
        List<String> description,
        Set<String> privileges,
        double tradeDiscountPercent,
        double rewardMultiplier
) { }
