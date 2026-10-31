package cc.crafto.ui.server;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static cc.crafto.ui.Protocol.*;

/** Presentation hint only. All item updates and clicks remain Minecraft inventory packets. */
final class AddonBridge implements Listener {
    private final CraftoUIPlugin plugin;
    AddonBridge(CraftoUIPlugin plugin){this.plugin=plugin;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void opened(InventoryOpenEvent event){
        if(!(event.getPlayer() instanceof Player player))return;
        Inventory inventory=event.getInventory();
        if(inventory.getType()!=InventoryType.CHEST||!plugin.hasNativeMenus(player.getUniqueId()))return;
        var holder=inventory.getHolder(false);if(holder==null)return;
        String owner;
        try{var p=JavaPlugin.getProvidingPlugin(holder.getClass());if(!p.isEnabled())return;owner=p.getName();}catch(IllegalArgumentException|IllegalStateException e){return;}
        var config=plugin.addonCatalog();if(!config.enabled())return;
        var match=config.entries().values().stream().filter(e->e.plugin().equals(owner)&&e.holders().contains(holder.getClass().getName())).findFirst();
        if(match.isEmpty())return;
        var entry=match.get();
        // The native open packet is emitted after InventoryOpenEvent. Send the hint on the next entity tick.
        player.getScheduler().run(plugin,task->{
            if(!player.isOnline()||!plugin.hasNativeMenus(player.getUniqueId())||player.getOpenInventory().getTopInventory()!=inventory)return;
            if(!player.hasPermission("craftoui.use"))return;
            String title=PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
            boolean editable=entry.inventoryHolders().contains(holder.getClass().getName())||entry.inventoryTitleWords().stream().anyMatch(w->title.toLowerCase(java.util.Locale.ROOT).contains(w));
            String style=holder.getClass().getName().equals(CityStylePreviewBridge.HOLDER)?CityStylePreviewBridge.selected(inventory):"";
            var presentation=NativeMenuPresentation.options(inventory,editable||preserveSlotLayout(entry.plugin(),holder.getClass().getName(),title),entry.plugin(),holder.getClass().getName());
            boolean stylePreview=!style.isEmpty()&&plugin.hasBuildingPreview(player.getUniqueId());
            if(stylePreview)presentation.addProperty("previewSlot",13);
            String previewKey=stylePreview||entry.plugin().equals("NeverLandTownyBuilds")&&holder.getClass().getName().endsWith("$DetailsHolder")&&presentation.get("previewSlot").getAsInt()>=0&&plugin.hasBuildingPreview(player.getUniqueId())&&BuildingPreviewBridge.available(plugin)?java.util.UUID.randomUUID().toString():"";
            boolean ordersUi=NativeMenuNavigation.orderBoard(entry.plugin(),holder.getClass().getName())&&presentation.getAsJsonObject("toolbar").has("primary")&&plugin.civicOrdersAvailable(player);
            boolean warehouseUi=entry.plugin().equals("NeverLandTownyBuilds")&&java.util.Set.of("ru.neverland.townybuilds.civic.CivicService$CivicStorageHolder","ru.neverland.townybuilds.gui.MenuManager$StorageHolder","ru.neverland.townybuilds.storage.BuildingStorageService$View").contains(holder.getClass().getName());
            plugin.nativeMenu(player,object("viewKey",entry.plugin()+"/"+holder.getClass().getName()+"/"+title,"presentation",presentation,"buildingPreviewKey",previewKey,"stylePreview",stylePreview,"ordersUi",ordersUi,"warehouseUi",warehouseUi,"placementControls",holder.getClass().getName().equals("ru.neverland.townybuilds.construction.PlacementService$PlacementMenu"),"title",title,"slots",inventory.getSize(),"module",holder.getClass().getName().equals("ru.neverland.townycontrol.PersonalHistoryMenu$View")?"Мои операции":entry.label(),"commandModule",entry.plugin(),
                "inventory",editable||preserveSlotLayout(entry.plugin(),holder.getClass().getName(),title),"columns",config.columns(),"layout",marketListing(entry,title)?"market":"cards",
                "width",config.width(),"height",config.height(),"theme",plugin.theme(config.theme())),inventory,entry,editable);
            if(stylePreview)CityStylePreviewBridge.load(plugin,player,inventory,previewKey,style);
            else BuildingPreviewBridge.load(plugin,player,inventory,previewKey);
        },null);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void clicked(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        Inventory inventory=event.getView().getTopInventory();var holder=inventory.getHolder(false);
        if(holder!=null&&holder.getClass().getName().equals("ru.neverland.townybuilds.construction.CityStyles$View")&&event.getRawSlot()==20
            &&inventory.getItem(13)!=null)plugin.styleConfirmed(player);
        if(holder==null||!holder.getClass().getName().equals("ru.neverland.townybuilds.gui.MenuManager$DetailsHolder"))return;
        try{if(!JavaPlugin.getProvidingPlugin(holder.getClass()).getName().equals("NeverLandTownyBuilds"))return;}catch(IllegalArgumentException|IllegalStateException e){return;}
        // Builds refreshes the same container after a deferred, verified contribution.
        player.getScheduler().runDelayed(plugin,task->{
            if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=inventory||!player.hasPermission("craftoui.use")||!player.hasPermission("neverlandtownybuilds.use"))return;
            String title=PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
            plugin.nativePresentation(player,object("presentationOnly",true,"viewKey","NeverLandTownyBuilds/"+holder.getClass().getName()+"/"+title,"presentation",NativeMenuPresentation.options(inventory,false,"NeverLandTownyBuilds",holder.getClass().getName())));
        },null,2L);
    }
    private static boolean marketListing(AddonCatalog.Entry entry,String title){
        return entry.layout().equals("market")&&java.util.Set.of("Городской рынок","Международный рынок","Предложения города","Покупки и получение","Каталог автоматических цен").contains(org.bukkit.ChatColor.stripColor(title));
    }
    static boolean preserveSlotLayout(String plugin,String holder,String title){
        // This window is a spatial map: keep its north/east slot positions in CraftoUI's themed grid.
        // It is still a button menu, never an editable storage inventory.
        return plugin.equals("NeverLandTownyDistricts")&&holder.equals("ru.neverland.townydistricts.gui.MobFarmMenu$View")
            &&org.bukkit.ChatColor.stripColor(title).equals("Город • защита от мобов");
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void closed(InventoryCloseEvent event){if(event.getPlayer() instanceof Player p)plugin.nativeClosed(p,event.getInventory());}
}
