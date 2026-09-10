package ru.neverland.townybuilds.storage;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.*;
import ru.neverland.townybuilds.api.MarketStorageApi;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.integration.TownyHook;
public final class MarketStorageService implements MarketStorageApi {
    private final DataStore data;private final TownyHook towny=new TownyHook();
    public MarketStorageService(Plugin plugin,DataStore data){this.data=data;Bukkit.getServicesManager().register(MarketStorageApi.class,this,plugin,ServicePriority.Normal);}
    private void thread(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Склад требует основного потока");}
    private MarketTransactions.Access access(){return new MarketTransactions.Access(){
        public ItemStack[] read(UUID id){return data.town(id).storage();}public void write(UUID id,ItemStack[] items){data.town(id).setStorage(items,data.town(id).storage().length);data.markDirty();}
        public boolean busy(UUID id){return data.storageBusy(id,"warehouse");}public void commit()throws java.io.IOException{data.saveOrThrow();}
    };}
    public int level(UUID town,String project){thread();return towny.town(town)==null?0:data.town(town).operationalLevel(project);}
    public String open(UUID town,UUID lot,ItemStack item,int amount)throws Exception{thread();if(towny.town(town)==null)return "CITY_MISSING";return MarketTransactions.open(data.town(town),access(),lot,item,amount);}
    public Map<String,Object> snapshot(UUID town,UUID lot){thread();var s=data.town(town).marketStock().get(lot);if(s==null)return Map.of();Map<String,String> holds=new HashMap<>();s.holds().forEach((id,h)->holds.put(id.toString(),h.status()));return Map.of("available",s.available(),"open",s.open(),"holds",Map.copyOf(holds));}
    public String reserve(UUID town,UUID lot,UUID order,UUID buyer,boolean city,int amount)throws Exception{thread();if(towny.town(town)==null||city&&towny.town(buyer)==null)return "CITY_MISSING";return MarketTransactions.reserve(data.town(town),access(),lot,order,buyer,city,amount);}
    public String deliver(UUID town,UUID lot,UUID order)throws Exception{thread();var s=data.town(town).marketStock().get(lot);var h=s==null?null:s.holds().get(order);if(h!=null&&h.city()&&towny.town(h.buyer())==null)return "CITY_MISSING";return MarketTransactions.deliver(data.town(town),access(),lot,order);}
    public String refund(UUID town,UUID lot,UUID order)throws Exception{thread();return MarketTransactions.refund(data.town(town),access(),lot,order);}
    public String close(UUID town,UUID lot)throws Exception{thread();return MarketTransactions.close(data.town(town),access(),lot);}
    public void acknowledge(UUID town,UUID lot,UUID order)throws Exception{thread();MarketTransactions.acknowledge(data.town(town),access(),lot,order);}
    public void forget(UUID town,UUID lot)throws Exception{thread();MarketTransactions.forget(data.town(town),access(),lot);}
    public String claim(Player player,UUID town,UUID lot,UUID order)throws Exception{thread();if(!player.isOnline()||player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR)return "MODE";
        return MarketTransactions.claim(data.town(town),access(),lot,order,new MarketTransactions.Inventory(){public UUID owner(){return player.getUniqueId();}public ItemStack[] read(){return player.getInventory().getStorageContents();}public void write(ItemStack[] items){player.getInventory().setStorageContents(items);}public void save(){player.saveData();}});
    }
    public void resolveClaim(UUID town,UUID lot,UUID order,boolean received)throws Exception{thread();MarketTransactions.resolveClaim(data.town(town),access(),lot,order,received);}
}
