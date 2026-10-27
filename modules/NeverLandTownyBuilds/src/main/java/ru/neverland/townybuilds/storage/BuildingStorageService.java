package ru.neverland.townybuilds.storage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.service.DefinitionRegistry;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.construction.BuildingFootprints;
import java.util.*;
import java.io.IOException;

public final class BuildingStorageService implements BuildingStorageApi,Listener {
    private final JavaPlugin plugin;private final DataStore data;private final DefinitionRegistry definitions;private final TownyHook towny=new TownyHook();
    private final BuildingFootprints footprints=new BuildingFootprints();
    private static final class View implements InventoryHolder {
        final UUID town;final UUID session=UUID.randomUUID();final String project;Inventory inventory;ItemStack[] auditBefore=new ItemStack[0];
        View(UUID town,String project){this.town=town;this.project=project;}
        @Override public Inventory getInventory(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return inventory;} }
    }
    public BuildingStorageService(JavaPlugin plugin,DataStore data,DefinitionRegistry definitions){this.plugin=plugin;this.data=data;this.definitions=definitions;}
    public void start(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {Bukkit.getServicesManager().register(BuildingStorageApi.class,this,plugin,ServicePriority.Normal);Bukkit.getPluginManager().registerEvents(this,plugin);} }
    public void stop(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.SuiteScheduler.cleanupPlayers(plugin, p -> {if(p.getOpenInventory().getTopInventory().getHolder() instanceof View)p.closeInventory();});Bukkit.getServicesManager().unregister(BuildingStorageApi.class,this);} }
    public void closeWarehouse(UUID town){ru.neverland.core.SuiteScheduler.players(plugin,p->{if(p.getOpenInventory().getTopInventory().getHolder() instanceof View v&&v.town.equals(town)&&v.project.equals("warehouse")){p.closeInventory();tell(p,"Склад закрыт для учёта захваченной добычи рейда. Откройте его немного позже.");}});}
    private void thread(){if(!ru.neverland.core.SuiteState.held())throw new IllegalStateException("Операция со складом требует основного потока");}
    public int level(TownData town,String project){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {var s=town.constructionSite(project);return s==null?0:Math.min(town.level(project),s.completedStage());} }
    public int size(TownData town,String project){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        if(project.equals("warehouse"))return town.storage().length;
        int configured=project.equals("forestry")?Math.max(9,level(town,project)*9):Math.max(9,(level(town,project)+1)*9);
        int used=town.civicOccupiedSlots(project);
        return Math.min(54,Math.max(((used+8)/9)*9,configured));
    } }
    public ItemStack[] read(TownData town,String id){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return id.equals("warehouse")?town.storage():town.civicInventory(id,size(town,id));} }
    public void write(TownData town,String id,ItemStack[] items){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {if(id.equals("warehouse"))town.setStorage(items,town.storage().length);else town.setCivicInventory(id,items);data.markDirty();} }
    public boolean busy(UUID town,String id){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return data.storageBusy(town,id);} }
    @Override public Map<String,BuildingDepot> depots(UUID townId){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        thread();if(towny.town(townId)==null)return Map.of();var town=data.town(townId);Map<String,BuildingDepot> result=new TreeMap<>();
        for(var e:town.constructionSites().entrySet()){
            int level=level(town,e.getKey());if(level<1)continue;var def=definitions.all().stream().filter(d->d.id().equals(e.getKey())).findFirst().orElse(null);if(def==null)continue;
            var bounds=footprints.footprint(e.getValue(),town.level(e.getKey())).orElse(null);if(bounds==null)continue;
            result.put(e.getKey(),new BuildingDepot(e.getKey(),def.name().replaceAll("(?i)[&§]#[0-9a-f]{6}|[&§][0-9a-fk-orx]", ""),def.icon().name(),bounds.worldId(),bounds.minX(),bounds.minZ(),bounds.maxX(),bounds.maxZ(),e.getValue().originY(),level,size(town,e.getKey())));
        }
        return Map.copyOf(result);
    } }
    @Override public ItemStack[] stock(UUID town,String project){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {thread();return read(data.town(town),project);} }
    @Override public Map<UUID,CargoShipment> shipments(UUID town){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {thread();return data.town(town).shipments();} }
    @Override public Set<UUID> shipmentTowns(){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {thread();Set<UUID> ids=new HashSet<>();data.towns().forEach((id,t)->{if(!t.shipments().isEmpty())ids.add(id);});return Set.copyOf(ids);} }
    private ShipmentTransactions.Access access(UUID id){var t=data.town(id);return new ShipmentTransactions.Access(){
        public ItemStack[] read(String project){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {return BuildingStorageService.this.read(t,project);} }
        public void write(String project,ItemStack[] contents){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {BuildingStorageService.this.write(t,project,contents);} }
        public boolean busy(String project){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {return BuildingStorageService.this.busy(id,project);} }
        public void commit()throws IOException{ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {data.saveOrThrow();} }
    };}
    @Override public CargoShipment pickup(UUID town,UUID id,String route,String from,String to,ItemStack filter,int limit,int keep)throws IOException{ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
        thread();var depots=depots(town);if(!depots.containsKey(from)||!depots.containsKey(to))return null;
        return ShipmentTransactions.pickup(data.town(town),access(town),id,route,from,to,filter,limit,keep);
    } }
    @Override public boolean unload(UUID town,UUID id,boolean returned)throws IOException{ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
        thread();var s=data.town(town).shipments().get(id);if(s==null)return false;
        // Emergency return remains possible after a building/town is lost. Cargo is never dropped.
        if(!returned&&!depots(town).containsKey(s.target()))return false;
        return ShipmentTransactions.unload(data.town(town),access(town),id,returned);
    } }
    @Override public void acknowledge(UUID town,UUID id)throws IOException{ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {thread();ShipmentTransactions.acknowledge(data.town(town),access(town),id);} }
    private TradeStorageTransactions.Access tradeAccess(){return new TradeStorageTransactions.Access(){
        public ItemStack[] read(UUID town){ try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {return data.town(town).storage();} }
        public void write(UUID town,ItemStack[] items){ try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {BuildingStorageService.this.write(data.town(town),"warehouse",items);} }
        public boolean busy(UUID town){ try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {return data.storageBusy(town,"warehouse");} }
        public void commit()throws IOException{ try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {data.saveOrThrow();} }
    };}
    @Override public String reserveTrade(UUID seller,UUID buyer,UUID id,ItemStack sample,int amount)throws IOException { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) {
        thread();if(towny.town(seller)==null||towny.town(buyer)==null)return "CITY_MISSING";
        return TradeStorageTransactions.reserve(data.town(seller),tradeAccess(),buyer,id,sample,amount);
    } }
    @Override public String settleTrade(UUID seller,UUID id,boolean deliver)throws IOException { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) {
        thread();var receipt=data.town(seller).tradeCargo().get(id);
        if(deliver&&receipt!=null&&towny.town(receipt.buyer())==null)return "CITY_MISSING";
        String result=TradeStorageTransactions.settle(data.town(seller),tradeAccess(),id,deliver);auditTrade(seller,id);return result;
    } }
    private boolean auditTrade(UUID seller,UUID id){var r=data.town(seller).tradeCargo().get(id);if(r==null||r.status().equals("RESERVED"))return true;return ru.neverland.core.AuditTrail.record(plugin,r.status(),id.toString(),"WAREHOUSE_TRADE",r.status(),ru.neverland.core.AuditRecord.Party.system(),ru.neverland.core.AuditTrail.town(seller),ru.neverland.core.AuditTrail.town(r.status().equals("DELIVERED")?r.buyer():seller),ru.neverland.core.AuditTrail.item(r.sample()),r.amount(),"","Ресурсная квитанция API; buyer="+r.buyer());}
    @Override public void acknowledgeTrade(UUID seller,UUID id)throws IOException { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) {thread();ru.neverland.core.AuditTrail.require(auditTrade(seller,id));TradeStorageTransactions.acknowledge(data.town(seller),tradeAccess(),id);} }
    @Override public void openStorage(Player player,String project){ try (var suiteGuard27 = ru.neverland.core.SuiteState.guard()) {
        thread();var town=towny.town(player);if(town==null){tell(player,"Вы не состоите в городе.");return;}
        if(!ru.neverland.core.CitizensAccess.allows(town.getUUID(),player.getUniqueId(),"STORAGE")){tell(player,"Ваш статус не даёт доступа к муниципальному складу.");return;}
        var t=data.town(town.getUUID());var def=definitions.all().stream().filter(d->d.id().equals(project)).findFirst().orElse(null);
        if(!project.equals("warehouse")&&(def==null||level(t,project)<1)){tell(player,"У здания ещё нет завершённого этапа.");return;}
        View v=new View(town.getUUID(),project);
        if(!data.lockStorage(town.getUUID(),project,v.session)){tell(player,"Склад уже открыт другим игроком. Дождитесь его закрытия.");return;}
        String name=project.equals("warehouse")?"Склад города":def.name();
        try{v.inventory=ru.neverland.core.MenuStyle.inventory(plugin, v,size(t,project),ru.neverland.townybuilds.util.ColorUtil.component("&2"+name+" &8• Склад"));v.auditBefore=StockMath.copy(read(t,project));v.inventory.setContents(v.auditBefore);player.openInventory(v.inventory);if(player.getOpenInventory().getTopInventory()!=v.inventory)data.unlockStorage(v.town,v.project,v.session);}
        catch(RuntimeException ex){data.unlockStorage(v.town,v.project,v.session);throw ex;}
    } }
    private void tell(Player p,String message){p.sendMessage(ChatColor.translateAlternateColorCodes('&',"&8[&aNeverLand &8• &fСклады&8] &r"+message));}
    private boolean citizen(Player p,View v){var t=towny.town(p);return t!=null&&t.getUUID().equals(v.town)&&ru.neverland.core.CitizensAccess.allows(v.town,p.getUniqueId(),"STORAGE");}
    @EventHandler public void click(InventoryClickEvent e){ try (var suiteGuard28 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyBuilds")){ru.neverland.core.ModuleGate.stopEvent(e);return;}
        if(!(e.getView().getTopInventory().getHolder() instanceof View v)||!(e.getWhoClicked() instanceof Player p))return;
        if(!citizen(p,v)){e.setCancelled(true);ru.neverland.core.SuiteScheduler.entity(plugin,p,()->p.closeInventory());return;}
        boolean mayor=towny.isMayor(p,towny.town(p));
        boolean deposit=mayor||ru.neverland.core.CouncilAccess.check(p,v.town,"storage_deposit",true);
        boolean withdraw=mayor||ru.neverland.core.CouncilAccess.check(p,v.town,"storage_withdraw",false);
        boolean top=e.getClickedInventory()==e.getView().getTopInventory();
        if(!StorageAccessPolicy.allows(e.getAction(),top,deposit,withdraw)){e.setCancelled(true);tell(p,"Мэр ограничил это действие со складом.");}
    } }
    @EventHandler public void drag(InventoryDragEvent e){ try (var suiteGuard29 = ru.neverland.core.SuiteState.guard()) {
        if(ru.neverland.core.SuiteState.frozen("NeverLandTownyBuilds")){ru.neverland.core.ModuleGate.stopEvent(e);return;}
        if(!(e.getView().getTopInventory().getHolder() instanceof View v)||!(e.getWhoClicked() instanceof Player p))return;
        if(!citizen(p,v)){e.setCancelled(true);return;}
        if(e.getRawSlots().stream().anyMatch(slot->slot<e.getView().getTopInventory().getSize())
                &&!towny.isMayor(p,towny.town(p))&&!ru.neverland.core.CouncilAccess.check(p,v.town,"storage_deposit",true)){
            e.setCancelled(true);tell(p,"Мэр ограничил пополнение склада.");
        }
    } }
    private void mirror(org.bukkit.entity.HumanEntity viewer,Inventory inventory,View view){ru.neverland.core.SuiteScheduler.entity(plugin,viewer,()->{
        if(data.ownsStorage(view.town,view.project,view.session))write(data.town(view.town),view.project,inventory.getContents());
    });}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void changed(InventoryClickEvent e){ try (var suiteGuard30 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyBuilds")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(e.getView().getTopInventory().getHolder() instanceof View v)mirror(e.getWhoClicked(),e.getView().getTopInventory(),v);} }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void changed(InventoryDragEvent e){ try (var suiteGuard31 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyBuilds")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(e.getView().getTopInventory().getHolder() instanceof View v)mirror(e.getWhoClicked(),e.getView().getTopInventory(),v);} }
    @EventHandler public void close(InventoryCloseEvent e){ try (var suiteGuard32 = ru.neverland.core.SuiteState.guard()) {if(!(e.getInventory().getHolder() instanceof View v)||!data.ownsStorage(v.town,v.project,v.session))return;
        try{write(data.town(v.town),v.project,e.getInventory().getContents());data.save();ru.neverland.core.AuditTrail.inventory(plugin,v.session.toString(),e.getPlayer().getUniqueId(),v.town,v.project,v.auditBefore,e.getInventory().getContents());}finally{data.unlockStorage(v.town,v.project,v.session);}} }
}
