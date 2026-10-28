package ru.neverland.runtime;

import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.block.BlockFace;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.block.Action;
import org.bukkit.craftbukkit.*;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import net.minecraft.server.level.*;
import com.mojang.authlib.GameProfile;
import com.palmergames.bukkit.towny.*;
import ru.neverland.mintcamps.*;
import ru.neverland.mintcamps.model.*;
import ru.neverland.mintcamps.gui.MenuManager;
import ru.neverland.mintcamps.service.CampService;
import ru.neverland.townychronicles.service.ChronicleRepository;
import ru.neverland.townychronicles.model.*;

/** Real Folia inventories, event handlers and persistence. Networkless actors stand in for clients. */
public final class CampGuestsProbe extends TntRuntimeProbe {
    static final UUID OWNER=id("guest-owner"),GUEST=id("limited-guest"),OUTSIDER=id("guest-outsider"),CITY=id("chronicle-city");
    MintTownyCamps campsPlugin;CampService camps;MenuManager menus;Camp camp;Actor owner,guest,outsider;UUID historyEntry;
    final class Actor extends CraftPlayer {
        Inventory top=Bukkit.createInventory(null,9);
        Actor(UUID id,String name)throws Exception{
            super((CraftServer)Bukkit.getServer(),new ServerPlayer(((CraftServer)Bukkit.getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(id,name),ClientInformation.createDefault()));
            var field=net.minecraft.world.entity.Entity.class.getDeclaredField("bukkitEntity");field.setAccessible(true);field.set(getHandleRaw(),this);getHandleRaw().setPosRaw(15.5,161,8.5);
            var region=io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();field=region.getClass().getDeclaredField("allEntities");field.setAccessible(true);((ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity>)field.get(region)).add(getHandleRaw());taskScheduler.registerTo(region.entitySchedulerTickList);
        }
        InventoryView view(Inventory inventory){return(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(p,m,a)->switch(m.getName()){
            case "getTopInventory"->inventory;case "getBottomInventory"->getInventory();case "getPlayer"->this;case "getType"->inventory.getType();
            case "convertSlot"->(int)a[0]<inventory.getSize()?(int)a[0]:(int)a[0]-inventory.getSize();
            case "getInventory"->(int)a[0]<0?null:(int)a[0]<inventory.getSize()?inventory:getInventory();
            case "getItem"->(int)a[0]<0?null:(int)a[0]<inventory.getSize()?inventory.getItem((int)a[0]):getInventory().getItem((int)a[0]-inventory.getSize());
            case "getCursor"->new ItemStack(Material.AIR);case "countSlots"->inventory.getSize()+36;case "getTitle","getOriginalTitle"->"Probe";default->null;});}
        @Override public InventoryView getOpenInventory(){return view(top);}
        @Override public InventoryView openInventory(Inventory inventory){
            Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view(top)));var event=new InventoryOpenEvent(view(inventory));Bukkit.getPluginManager().callEvent(event);if(!event.isCancelled())top=inventory;return view(top);
        }
        @Override public void closeInventory(){Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view(top)));top=Bukkit.createInventory(null,9);}
        @Override public boolean isOnline(){return true;}@Override public boolean isOp(){return false;}
        @Override public boolean hasPermission(String permission){return permission.equals("mintcamps.use");}
        @Override public GameMode getGameMode(){return GameMode.SURVIVAL;}
        @Override public void sendMessage(String s){}@Override public void sendMessage(String... s){}@Override public void sendMessage(net.kyori.adventure.text.Component s){}@Override public void sendActionBar(net.kyori.adventure.text.Component s){}
    }
    @Override void setup()throws Exception{
        campsPlugin=(MintTownyCamps)Bukkit.getPluginManager().getPlugin("NeverLandTownyCamps");camps=(CampService)field(campsPlugin.getCommand("camp").getExecutor(),"camps");menus=(MenuManager)field(campsPlugin.getCommand("camp").getExecutor(),"menus");
        check(campsPlugin.isEnabled(),"real Camps enabled");var universe=TownyUniverse.getInstance();
        if(!restart)for(var entry:Map.of(OWNER,"CampOwner",GUEST,"CampGuest",OUTSIDER,"CampStranger").entrySet())universe.getDataSource().newResident(entry.getValue(),entry.getKey()).save();
        owner=new Actor(OWNER,"CampOwner");guest=new Actor(GUEST,"CampGuest");outsider=new Actor(OUTSIDER,"CampStranger");
        if(restart){
            camp=campsPlugin.repository().get(OWNER).orElseThrow();check(camp.stash()[0].getType()==Material.EMERALD&&camp.stash()[0].getAmount()==5,"closing revoked storage persists real items through full restart");
            check(!camp.allows(GUEST,GuestAccess.Right.STORAGE,System.currentTimeMillis()),"expired permission is not renewed by restart");
            var history=(ChronicleRepository)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyChronicles"),"repository");historyEntry=UUID.fromString(Files.readString(proof.resolve("entry.txt")));check(history.entries(CITY).stream().anyMatch(e->e.id().equals(historyEntry)),"final chronicle snapshot survives full server restart");finish();return;
        }
        long now=System.currentTimeMillis();camp=new Camp(OWNER,"CampOwner",world.getUID(),world.getName(),new ru.neverland.mintcamps.model.BlockPos(15,160,8),BlockFace.NORTH,1,"plains",now,now+3600000,true,9);camp.stashBlock(new ru.neverland.mintcamps.model.BlockPos(15,160,10));campsPlugin.repository().put(camp);campsPlugin.repository().saveOrThrow();
        camps.guest(owner,GUEST,"CampGuest",Set.of(GuestAccess.Right.SLEEP),60);
        check(camp.allows(GUEST,GuestAccess.Right.SLEEP,now+1000)&&!camp.canAccess(GUEST),"sleep guest has no full access or teleport authority");
        Inventory before=guest.top;menus.openStorage(guest,camp);check(guest.top==before,"sleep right cannot open warehouse");
        world.getBlockAt(15,160,9).setType(Material.CRAFTING_TABLE,false);check(interact(guest).isCancelled(),"sleep right cannot use a camp workbench");
        camps.guest(owner,GUEST,"CampGuest",Set.of(GuestAccess.Right.STORAGE),60);menus.openStorage(guest,camp);Inventory storage=guest.top;check(storage!=before&&campsPlugin.repository().stashBusy(OWNER),"storage right opens one exclusive real inventory");
        before=outsider.top;menus.openStorage(outsider,camp);check(outsider.top==before,"stranger cannot join another player's storage session");storage.setItem(0,new ItemStack(Material.EMERALD,5));
        camps.revokeGuest(owner,GUEST);check(click(guest).isCancelled(),"revocation denies another inventory action immediately");
        later(3,()->{check(!campsPlugin.repository().stashBusy(OWNER)&&camp.stash()[0].getAmount()==5,"revoked window closes and saves contents once");
            camps.guest(owner,GUEST,"CampGuest",Set.of(GuestAccess.Right.STORAGE),60);menus.openStorage(guest,camp);expire();
            later(23,()->{check(!campsPlugin.repository().stashBusy(OWNER),"expiry closes idle warehouse window without requiring a click");
                camps.guest(owner,GUEST,"CampGuest",Set.of(GuestAccess.Right.WORKBENCH),60);check(!interact(guest).isCancelled(),"workbench right permits camp crafting interaction");
                guest.openInventory(Bukkit.createInventory(null,9));expire();check(click(guest).isCancelled(),"expiry denies next action in captured workbench window");
                later(3,()->{guest.closeInventory();chronicleShutdown();});
            });
        });
    }
    PlayerInteractEvent interact(Actor actor){var event=new PlayerInteractEvent(actor,Action.RIGHT_CLICK_BLOCK,null,world.getBlockAt(15,160,9),BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(event);return event;}
    InventoryClickEvent click(Actor actor){var event=new InventoryClickEvent(actor.getOpenInventory(),InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);return event;}
    void expire()throws Exception{long now=System.currentTimeMillis();var previous=camp.guests().get(GUEST);camp.guests().put(GUEST,new GuestAccess("CampGuest",now-60000,now-1,previous.rights()));campsPlugin.repository().markDirty();campsPlugin.repository().saveOrThrow();}
    void chronicleShutdown()throws Exception{
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyChronicles");var repo=(ChronicleRepository)field(plugin,"repository");repo.state(CITY).name("SnapshotCity");repo.markDirty();historyEntry=UUID.randomUUID();
        repo.add(new ChronicleEntry(historyEntry,CITY,"SnapshotCity",ChronicleCategory.CUSTOM,System.currentTimeMillis(),"Final before disable",List.of(),"","native-probe"));Files.writeString(proof.resolve("entry.txt"),historyEntry.toString());
        var control=(org.bukkit.plugin.java.JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"disable","Chronicles"});
        check(ru.neverland.core.SuiteState.frozen(plugin.getName()),"real administrator disable first freezes Chronicle work");
        later(15,()->{Path file=plugin.getDataFolder().toPath().resolve("chronicles.yml");check(!plugin.isEnabled()&&Files.readString(file).contains(historyEntry.toString()),"confirmed final snapshot precedes actual plugin disable without blocking region ledger");
            control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"enable","Chronicles"});finish();});
    }
    @Override void finish()throws Exception{
        owner.closeInventory();guest.closeInventory();outsider.closeInventory();campsPlugin.repository().saveOrThrow();TownyUniverse.getInstance().getDataSource().saveAll();
        Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; genuine Folia inventories/event handlers, timed camp rights, item serialization and Chronicle shutdown; explicit networkless actors and inventory-view stand-ins; no GUI client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());
    }
}
