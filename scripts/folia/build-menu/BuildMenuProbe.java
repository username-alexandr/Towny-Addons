package ru.neverland.validation;

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
import ru.neverland.core.*;
import ru.neverland.townybuilds.*;
import ru.neverland.townybuilds.gui.MenuManager;
import ru.neverland.townybuilds.model.*;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.service.*;

/** Real Folia items, menus, events and resource fund; explicitly simulated player transport. */
public final class BuildMenuProbe extends JavaPlugin {
    final String run=System.getProperty("neverland.probe.run","legacy");
    final List<String> checks=new ArrayList<>();World world;Town town;Actor actor;NeverLandTownyBuilds builds;JavaPlugin ui;
    MenuManager menu;BuildService service;DataStore store;ProjectDefinition hall;Inventory original;Path proof;boolean done;
    Object field(Object o,String key)throws Exception{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    void check(boolean value,String label){checks.add((value?"PASS ":"FAIL ")+label);if(!value)throw new AssertionError(label);}
    UUID id(String key){return UUID.nameUUIDFromBytes((run+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    void later(Runnable action){SuiteScheduler.regionLater(this,new Location(world,1,160,1),()->{try{action.run();}catch(Throwable e){finish(e);}},4);}
    @Override public void onEnable(){
        if(!Files.exists(Path.of("ALLOW_DISPOSABLE_BUILD_MENU_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath().resolve("runs").resolve(run);world=Bukkit.getWorlds().get(0);
        world.getChunkAtAsync(0,0).whenComplete((chunk,error)->{if(error!=null){finish(error);return;}later(()->{try{start();}catch(Throwable e){finish(e);}});});
    }
    void start()throws Exception{
        builds=(NeverLandTownyBuilds)Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");ui=(JavaPlugin)Bukkit.getPluginManager().getPlugin("CraftoUI");
        menu=(MenuManager)field(builds,"uiMenus");service=(BuildService)field(menu,"builds");store=(DataStore)field(builds,"dataStore");
        check(builds.isEnabled()&&ui.isEnabled(),"Builds and CraftoUI enabled on Folia 26.2");
        var required=new ItemStack(Material.STONE_BRICKS,272);boolean rejected=false;
        try{required.serializeAsBytes();}catch(IllegalStateException expected){rejected=true;}
        check(rejected,"original 272-item serialization failure reproduced");
        String signature=ContributionFingerprint.item(required);
        check(signature.startsWith("272@")&&required.getAmount()==272,"fingerprint preserves quantity without mutating requirement");
        var sample=ItemStack.deserializeBytes(Base64.getDecoder().decode(signature.substring(signature.indexOf('@')+1)));
        check(sample.getAmount()==1&&sample.isSimilar(required),"NBT contains legal one-item sample with original metadata");
        required.setAmount(273);check(!signature.equals(ContributionFingerprint.item(required)),"quantity changes invalidate confirmation fingerprint");
        required.setAmount(272);var meta=required.getItemMeta();meta.displayName(Component.text("Distinct material"));required.setItemMeta(meta);
        check(!signature.equals(ContributionFingerprint.item(required)),"item metadata changes invalidate confirmation fingerprint");
        var u=TownyUniverse.getInstance();u.newTownInternal("BuildUi"+run.substring(0,8),id("town"));town=u.getTown(id("town"));
        var resident=u.getDataSource().newResident("Builder"+run.substring(0,8),id("player"));resident.setTown(town);town.setMayor(resident);
        actor=new Actor(resident.getUUID());
        var type=Class.forName("cc.crafto.ui.server.CraftoUIPlugin$Connection",true,ui.getClass().getClassLoader());var constructor=type.getDeclaredConstructor(Player.class);constructor.setAccessible(true);Object connection=constructor.newInstance(actor.player);
        for(String key:List.of("ready","nativeMenus","navigation","buildingPreview")){var f=type.getDeclaredField(key);f.setAccessible(true);f.set(connection,true);}
        ((Map<UUID,Object>)field(ui,"connections")).put(actor.uuid,connection);
        var custom=new ProjectDefinition("fixture_requirement272",ProjectType.BUILDING,"Fixture",Material.BRICKS,"",10,List.of(),Map.of(1,new LevelDefinition(1,0,0,List.of(new ItemStack(Material.STONE_BRICKS,272)),Map.of(),List.of())));
        actor.contents[0]=new ItemStack(Material.STONE_BRICKS,64);
        var quote=service.previewContribution(actor.player,custom,false);
        check(quote.ready()&&quote.lines().get(0).needed()==272&&quote.total()==64,"actual contribution preview handles requirement 272");
        var defs=field(builds,"definitions");hall=(ProjectDefinition)defs.getClass().getMethod("project",String.class).invoke(defs,"town_hall");
        var storage=new ItemStack[54];storage[0]=new ItemStack(Material.STONE_BRICKS,64);store.town(town.getUUID()).setStorage(storage,54);
        menu.openDetails(actor.player,hall);original=actor.top;
        check(original.getSize()==54&&original.getItem(28)!=null&&original.getItem(53)!=null,"materials and donation button share the building details inventory");
        check(menu.preview(actor.player,original)!=null,"town hall has a real blueprint preview");
        later(this::stale);
    }
    void click(int slot){var event=new InventoryClickEvent(actor.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"menu click never takes its icon");}
    long fund(){return store.town(town.getUUID()).resourceFund("town_hall",1).count(new ItemStack(Material.STONE_BRICKS));}
    void stale(){
        var hints=actor.packets.stream().map(Protocol::decode).filter(m->m.kind()==Protocol.Kind.NATIVE_MENU&&!Protocol.bool(m.body(),"presentationOnly",false)).toList();
        check(hints.size()==1&&hints.get(0).body().getAsJsonObject("presentation").get("previewSlot").getAsInt()==11,"CraftoUI preview is tied only to the town hall button");
        actor.contents[0].setAmount(65);click(53);
        later(()->{check(actor.top==original&&fund()==0&&actor.contents[0].getAmount()==65,"stale quote refreshes same menu without taking resources");click(53);click(53);later(this::personalDone);});
    }
    void personalDone(){
        check(actor.top==original&&fund()==65&&actor.contents[0]==null,"duplicate confirmation transfers personal materials once and keeps same menu");
        check(actor.opens==1&&actor.closes==0,"supply operation never closes or reopens the building menu");
        check(actor.packets.stream().map(Protocol::decode).anyMatch(m->m.kind()==Protocol.Kind.NATIVE_MENU&&Protocol.bool(m.body(),"presentationOnly",false)),"same-container role and disabled-state updates reach CraftoUI");
        click(13);check(actor.top==original,"source switch stays in same menu");click(53);later(this::warehouseDone);
    }
    void warehouseDone(){
        check(actor.top==original&&fund()==129&&store.town(town.getUUID()).storage()[0]==null,"warehouse contribution conserves stock and stays in same menu");
        try{
            var defs=field(builds,"definitions");var forge=(ProjectDefinition)defs.getClass().getMethod("project",String.class).invoke(defs,"forge");menu.openDetails(actor.player,forge);
            check(menu.preview(actor.player,actor.top)==null,"other building buttons do not supply a preview");
            store.saveOrThrow();finish(null);
        }catch(Exception e){finish(e);}
    }
    synchronized void finish(Throwable error){if(done)return;done=true;if(error!=null){checks.add("FAIL "+error);getLogger().log(java.util.logging.Level.SEVERE,"BUILD_MENU_PROBE_FAIL",error);}
        try{Files.createDirectories(proof);Files.write(proof.resolve("checks.txt"),checks);Files.writeString(proof.resolve("result.txt"),"run_id="+run+"\nfailed="+checks.stream().filter(x->x.startsWith("FAIL")).count()+"\nchecks="+checks.size()+"\n");}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Cannot save proof",e);}}
    @Override public void onDisable(){if(proof!=null&&!done)finish(new IllegalStateException("probe not completed"));}
    final class Actor {
        final UUID uuid;final Player player;final PlayerInventory inventory;InventoryView view;Inventory top=Bukkit.createInventory(null,9);ItemStack[] contents=new ItemStack[36];List<byte[]> packets=new ArrayList<>();int opens,closes;
        Actor(UUID uuid){this.uuid=uuid;
            inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class[]{PlayerInventory.class},(o,m,a)->switch(m.getName()){
                case "getStorageContents","getContents"->Arrays.stream(contents).map(x->x==null?null:x.clone()).toArray(ItemStack[]::new);
                case "setStorageContents"->{contents=Arrays.stream((ItemStack[])a[0]).map(x->x==null?null:x.clone()).toArray(ItemStack[]::new);yield null;}
                case "getItemInMainHand","getItemInOffHand","getItem"->new ItemStack(Material.AIR);case "getHeldItemSlot"->0;case "getSize"->36;case "getHolder"->null;default->null;});
            EntityScheduler scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class[]{EntityScheduler.class},(o,m,a)->{
                if(m.getName().equals("run"))return Bukkit.getRegionScheduler().run((org.bukkit.plugin.Plugin)a[0],new Location(world,1,160,1),(Consumer<ScheduledTask>)a[1]);
                if(m.getName().equals("runDelayed"))return Bukkit.getRegionScheduler().runDelayed((org.bukkit.plugin.Plugin)a[0],new Location(world,1,160,1),(Consumer<ScheduledTask>)a[1],(Long)a[3]);throw new UnsupportedOperationException(m.getName());});
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
                case "getUniqueId"->uuid;case "getName"->TownyAPI.getInstance().getResident(uuid).getName();case "getServer"->Bukkit.getServer();case "isOnline","hasPermission"->true;case "isOp","isSneaking"->false;
                case "getWorld"->world;case "getLocation"->new Location(world,1,160,1);case "getScheduler"->scheduler;case "getInventory"->inventory;case "getOpenInventory"->view;
                case "openInventory"->{opens++;top=(Inventory)a[0];Bukkit.getPluginManager().callEvent(new InventoryOpenEvent(view));yield view;}
                case "closeInventory"->{closes++;yield null;}case "sendPluginMessage"->{packets.add(((byte[])a[2]).clone());yield null;}case "sendMessage","sendActionBar","updateInventory","saveData"->null;
                case "hashCode"->uuid.hashCode();case "equals"->o==a[0];case "toString"->"BuildMenuActor";default->null;});
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class[]{InventoryView.class},(o,m,a)->switch(m.getName()){
                case "getPlayer"->player;case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getType"->InventoryType.CHEST;case "title"->Component.text("Build menu probe");case "getTitle","getOriginalTitle"->"Build menu probe";case "getCursor"->new ItemStack(Material.AIR);
                case "convertSlot"->(Integer)a[0]<top.getSize()?a[0]:(Integer)a[0]-top.getSize();case "getInventory"->(Integer)a[0]<top.getSize()?top:inventory;
                case "getItem"->(Integer)a[0]<top.getSize()?top.getItem((Integer)a[0]):inventory.getItem((Integer)a[0]-top.getSize());case "getSlotType"->InventoryType.SlotType.CONTAINER;case "countSlots"->top.getSize()+36;default->null;});
        }
    }
}
