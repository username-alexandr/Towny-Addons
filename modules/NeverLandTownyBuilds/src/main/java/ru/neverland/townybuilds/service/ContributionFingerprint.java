package ru.neverland.townybuilds.service;

import java.util.Base64;
import org.bukkit.inventory.ItemStack;

/** Project requirements may contain thousands of items; NBT only carries a one-item sample. */
public final class ContributionFingerprint {
    private ContributionFingerprint() {}
    public static String item(ItemStack stack){
        if(stack==null||stack.getAmount()<=0||stack.getType().isAir())return "-";
        int amount=stack.getAmount();ItemStack sample=stack.clone();sample.setAmount(1);
        return amount+"@"+Base64.getEncoder().encodeToString(sample.serializeAsBytes());
    }
}
