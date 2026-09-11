package ru.neverland.townybuilds.storage;
import java.util.UUID;
import java.io.IOException;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.*;
import ru.neverland.townybuilds.api.MunicipalStorageApi;
import ru.neverland.townybuilds.data.DataStore;
import com.palmergames.bukkit.towny.TownyAPI;

public final class MunicipalStorageService implements MunicipalStorageApi {
    private final DataStore data;
    public MunicipalStorageService(Plugin plugin,DataStore data){this.data=data;Bukkit.getServicesManager().register(MunicipalStorageApi.class,this,plugin,ServicePriority.Normal);}
    private void thread(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Склад требует основного потока");}
    private MarketTransactions.Access access(){return new MarketTransactions.Access(){
        public ItemStack[] read(UUID town){return data.town(town).storage();}public void write(UUID town,ItemStack[] items){data.town(town).setStorage(items,items.length);data.markDirty();}
        public boolean busy(UUID town){return data.storageBusy(town,"warehouse");}public void commit()throws IOException{data.saveOrThrow();}
    };}
    public int capacity(UUID town,ItemStack sample){thread();if(TownyAPI.getInstance().getTown(town)==null)return -1;if(data.storageBusy(town,"warehouse"))return -2;return MunicipalDeposits.capacity(data.town(town).storage(),sample);}
    public String deposit(UUID town,UUID operation,ItemStack sample,int amount)throws IOException{thread();if(TownyAPI.getInstance().getTown(town)==null)return "CITY_MISSING";return MunicipalDeposits.deposit(data.town(town),access(),operation,sample,amount);}
    public void acknowledge(UUID town,UUID operation)throws IOException{thread();MunicipalDeposits.acknowledge(data.town(town),access(),operation);}
}
