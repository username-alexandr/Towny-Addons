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
    private void thread(){if(!ru.neverland.core.SuiteState.held())throw new IllegalStateException("Склад требует основного потока");}
    private MarketTransactions.Access access(){return new MarketTransactions.Access(){
        public ItemStack[] read(UUID town){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return data.town(town).storage();} }public void write(UUID town,ItemStack[] items){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {data.town(town).setStorage(items,items.length);data.markDirty();} }
        public boolean busy(UUID town){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return data.storageBusy(town,"warehouse");} }public void commit()throws IOException{ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {data.saveOrThrow();} }
    };}
    public int capacity(UUID town,ItemStack sample){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {thread();if(TownyAPI.getInstance().getTown(town)==null)return -1;if(data.storageBusy(town,"warehouse"))return -2;return MunicipalDeposits.capacity(data.town(town).storage(),sample);} }
    public String deposit(UUID town,UUID operation,ItemStack sample,int amount)throws IOException{ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {thread();if(TownyAPI.getInstance().getTown(town)==null)return "CITY_MISSING";return MunicipalDeposits.deposit(data.town(town),access(),operation,sample,amount);} }
    public void acknowledge(UUID town,UUID operation)throws IOException{ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {thread();MunicipalDeposits.acknowledge(data.town(town),access(),operation);} }
}
