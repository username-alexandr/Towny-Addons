package ru.neverland.mintexpeditions.model;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;
public record SiteSnapshot(Material material, String blockData, List<ItemStack> inventory) {}
