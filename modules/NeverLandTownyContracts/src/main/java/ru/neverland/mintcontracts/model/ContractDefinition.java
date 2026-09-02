package ru.neverland.mintcontracts.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public record ContractDefinition(String id, String name, ContractType type, Material icon, int slot,
                                 List<String> description, String target, ItemStack deliveryItem,
                                 int goal, double reward, long durationSeconds) {}
