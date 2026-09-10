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
        final UUID town;final UUID session=UUID.randomUUID();final String project;Inventory inventory;
        View(UUID town,String project){this.town=town;this.project=project;}
        @Override public Inventory getInventory(){return inventory;}
    }
    public BuildingStorageService(JavaPlugin plugin,DataStore data,DefinitionRegistry definitions){this.plugin=plugin;this.data=data;this.definitions=definitions;}
    public void start(){Bukkit.getServicesManager().register(BuildingStorageApi.class,this,plugin,ServicePriority.Normal);Bukkit.getPluginManager().registerEvents(this,plugin);}
    public void stop(){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof View)p.closeInventory();Bukkit.getServicesManager().unregister(BuildingStorageApi.class,this);}
    private void thread(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Операция со складом требует основного потока");}
    public int level(TownData town,String project){var s=town.constructionSite(project);return s==null?0:Math.min(town.level(project),s.completedStage());}
    public int size(TownData town,String project){
        if(project.equals("warehouse"))return town.storage().length;
        int configured=project.equals("forestry")?Math.max(9,level(town,project)*9):Math.max(9,(level(town,project)+1)*9);
        int used=town.civicOccupiedSlots(project);
        return Math.min(54,Math.max(((used+8)/9)*9,configured));
    }
    public ItemStack[] read(TownData town,String id){return id.equals("warehouse")?town.storage():town.civicInventory(id,size(town,id));}
    public void write(TownData town,String id,ItemStack[] items){if(id.equals("warehouse"))town.setStorage(items,town.storage().length);else town.setCivicInventory(id,items);data.markDirty();}
    public boolean busy(UUID town,String id){return data.storageBusy(town,id);}
    @Override public Map<String,BuildingDepot> depots(UUID townId){
        thread();if(towny.town(townId)==null)return Map.of();var town=data.town(townId);Map<String,BuildingDepot> result=new TreeMap<>();
        for(var e:town.constructionSites().entrySet()){
            int level=level(town,e.getKey());if(level<1)continue;var def=definitions.all().stream().filter(d->d.id().equals(e.getKey())).findFirst().orElse(null);if(def==null)continue;
            var bounds=footprints.footprint(e.getValue(),town.level(e.getKey())).orElse(null);if(bounds==null)continue;
            result.put(e.getKey(),new BuildingDepot(e.getKey(),def.name().replaceAll("(?i)[&§]#[0-9a-f]{6}|[&§][0-9a-fk-orx]", ""),def.icon().name(),bounds.worldId(),bounds.minX(),bounds.minZ(),bounds.maxX(),bounds.maxZ(),e.getValue().originY(),level,size(town,e.getKey())));
        }
        return Map.copyOf(result);
    }
    @Override public ItemStack[] stock(UUID town,String project){thread();return read(data.town(town),project);}
    @Override public Map<UUID,CargoShipment> shipments(UUID town){thread();return data.town(town).shipments();}
    @Override public Set<UUID> shipmentTowns(){thread();Set<UUID> ids=new HashSet<>();data.towns().forEach((id,t)->{if(!t.shipments().isEmpty())ids.add(id);});return Set.copyOf(ids);}
    private ShipmentTransactions.Access access(UUID id){var t=data.town(id);return new ShipmentTransactions.Access(){
        public ItemStack[] read(String project){return BuildingStorageService.this.read(t,project);}
        public void write(String project,ItemStack[] contents){BuildingStorageService.this.write(t,project,contents);}
        public boolean busy(String project){return BuildingStorageService.this.busy(id,project);}
        public void commit()throws IOException{data.saveOrThrow();}
    };}
    @Override public CargoShipment pickup(UUID town,UUID id,String route,String from,String to,ItemStack filter,int limit,int keep)throws IOException{
        thread();var depots=depots(town);if(!depots.containsKey(from)||!depots.containsKey(to))return null;
        return ShipmentTransactions.pickup(data.town(town),access(town),id,route,from,to,filter,limit,keep);
    }
    @Override public boolean unload(UUID town,UUID id,boolean returned)throws IOException{
        thread();var s=data.town(town).shipments().get(id);if(s==null)return false;
        // Emergency return remains possible after a building/town is lost. Cargo is never dropped.
        if(!returned&&!depots(town).containsKey(s.target()))return false;
        return ShipmentTransactions.unload(data.town(town),access(town),id,returned);
    }
    @Override public void acknowledge(UUID town,UUID id)throws IOException{thread();ShipmentTransactions.acknowledge(data.town(town),access(town),id);}
    @Override public void openStorage(Player player,String project){
        thread();var town=towny.town(player);if(town==null){tell(player,"Вы не состоите в городе.");return;}
        var t=data.town(town.getUUID());var def=definitions.all().stream().filter(d->d.id().equals(project)).findFirst().orElse(null);
        if(!project.equals("warehouse")&&(def==null||level(t,project)<1)){tell(player,"У здания ещё нет завершённого этапа.");return;}
        View v=new View(town.getUUID(),project);
        if(!data.lockStorage(town.getUUID(),project,v.session)){tell(player,"Склад уже открыт другим игроком. Дождитесь его закрытия.");return;}
        String name=project.equals("warehouse")?"Склад города":def.name();
        try{v.inventory=Bukkit.createInventory(v,size(t,project),ru.neverland.townybuilds.util.ColorUtil.component("&2"+name+" &8• Склад"));v.inventory.setContents(read(t,project));player.openInventory(v.inventory);if(player.getOpenInventory().getTopInventory()!=v.inventory)data.unlockStorage(v.town,v.project,v.session);}
        catch(RuntimeException ex){data.unlockStorage(v.town,v.project,v.session);throw ex;}
    }
    private void tell(Player p,String message){p.sendMessage(ChatColor.translateAlternateColorCodes('&',"&8[&aNeverLand &8• &fСклады&8] &r"+message));}
    private boolean citizen(Player p,View v){var t=towny.town(p);return t!=null&&t.getUUID().equals(v.town);}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof View v)||!(e.getWhoClicked() instanceof Player p))return;
        if(!citizen(p,v)){e.setCancelled(true);Bukkit.getScheduler().runTask(plugin,()->p.closeInventory());return;}
        if(towny.isMayor(p,towny.town(p)))return;
        boolean top=e.getClickedInventory()==e.getView().getTopInventory();
        boolean deposit=e.getAction()==InventoryAction.PLACE_ALL||e.getAction()==InventoryAction.PLACE_ONE||e.getAction()==InventoryAction.PLACE_SOME||e.getAction()==InventoryAction.NOTHING;
        if((top&&!deposit)||e.getClick()==ClickType.DOUBLE_CLICK||e.getClick()==ClickType.NUMBER_KEY||e.getClick()==ClickType.SWAP_OFFHAND){e.setCancelled(true);tell(p,"Забирать ресурсы со склада может мэр.");}
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View v&&e.getWhoClicked() instanceof Player p&&!citizen(p,v))e.setCancelled(true);}
    private void mirror(Inventory inventory,View view){Bukkit.getScheduler().runTask(plugin,()->{
        if(data.ownsStorage(view.town,view.project,view.session))write(data.town(view.town),view.project,inventory.getContents());
    });}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void changed(InventoryClickEvent e){if(e.getView().getTopInventory().getHolder() instanceof View v)mirror(e.getView().getTopInventory(),v);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void changed(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View v)mirror(e.getView().getTopInventory(),v);}
    @EventHandler public void close(InventoryCloseEvent e){if(!(e.getInventory().getHolder() instanceof View v)||!data.ownsStorage(v.town,v.project,v.session))return;
        try{write(data.town(v.town),v.project,e.getInventory().getContents());data.save();}finally{data.unlockStorage(v.town,v.project,v.session);}}
}
