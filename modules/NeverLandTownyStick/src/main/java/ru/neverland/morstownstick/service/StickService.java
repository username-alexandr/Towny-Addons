package ru.neverland.morstownstick.service;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.morstownstick.util.ColorUtil;

import java.util.List;

public final class StickService {
    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final NamespacedKey legacyKey;

    public StickService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "territory_selector");
        this.legacyKey = NamespacedKey.fromString("morstownstick:territory_selector");
    }

    public ItemStack create() {
        String configured = plugin.getConfig().getString("stick.material", "STICK");
        Material material = Material.matchMaterial(configured);
        if (material == null || !material.isItem()) material = Material.STICK;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtil.legacy(plugin.getConfig().getString("stick.name", "&#55FF55Палка выбора территории")));
        List<String> lore = plugin.getConfig().getStringList("stick.lore").stream().map(ColorUtil::legacy).toList();
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(plugin.getConfig().getBoolean("stick.glint", true));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSelector(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        if (value == null && legacyKey != null) value = item.getItemMeta().getPersistentDataContainer().get(legacyKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public boolean has(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isSelector(item)) return true;
        return false;
    }

    public void give(Player player) {
        ItemStack item = create();
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}
