package cc.crafto.probe;
import cc.crafto.ui.Protocol;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.Town;
import io.papermc.paper.threadedregions.scheduler.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.SuiteState;
import ru.neverland.townycouncil.*;

/** Real Council menus and Bukkit clicks; simulated client connection/player view on the owning region. */
public final class AddonUiProbe extends JavaPlugin {
    World world;Town town;Actor mayor,member;AccessMenus access;MinisterMenus ministers;CouncilService service;JavaPlugin ui;Object connection;Path proof;
    List<String> checks=new ArrayList<>();
    UUID id(String name){return UUID.nameUUIDFromBytes(("addon-ui-"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks.add("PASS "+name);}
    Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    void later(Runnable action){Bukkit.getRegionScheduler().runDelayed(this,new Location(world,1,160,1),task->{synchronized(SuiteState.lock()){try{action.run();}catch(Throwable e){finish(e);}}},4);}
    @Override public void onEnable(){
        if(!Files.exists(Path.of("ALLOW_DISPOSABLE_ADDON_UI_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath();world=Bukkit.getWorlds().getFirst();world.getChunkAtAsync(0,0).whenComplete((chunk,e)->later(()->{try{setup();roleList();}catch(Throwable error){finish(error);}}));
    }
    void setup()throws Exception{
        Files.createDirectories(proof);var council=(NeverLandTownyCouncil)Bukkit.getPluginManager().getPlugin("NeverLandTownyCouncil");service=council.council();ui=(JavaPlugin)Bukkit.getPluginManager().getPlugin("CraftoUI");
        check(ui.isEnabled()&&ui.getDescription().getVersion().equals("0.8.2"),"CraftoUI server 0.8.2 enabled");
        check(Files.exists(ui.getDataFolder().toPath().resolve("backups/0.8.1/addons.json")),"previous addon catalog automatically backed up on startup");
        var u=TownyUniverse.getInstance();u.newTownInternal("UiProbeTown",id("town"));town=u.getTown(id("town"));
        for(String name:List.of("Mayor","Member")){var r=u.getDataSource().newResident("UiProbe"+name,id(name));r.setTown(town);if(name.equals("Mayor"))town.setMayor(r);}
        mayor=new Actor(id("Mayor"));member=new Actor(id("Member"));
        for(var listener:InventoryClickEvent.getHandlerList().getRegisteredListeners()){
            if(listener.getPlugin()!=council)continue;if(listener.getListener() instanceof AccessMenus v)access=v;if(listener.getListener() instanceof MinisterMenus v)ministers=v;
        }
        check(access!=null&&ministers!=null,"real access and minister listeners registered");
        var type=Class.forName("cc.crafto.ui.server.CraftoUIPlugin$Connection",true,ui.getClass().getClassLoader());var ctor=type.getDeclaredConstructor(Player.class);ctor.setAccessible(true);connection=ctor.newInstance(mayor.player);
        for(String name:List.of("ready","nativeMenus","navigation")){var f=type.getDeclaredField(name);f.setAccessible(true);f.set(connection,true);}
        ((Map<UUID,Object>)field(ui,"connections")).put(mayor.uuid,connection);
        try{access.open(member.player,town,true,0);throw new AssertionError("member may edit access");}catch(IllegalArgumentException expected){check(true,"ordinary member still cannot open mayor permission editor");}
    }
    void hint(String label){
        var messages=mayor.packets.stream().map(Protocol::decode).filter(m->m.kind()==Protocol.Kind.NATIVE_MENU).toList();
        check(messages.size()==1,label+" sends exactly one custom UI hint");var body=messages.getFirst().body();
        check(body.get("commandModule").getAsString().equals("NeverLandTownyCouncil")&&!body.get("inventory").getAsBoolean()&&body.get("layout").getAsString().equals("cards"),label+" uses Council cards, not storage or vanilla fallback");
        check(body.has("navigation")&&!body.get("navigation").getAsString().isEmpty(),label+" retains navigation token");mayor.packets.clear();
    }
    void click(int slot){var e=new InventoryClickEvent(mayor.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(e);check(e.isCancelled(),"button click never takes menu item");}
    void roleList(){access.open(mayor.player,town,true,0);later(()->{hint("Role access list");click(19);later(this::categories);});}
    void categories(){hint("Role permission categories");click(19);later(()->{hint("Role action list");click(10);later(this::choose);});}
    void choose(){hint("Allow/deny/reset choices");click(29);later(()->{
        hint("Action list after save");check(service.rules(town).roles().get(MinisterRole.values()[0].id()).containsValue(true),"real mayor action saved in Council rules");
        access.open(mayor.player,town,false,0);later(()->{hint("Resident access list");check(mayor.top.getItem(10)!=null&&mayor.top.getItem(10).getType()==Material.PLAYER_HEAD,"real resident rendered as selectable item");click(10);later(this::residentCategories);});
    });}
    void residentCategories(){hint("Resident permission categories");ministers.open(mayor.player,town,MinisterRole.values()[0]);later(()->{
        hint("Minister management");
        // An old queued hint must not replace a newer screen.
        access.open(mayor.player,town,true,0);Inventory old=mayor.top;access.open(mayor.player,town,false,0);later(()->{
            hint("Rapid screen change");check(mayor.top!=old,"latest menu remains active");
            mayor.player.openInventory(Bukkit.createInventory(null,54,Component.text("Права министров")));later(()->{check(mayor.packets.isEmpty(),"unowned same-title inventory is not intercepted");finish(null);});
        });
    });}
    void finish(Throwable error){try{Files.createDirectories(proof);Files.write(proof.resolve("checks.txt"),checks);
        if(error==null)Files.writeString(proof.resolve("passed.txt"),"PASS "+checks.size()+" checks\n");
        else{getLogger().log(java.util.logging.Level.SEVERE,"ADDON_UI_FAIL",error);var text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));Files.writeString(proof.resolve("failed.txt"),text.toString());}
    }catch(Exception e){e.printStackTrace();}Bukkit.getGlobalRegionScheduler().runDelayed(this,t->Bukkit.shutdown(),2);}
    final class Actor {
        UUID uuid;Player player;Inventory top=Bukkit.createInventory(null,9);InventoryView view;PlayerInventory inventory;List<byte[]> packets=new ArrayList<>();
        Actor(UUID uuid){this.uuid=uuid;
            inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class[]{PlayerInventory.class},(o,m,a)->switch(m.getName()){
                case "getItemInMainHand","getItemInOffHand","getItem"->new ItemStack(Material.AIR);case "getHeldItemSlot"->0;case "getContents","getStorageContents"->new ItemStack[36];case "getSize"->36;default->null;});
            EntityScheduler scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class[]{EntityScheduler.class},(o,m,a)->{
                if(m.getName().equals("run"))return Bukkit.getRegionScheduler().run((org.bukkit.plugin.Plugin)a[0],new Location(world,1,160,1),(Consumer<ScheduledTask>)a[1]);
                if(m.getName().equals("runDelayed"))return Bukkit.getRegionScheduler().runDelayed((org.bukkit.plugin.Plugin)a[0],new Location(world,1,160,1),(Consumer<ScheduledTask>)a[1],(Long)a[3]);throw new UnsupportedOperationException(m.getName());});
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
                case "getUniqueId"->uuid;case "getName"->TownyAPI.getInstance().getResident(uuid).getName();case "getServer"->Bukkit.getServer();case "isOnline","hasPermission"->true;case "isOp","isSneaking"->false;
                case "getWorld"->world;case "getLocation"->new Location(world,1,160,1);case "getScheduler"->scheduler;case "getInventory"->inventory;case "getOpenInventory"->view;
                case "openInventory"->{Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view));top=(Inventory)a[0];Bukkit.getPluginManager().callEvent(new InventoryOpenEvent(view));yield view;}
                case "sendPluginMessage"->{packets.add(((byte[])a[2]).clone());yield null;}case "sendMessage","sendActionBar"->null;case "hashCode"->uuid.hashCode();case "equals"->o==a[0];case "toString"->"AddonUiActor";default->null;});
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class[]{InventoryView.class},(o,m,a)->switch(m.getName()){
                case "getPlayer"->player;case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getType"->InventoryType.CHEST;case "title"->Component.text("Council probe menu");case "getTitle","getOriginalTitle"->"Council probe menu";case "getCursor"->new ItemStack(Material.AIR);
                case "convertSlot"->(Integer)a[0]<top.getSize()?a[0]:(Integer)a[0]-top.getSize();case "getInventory"->(Integer)a[0]<top.getSize()?top:inventory;
                case "getItem"->(Integer)a[0]<top.getSize()?top.getItem((Integer)a[0]):inventory.getItem((Integer)a[0]-top.getSize());case "getSlotType"->InventoryType.SlotType.CONTAINER;case "countSlots"->top.getSize()+36;default->null;});
        }
    }
}
