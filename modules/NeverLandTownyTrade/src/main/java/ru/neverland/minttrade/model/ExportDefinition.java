package ru.neverland.minttrade.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public record ExportDefinition(String id, String name, Material icon, int slot, List<String> description,
                               String itemKey, ItemStack item, int amount, double price) {}
