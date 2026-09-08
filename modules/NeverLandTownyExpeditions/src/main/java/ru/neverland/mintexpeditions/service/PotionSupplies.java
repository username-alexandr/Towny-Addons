package ru.neverland.mintexpeditions.service;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class PotionSupplies {
    private PotionSupplies() {}

    public static List<String> migrate(String expedition, List<String> costs) {
        if (!expedition.equals("royal_galleon") && !expedition.equals("drowned_temple")) return costs;
        if (costs.stream().noneMatch(s -> s.matches("(?i)POTION(?::[0-9]+)?"))) return costs;
        List<String> result = new ArrayList<>();
        for (String cost : costs) {
            if (!cost.matches("(?i)POTION(?::[0-9]+)?")) result.add(cost);
        }
        result.add("POTION:WATER_BREATHING:2");
        result.add("POTION:NIGHT_VISION:2");
        return result;
    }

    public static ItemStack create(String key) {
        String[] parts = key.split(":");
        if (parts.length != 2 || !parts[0].equalsIgnoreCase("POTION")) return null;
        PotionType type = PotionType.valueOf(parts[1].toUpperCase(java.util.Locale.ROOT));
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    public static String name(PotionType type) {
        if (type == null) return null;
        return switch (type) {
            case WATER_BREATHING -> "Зелье подводного дыхания (3:00)";
            case LONG_WATER_BREATHING -> "Зелье подводного дыхания (8:00)";
            case NIGHT_VISION -> "Зелье ночного зрения (3:00)";
            case LONG_NIGHT_VISION -> "Зелье ночного зрения (8:00)";
            default -> null;
        };
    }
}
