package ru.neverland.townybuilds.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Единые правила сравнения ресурсов в инвентаре, фонде и меню строительства. */
public final class ResourceMatcher {
    private ResourceMatcher() {
    }

    public static boolean matches(ItemStack candidate, ItemStack required) {
        if (candidate == null || required == null
                || isAir(candidate.getType()) || isAir(required.getType())) {
            return false;
        }
        if (candidate.isSimilar(required)) {
            return true;
        }

        // Ресурс из resources-base64 или ItemsAdder обязан совпасть вместе со всеми
        // компонентами. Для обычной записи MATERIAL:amount достаточно материала:
        // Paper может добавить ванильному стаку служебные компоненты, из-за которых
        // ItemStack#isSimilar возвращает false (в частности для DEEPSLATE_TILES).
        if (required.hasItemMeta() || candidate.getType() != required.getType()) {
            return false;
        }
        return hasNoCustomIdentity(candidate);
    }

    private static boolean hasNoCustomIdentity(ItemStack item) {
        if (!item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;
        return !meta.hasCustomName()
                && !meta.hasDisplayName()
                && !meta.hasLore()
                && !meta.hasCustomModelData()
                && !meta.hasEnchants()
                && meta.getPersistentDataContainer().getKeys().isEmpty();
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }
}
