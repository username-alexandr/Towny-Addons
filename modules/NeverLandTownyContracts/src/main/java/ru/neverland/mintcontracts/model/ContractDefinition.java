package ru.neverland.mintcontracts.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public record ContractDefinition(String id, String name, ContractType type, Material icon, int slot,
                                 List<String> description, String target, ItemStack deliveryItem,
                                 int goal, double reward, long durationSeconds) {
    public ContractDefinition {
        if(id==null||!id.matches("[a-z0-9_-]{1,80}")||name==null||name.length()>160||type==null||icon==null||target==null||target.length()>256
                ||goal<1||goal>1_000_000||!Double.isFinite(reward)||reward<0||reward>1_000_000_000||durationSeconds<1||durationSeconds>31_536_000)
            throw new IllegalArgumentException("Некорректные условия контракта");
        try{java.math.BigDecimal.valueOf(reward).movePointRight(2).longValueExact();}catch(ArithmeticException ex){throw new IllegalArgumentException("Награда должна быть указана с точностью до копейки");}
        description=List.copyOf(description);deliveryItem=deliveryItem==null?null:deliveryItem.clone();
        if(type==ContractType.DELIVERY&&(deliveryItem==null||deliveryItem.getType().isAir()))throw new IllegalArgumentException("Не задан предмет поставки");
    }
    @Override public ItemStack deliveryItem(){return deliveryItem==null?null:deliveryItem.clone();}
}
