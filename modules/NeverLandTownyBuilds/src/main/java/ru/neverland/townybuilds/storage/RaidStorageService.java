package ru.neverland.townybuilds.storage;

import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townybuilds.data.DataStore;

public final class RaidStorageService implements RaidStorageApi {
    private final JavaPlugin plugin;private final DataStore data;private final BuildingStorageService views;
    public RaidStorageService(JavaPlugin plugin,DataStore data,BuildingStorageService views){this.plugin=plugin;this.data=data;this.views=views;Bukkit.getServicesManager().register(RaidStorageApi.class,this,plugin,ServicePriority.Normal);}
    public boolean healthy(){try(var guard=SuiteState.guard()){return plugin.isEnabled()&&!SuiteState.frozen(plugin.getName())&&data.writable();}}
    private void check(){ApiServices.primaryThread();if(!healthy())throw new IllegalStateException("Склад добычи временно недоступен");}
    public Optional<RegisteredBuildingBlock> core(UUID town){try(var guard=SuiteState.guard()){
        check();if(town==null||TownyAPI.getInstance().getTown(town)==null)return Optional.empty();
        var state=data.town(town);var site=state.constructionSite("town_hall");if(site==null)return Optional.empty();
        int level=Math.min(state.level("town_hall"),site.completedStage());if(level<1)return Optional.empty();
        var plan=BlueprintCatalog.sitePlan(site,level,new BuildingBlueprintGenerator());if(plan==null)return Optional.empty();
        return plan.blocks().entrySet().stream().filter(e->e.getValue().material()==Material.LECTERN)
            .min(Comparator.<Map.Entry<BlockOffset,BlueprintBlock>>comparingInt(e->e.getValue().stage()).thenComparingInt(e->e.getKey().y()).thenComparingInt(e->e.getKey().x()).thenComparingInt(e->e.getKey().z()))
            .map(e->{var at=site.location(null,e.getKey());return new RegisteredBuildingBlock(site.worldId(),at.getBlockX(),at.getBlockY(),at.getBlockZ(),"town_hall","LECTERN");});
    }}
    private RaidStockTransactions.Access access(){return new RaidStockTransactions.Access(){
        public ItemStack[] read(UUID town){return data.town(town).storage();}
        public void write(UUID town,ItemStack[] items){data.town(town).setStorage(items,items.length);data.markDirty();}
        public RaidStock get(UUID op){return data.raidStocks().get(op);}
        public void put(UUID op,RaidStock stock){data.raidStock(op,stock);}
        public void commit()throws IOException{data.saveOrThrow();}
    };}
    private boolean close(UUID town,UUID operation){if(!data.freezeWarehouse(town,operation))return false;SuiteScheduler.get().runTaskLater(plugin,()->data.thawWarehouse(town,operation),40);if(data.storageSessionBusy(town,"warehouse")){views.closeWarehouse(town);return false;}return true;}
    public Map<String,Object> reserve(UUID op,UUID defender,UUID attacker,UUID actor,int bps)throws IOException {try(var guard=SuiteState.guard()){
        check();var prior=data.raidStocks().get(op);
        if(prior!=null)return RaidStockTransactions.reserve(access(),op,defender,attacker,actor,bps).snapshot();
        if(TownyAPI.getInstance().getTown(defender)==null||TownyAPI.getInstance().getTown(attacker)==null)return Map.of("state","CITY_MISSING");
        if(!close(defender,op))return Map.of("state","BUSY");
        try{return RaidStockTransactions.reserve(access(),op,defender,attacker,actor,bps).snapshot();}finally{data.thawWarehouse(defender,op);}
    }}
    public Map<String,Object> settle(UUID op,boolean deliver)throws IOException {try(var guard=SuiteState.guard()){
        check();var old=data.raidStocks().get(op);if(old==null)return Map.of("state","MISSING");if(!old.state().equals("RESERVED")){audit(old);return old.snapshot();}
        UUID target=deliver?old.attacker():old.defender();if(TownyAPI.getInstance().getTown(target)==null)return Map.of("state","CITY_MISSING");
        if(!close(target,op))return Map.of("state","BUSY");
        try{String state=RaidStockTransactions.settle(access(),op,deliver);var saved=data.raidStocks().get(op);if(state.equals("DELIVERED")||state.equals("RETURNED"))audit(saved);return state.equals(saved.state())?saved.snapshot():Map.of("state",state);}finally{data.thawWarehouse(target,op);}
    }}
    public Map<String,Object> status(UUID op){try(var guard=SuiteState.guard()){check();var old=data.raidStocks().get(op);return old==null?Map.of("state","MISSING"):old.snapshot();}}
    private void audit(RaidStock stock)throws IOException{var items=stock.cargo();for(int i=0;i<items.length;i++)AuditTrail.require(AuditTrail.record(plugin,"raid-stock:"+i,stock.id().toString(),"RAID_LOOT_RESOURCES",stock.state(),AuditTrail.player(stock.actor()),AuditTrail.town(stock.defender()),AuditTrail.town(stock.state().equals("DELIVERED")?stock.attacker():stock.defender()),AuditTrail.item(items[i]),items[i].getAmount(),"","Захваченная доля склада; bps="+stock.basisPoints()));}
}
