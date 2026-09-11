package ru.neverland.townybuilds.storage;
import java.util.*;import java.io.IOException;import org.bukkit.*;import org.bukkit.inventory.ItemStack;import org.bukkit.plugin.*;
import ru.neverland.townybuilds.api.WarehouseApi;import ru.neverland.townybuilds.data.DataStore;import com.palmergames.bukkit.towny.TownyAPI;
public final class WarehouseService implements WarehouseApi {
    private final DataStore data;
    public WarehouseService(Plugin plugin,DataStore data){this.data=data;Bukkit.getServicesManager().register(WarehouseApi.class,this,plugin,ServicePriority.Normal);}
    public Map<String,Object> take(UUID town,ItemStack sample,int amount)throws IOException{return change(town,sample,amount,false);}
    public Map<String,Object> deposit(UUID town,ItemStack sample,int amount)throws IOException{return change(town,sample,amount,true);}
    private Map<String,Object> result(String status,int amount){return Map.of("status",status,"amount",amount);}
    private Map<String,Object> change(UUID town,ItemStack sample,int amount,boolean incoming)throws IOException{
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Склад требует основного потока");if(sample==null||sample.getType().isAir()||amount<1||amount>1_000_000)throw new IllegalArgumentException("Неверный груз");
        if(!data.writable()||TownyAPI.getInstance().getTown(town)==null)return result("UNAVAILABLE",0);if(data.storageBusy(town,"warehouse"))return result("BUSY",0);
        var state=data.town(town);var before=state.storage();var work=StockMath.copy(before);
        if(incoming){var cargo=sample.clone();cargo.setAmount(amount);if(!StockMath.insert(work,new ItemStack[]{cargo}))return result("FULL",0);}else{int have=StockMath.count(work,sample);if(have<amount)return result("INSUFFICIENT",have);StockMath.take(work,sample,amount,0);}
        state.setStorage(work,work.length);data.markDirty();try{data.saveOrThrow();}catch(IOException|RuntimeException ex){state.setStorage(before,before.length);throw ex;}return result("SUCCESS",amount);
    }
}
