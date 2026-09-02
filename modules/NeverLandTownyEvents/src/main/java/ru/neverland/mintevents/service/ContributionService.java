package ru.neverland.mintevents.service;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.ContributionRule;

public final class ContributionService {
    public record Result(int amount, int points) {}

    public Result contribute(Player player, ContributionRule rule, boolean all, ActiveEvent event) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int wanted = all ? Integer.MAX_VALUE : 1;
        int removed = 0;
        for (int slot = 0; slot < contents.length && removed < wanted; slot++) {
            ItemStack stack = contents[slot];
            if (!matches(rule, stack)) continue;
            int take = Math.min(stack.getAmount(), wanted - removed);
            removed += take;
            if (take == stack.getAmount()) player.getInventory().setItem(slot, null);
            else stack.setAmount(stack.getAmount() - take);
        }
        if (removed == 0) return new Result(0, 0);
        returnContainers(player, rule, removed);
        double bonus = 1.0 + event.protection() * 0.5;
        int points = Math.max(1, (int) Math.round(removed * rule.points() * bonus));
        return new Result(removed, points);
    }

    private void returnContainers(Player player, ContributionRule rule, int amount) {
        Material returned = switch (rule.key().toUpperCase()) {
            case "WATER_BUCKET", "MILK_BUCKET" -> Material.BUCKET;
            case "HONEY_BOTTLE", "FIRE_RESISTANCE" -> Material.GLASS_BOTTLE;
            default -> null;
        };
        if (returned == null) return;
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(returned.getMaxStackSize(), remaining);
            ItemStack stack = new ItemStack(returned, stackSize);
            player.getInventory().addItem(stack).values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= stackSize;
        }
    }

    private boolean matches(ContributionRule rule, ItemStack stack) {
        if (stack == null || stack.getType() != rule.material()) return false;
        if (!rule.key().equalsIgnoreCase("FIRE_RESISTANCE")) return true;
        if (!(stack.getItemMeta() instanceof PotionMeta potion)) return false;
        PotionType type = potion.getBasePotionType();
        return type == PotionType.FIRE_RESISTANCE || type == PotionType.LONG_FIRE_RESISTANCE;
    }
}
