package ru.neverland.townybuilds.storage;
import java.util.*;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.api.MunicipalReceipt;

public final class MunicipalDeposits {
    private MunicipalDeposits(){}
    public static int capacity(ItemStack[] items,ItemStack sample){int max=Math.min(64,sample.getMaxStackSize()),n=0;for(var item:items){if(!StockMath.present(item))n+=max;else if(item.isSimilar(sample))n+=Math.max(0,max-item.getAmount());}return n;}
    public static String deposit(TownData town,MarketTransactions.Access a,UUID id,ItemStack sample,int amount)throws IOException {
        var receipt=new MunicipalReceipt(id,sample,amount);var old=town.municipalReceipts().get(id);
        if(old!=null){if(old.amount()!=amount||!old.sample().isSimilar(sample))throw new IllegalArgumentException("ID поставки занят");return "DELIVERED";}
        if(a.busy(town.townId()))return "BUSY";
        ItemStack[] before=a.read(town.townId()),work=StockMath.copy(before);if(capacity(before,sample)<amount)return "FULL";
        java.util.List<ItemStack> cargo=new java.util.ArrayList<>();int left=amount;while(left>0){ItemStack part=sample.clone();int n=Math.min(left,Math.min(64,sample.getMaxStackSize()));part.setAmount(n);cargo.add(part);left-=n;}
        if(!StockMath.insert(work,cargo.toArray(ItemStack[]::new)))return "FULL";
        a.write(town.townId(),work);town.putMunicipalReceipt(receipt);
        try{a.commit();}catch(IOException|RuntimeException ex){town.removeMunicipalReceipt(id);a.write(town.townId(),before);throw ex;}
        return "DELIVERED";
    }
    public static void acknowledge(TownData town,MarketTransactions.Access a,UUID id)throws IOException {
        var old=town.municipalReceipts().get(id);if(old==null)return;town.removeMunicipalReceipt(id);
        try{a.commit();}catch(IOException|RuntimeException ex){town.putMunicipalReceipt(old);throw ex;}
    }
}
