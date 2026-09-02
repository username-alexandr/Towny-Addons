package ru.neverland.mintcamps.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record BlockSnapshot(Material material, String blockData, List<ItemStack> inventory) {
}
