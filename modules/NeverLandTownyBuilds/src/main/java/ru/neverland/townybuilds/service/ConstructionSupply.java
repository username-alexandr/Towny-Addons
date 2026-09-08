package ru.neverland.townybuilds.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** One vanilla slab resource for legacy full deepslate tile requirements. */
public final class ConstructionSupply {
    private ConstructionSupply() {}

    public static Material material(Material blueprintMaterial) {
        return blueprintMaterial == Material.DEEPSLATE_TILES
                ? Material.COBBLED_DEEPSLATE_SLAB : blueprintMaterial;
    }

    public static ItemStack migrate(ItemStack item) {
        ItemStack result = item.clone();
        if (item.getType() == Material.DEEPSLATE_TILES && !item.hasItemMeta()) {
            result.setType(Material.COBBLED_DEEPSLATE_SLAB);
        }
        return result;
    }
}
