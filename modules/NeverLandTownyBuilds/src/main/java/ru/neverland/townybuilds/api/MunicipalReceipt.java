package ru.neverland.townybuilds.api;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
public record MunicipalReceipt(UUID id,ItemStack sample,int amount) {
    public MunicipalReceipt{if(id==null||!ru.neverland.townybuilds.storage.StockMath.present(sample)||amount<1||amount>1_000_000)throw new IllegalArgumentException("Некорректная поставка");sample=sample.clone();sample.setAmount(1);}
    @Override public ItemStack sample(){return sample.clone();}
}
