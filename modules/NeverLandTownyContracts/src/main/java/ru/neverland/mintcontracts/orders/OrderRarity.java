package ru.neverland.mintcontracts.orders;

import net.kyori.adventure.text.format.NamedTextColor;

public enum OrderRarity {
    COMMON("Обычный", "Лёгкая", "§f", NamedTextColor.WHITE, 60),
    UNCOMMON("Необычный", "Умеренная", "§a", NamedTextColor.GREEN, 30),
    RARE("Редкий", "Сложная", "§9", NamedTextColor.BLUE, 12),
    EPIC("Эпический", "Очень сложная", "§d", NamedTextColor.LIGHT_PURPLE, 4),
    LEGENDARY("Легендарный", "Высшая", "§6", NamedTextColor.GOLD, 1);

    public final String label, difficulty, legacyColor;
    public final NamedTextColor color;
    public final int defaultWeight;

    OrderRarity(String label, String difficulty, String legacyColor, NamedTextColor color, int weight) {
        this.label = label; this.difficulty = difficulty; this.legacyColor = legacyColor;
        this.color = color; this.defaultWeight = weight;
    }
}
