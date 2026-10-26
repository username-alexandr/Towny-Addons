package ru.neverland.probe;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import io.papermc.paper.event.player.PlayerTradeEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.SuiteState;
import ru.neverland.townydiplomacy.*;

/** Disposable real Folia/Towny/Diplomacy integration; player and inventory view are simulated. */
public final class VillagerTradeProbe extends JavaPlugin {
    final List<String> checks=new ArrayList<>();World world;JavaPlugin control;Town host,visitor,neutral;
    AbstractVillager villager,trader;Actor own,guest,other,nomad;Path proof;
    static UUID id(String s){return UUID.nameUUIDFromBytes(("villager-trade-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add("PASS "+text);}
    @Override public void onEnable(){
        if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_VILLAGER_TRADE_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){getServer().getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath();world=Bukkit.getWorlds().getFirst();
        Bukkit.getPluginManager().registerEvents(new Listener(){@EventHandler(priority=EventPriority.MONITOR) public void spawned(org.bukkit.event.entity.CreatureSpawnEvent e){if(e.getEntity() instanceof AbstractVillager)getLogger().info("Probe spawn "+e.getEntityType()+" cancelled="+e.isCancelled()+" reason="+e.getSpawnReason());}},this);
        var chunks=new ArrayList<java.util.concurrent.CompletableFuture<?>>();
        for(int x=-1;x<=4;x++)for(int z=-1;z<=1;z++)chunks.add(world.getChunkAtAsync(x,z));
        java.util.concurrent.CompletableFuture.allOf(chunks.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((ignored,error)->{
            if(error!=null){finish(error);return;}
            Bukkit.getRegionScheduler().runDelayed(this,new Location(world,1,160,1),t->{try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()){
                try{Files.createDirectories(proof);run();}catch(Throwable failure){finish(failure);}
            }},50);
        });
    }
    void finish(Throwable error){try{
        if(error==null){TownyUniverse.getInstance().getDataSource().saveAll();TownyUniverse.getInstance().getDataSource().saveQueues();Files.writeString(proof.resolve("passed.txt"),"PASS "+checks.size()+" checks\n");}
        else{getLogger().log(java.util.logging.Level.SEVERE,"VILLAGER_TRADE_FAIL",error);var text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));Files.writeString(proof.resolve("failed.txt"),text.toString());}
        Files.write(proof.resolve("checks.txt"),checks);
    }catch(Exception e){e.printStackTrace();}Bukkit.getGlobalRegionScheduler().runDelayed(this,t->Bukkit.shutdown(),2);}
    Town makeTown(String name,int chunk)throws Exception{
        var u=TownyUniverse.getInstance();u.newTownInternal(name,id(name));var town=u.getTown(id(name));
        var resident=u.getDataSource().newResident(name+"Mayor",id(name+"Mayor"));resident.setTown(town);town.setMayor(resident);
        var claim=new TownBlock(chunk,0,TownyAPI.getInstance().getTownyWorld(world));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);
        town.setSpawn(new Location(world,chunk*16+1,160,1));claim.save();resident.save();town.save();return town;
    }
    void run()throws Exception{
        control=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");check(control!=null&&control.isEnabled()&&control.getDescription().getVersion().equals("0.4.12"),"Control 0.4.12 enabled");
        var tw=TownyAPI.getInstance().getTownyWorld(world);tw.setUsingTowny(true);tw.save();
        for(int x=0;x<=3;x++)world.addPluginChunkTicket(x,0,this);
        boolean restart=TownyAPI.getInstance().getTown(id("TradeHost"))!=null;
        host=restart?TownyAPI.getInstance().getTown(id("TradeHost")):makeTown("TradeHost",0);
        visitor=restart?TownyAPI.getInstance().getTown(id("TradeGuest")):makeTown("TradeGuest",1);
        neutral=restart?TownyAPI.getInstance().getTown(id("TradeOther")):makeTown("TradeOther",2);
        if(!restart)TownyUniverse.getInstance().getDataSource().newResident("TradeNomad",id("nomad")).save();
        own=new Actor(host.getMayor().getUUID());guest=new Actor(visitor.getMayor().getUUID());other=new Actor(neutral.getMayor().getUUID());nomad=new Actor(id("nomad"));
        villager=world.spawn(new Location(world,1,160,1),Villager.class,e->{e.setAI(false);e.setGravity(false);e.setProfession(Villager.Profession.FARMER);});
        trader=world.spawn(new Location(world,2,160,1),WanderingTrader.class,e->{e.setAI(false);e.setGravity(false);});
        Bukkit.getRegionScheduler().runDelayed(this,new Location(world,1,160,1),t->{try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()){
            try{scenarios(restart);finish(null);}catch(Throwable failure){finish(failure);}
        }},5);
    }
    void scenarios(boolean restart)throws Exception{
        getLogger().info("Probe villager valid="+villager.isValid()+" dead="+villager.isDead()+" owned="+Bukkit.isOwnedByCurrentRegion(villager)+" at="+villager.getLocation());
        check(villager.isInWorld()&&trader.isInWorld(),"real villagers on owning Folia region; villager="+villager.isInWorld()+", trader="+trader.isInWorld());
        if(restart){
            check(!interact(own,villager,EquipmentSlot.HAND),"own trading survives restart");
            check(!interact(guest,villager,EquipmentSlot.HAND),"persisted Towny alliance permits trading after restart");
            check(trade(other,villager),"neutral trade denied after restart");
            check(trade(nomad,trader),"townless visitor denied after restart");return;
        }
        check(!interact(own,villager,EquipmentSlot.HAND),"own town may interact");
        check(!open(own,villager,false),"own town may open merchant");check(!trade(own,villager),"own town may trade");
        check(interact(guest,villager,EquipmentSlot.HAND),"neutral visitor denied with main hand");
        check(interact(guest,villager,EquipmentSlot.OFF_HAND),"neutral visitor denied with off hand");
        check(open(guest,villager,false),"neutral merchant inventory denied");check(trade(guest,villager),"neutral final exchange denied");
        check(trade(nomad,villager),"townless visitor denied");check(trade(guest,trader),"wandering trader protected inside town");
        guest.op=true;check(trade(guest,villager),"OP does not silently bypass city relation");guest.op=false;
        visitor.addAlly(host);check(trade(guest,villager),"one-sided visitor alliance cannot grant access");host.addAlly(visitor);
        check(!interact(guest,villager,EquipmentSlot.HAND),"mutual town alliance permits interaction");
        check(!open(guest,villager,false),"mutual town alliance permits merchant inventory");check(!trade(guest,villager),"mutual town alliance permits final exchange");
        check(!trade(guest,trader),"ally may use wandering trader");
        check(open(guest,villager,true),"prior inventory cancellation retained");
        var cancelled=new PlayerTradeEvent(guest.player,villager,recipe(),true,true);cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);check(cancelled.isCancelled(),"prior trade cancellation retained");
        host.removeAlly(visitor);visitor.removeAlly(host);check(trade(guest,villager),"alliance revoked while menu remains open blocks exchange");
        var nation=new Nation("TradeNation",id("nation"));host.setNation(nation);visitor.setNation(nation);
        check(!trade(guest,villager),"same nation permits trading");host.removeNation();visitor.removeNation();
        var first=new Nation("TradeNationA",id("nationA"));var second=new Nation("TradeNationB",id("nationB"));host.setNation(first);visitor.setNation(second);
        first.addAlly(second);second.addAlly(first);check(!trade(guest,villager),"mutually allied nations permit trading");first.removeAlly(second);check(trade(guest,villager),"revoked nation alliance denies trading");host.removeNation();visitor.removeNation();
        var claim=TownyAPI.getInstance().getTownBlock(villager.getLocation());claim.setTown(visitor);check(!trade(guest,villager)&&trade(own,villager),"current merchant plot owner is used");claim.setTown(host);
        var wild=world.spawn(new Location(world,49,160,1),Villager.class,e->{e.setAI(false);e.setGravity(false);});
        check(!trade(nomad,wild),"wilderness trading remains available");wild.remove();
        guest.location=new Location(world,49,160,1);check(trade(guest,villager),"player across town border cannot bypass merchant plot check");guest.location=new Location(world,1,160,1);
        check(!open(guest,Bukkit.createMerchant("Standalone probe"),false),"standalone plugin merchant unaffected");
        control.getConfig().set("villager-trading.enabled",false);check(!trade(guest,villager),"config switch restores previous behavior");control.getConfig().set("villager-trading.enabled",null);
        check(trade(guest,villager),"old config without key enables protection by default");
        var dip=(NeverLandTownyDiplomacy)Bukkit.getPluginManager().getPlugin("NeverLandTownyDiplomacy");check(dip!=null&&dip.isEnabled(),"real Diplomacy provider enabled");var service=dip.diplomacy();
        var agreement=service.offer(Bukkit.getConsoleSender(),host,visitor,TreatyType.ALLIANCE,1,Treaty.SanctionScope.NONE,"probe");
        check(trade(guest,villager),"pending diplomatic alliance grants nothing");service.change(Bukkit.getConsoleSender(),visitor,agreement.id(),"accept");
        check(!trade(guest,villager)&&!open(guest,villager,false),"active Diplomacy ALLIANCE permits merchant trading");
        var embargo=service.offer(Bukkit.getConsoleSender(),host,visitor,TreatyType.EMBARGO,1,Treaty.SanctionScope.NONE,"probe");check(trade(guest,villager),"embargo overrides alliance");
        service.change(Bukkit.getConsoleSender(),host,embargo.id(),"end");check(!trade(guest,villager),"lifting embargo restores allied trade");
        var sanctions=service.offer(Bukkit.getConsoleSender(),host,visitor,TreatyType.SANCTIONS,1,Treaty.SanctionScope.TRADE,"probe");check(trade(guest,villager),"trade sanction overrides alliance");service.change(Bukkit.getConsoleSender(),host,sanctions.id(),"end");
        var values=new LinkedHashMap<>(service.repository().all());values.put(agreement.id(),values.get(agreement.id()).end());service.repository().commit(values,List.of(),List.of());
        check(trade(guest,villager),"ended diplomatic alliance immediately revokes exchange");
        var tradeOnly=service.offer(Bukkit.getConsoleSender(),host,visitor,TreatyType.TRADE,1,Treaty.SanctionScope.NONE,"probe");service.change(Bukkit.getConsoleSender(),visitor,tradeOnly.id(),"accept");
        check(trade(guest,villager),"trade discount agreement alone is not an alliance");
        SuiteState.freeze("NeverLandTownyDiplomacy");check(trade(guest,villager),"paused diplomacy cannot grant access");check(!trade(own,villager),"paused diplomacy preserves own-city trading");SuiteState.thaw("NeverLandTownyDiplomacy");
        host.addAlly(visitor);visitor.addAlly(host);host.save();visitor.save();
        villager.remove();trader.remove();
    }
    MerchantRecipe recipe(){var r=new MerchantRecipe(new ItemStack(Material.BREAD),10);r.addIngredient(new ItemStack(Material.EMERALD));return r;}
    boolean interact(Actor p,AbstractVillager merchant,EquipmentSlot hand){var e=new PlayerInteractEntityEvent(p.player,merchant,hand);Bukkit.getPluginManager().callEvent(e);return e.isCancelled();}
    boolean trade(Actor p,AbstractVillager merchant){var e=new PlayerTradeEvent(p.player,merchant,recipe(),true,true);Bukkit.getPluginManager().callEvent(e);return e.isCancelled();}
    boolean open(Actor p,Merchant merchant,boolean cancelled){
        var contents=Bukkit.createInventory(null,9);var inventory=(MerchantInventory)Proxy.newProxyInstance(MerchantInventory.class.getClassLoader(),new Class[]{MerchantInventory.class},(o,m,a)->switch(m.getName()){
            case "getMerchant"->merchant;case "getType"->InventoryType.MERCHANT;case "getHolder"->null;default->m.invoke(contents,a);});
        var view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class[]{InventoryView.class},(o,m,a)->switch(m.getName()){
            case "getPlayer"->p.player;case "getTopInventory"->inventory;case "getBottomInventory"->p.inventory;case "getType"->InventoryType.MERCHANT;case "getTitle","getOriginalTitle"->"Probe";default->null;});
        var event=new InventoryOpenEvent(view);event.setCancelled(cancelled);Bukkit.getPluginManager().callEvent(event);return event.isCancelled();
    }
    final class Actor {
        final Player player;final PlayerInventory inventory;boolean op;Location location=new Location(world,1,160,1);
        Actor(UUID uuid){
            inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class[]{PlayerInventory.class},(o,m,a)->switch(m.getName()){
                case "getItem","getItemInMainHand","getItemInOffHand"->new ItemStack(Material.AIR);case "getHeldItemSlot"->0;case "getType"->InventoryType.PLAYER;case "getStorageContents","getContents"->new ItemStack[36];default->null;});
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
                case "getUniqueId"->uuid;case "getName"->TownyAPI.getInstance().getResident(uuid).getName();case "getServer"->Bukkit.getServer();case "getLocation","getEyeLocation"->location.clone();case "getWorld"->world;case "getInventory"->inventory;
                case "isOnline"->true;case "isOp","hasPermission"->op;case "isSneaking","isInsideVehicle","isDead"->false;case "getGameMode"->GameMode.SURVIVAL;case "sendMessage","sendActionBar"->null;case "hashCode"->uuid.hashCode();case "equals"->o==a[0];case "toString"->"TradeProbePlayer";default->null;});
        }
    }
}
