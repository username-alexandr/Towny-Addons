package ru.neverland.townyideologies.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record IdeologyDefinition(
        String id,
        String name,
        Material material,
        String itemsAdderIcon,
        int slot,
        double selectionPrice,
        Map<Integer, Double> upgradePrices,
        List<String> description,
        List<String> bonusDescription
) {
    public static final int MAX_LEVEL = 5;

    public double priceForLevel(int level) {
        return level <= 1 ? selectionPrice : upgradePrices.getOrDefault(level, -1.0D);
    }
}
