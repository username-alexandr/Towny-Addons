package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.*;
import org.bukkit.craftbukkit.*;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.core.BlockPos;
import com.palmergames.bukkit.towny.*;
import ru.neverland.mintcontracts.MintTownyContracts;
import ru.neverland.mintcontracts.orders.PublicOrders;
import ru.neverland.mintcontracts.fishing.*;

/** Native pond calculation, real fish events/SQLite and explicit networkless CraftPlayer actor. */
public final class FishingProbe extends TntRuntimeProbe {
    static final UUID FISHER=id("fisher-052");MintTownyContracts contracts;FishingProtection fishing;PublicOrders orders;Map<UUID,PublicOrders.Order> tasks;Actor actor;UUID orderId;
    final class Actor extends CraftPlayer {
        Actor()throws Exception{super((CraftServer)Bukkit.getServer(),new ServerPlayer(((CraftServer)Bukkit.getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(FISHER,"NativeFisher"),ClientInformation.createDefault()));
            var f=net.minecraft.world.entity.Entity.class.getDeclaredField("bukkitEntity");f.setAccessible(true);f.set(getHandleRaw(),this);getHandleRaw().setPosRaw(15.5,161,8.5);
            var region=io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();f=region.getClass().getDeclaredField("allEntities");f.setAccessible(true);((ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity>)f.get(region)).add(getHandleRaw());taskScheduler.registerTo(region.entitySchedulerTickList);getInventory().setItemInMainHand(new ItemStack(Material.FISHING_ROD));
        }
        @Override public boolean isOnline(){return true;}@Override public boolean hasPermission(String p){return true;}@Override public GameMode getGameMode(){return GameMode.SURVIVAL;}
        @Override public void sendMessage(String message){}@Override public void sendMessage(String... message){}@Override public void sendMessage(net.kyori.adventure.text.Component message){}@Override public void sendActionBar(net.kyori.adventure.text.Component message){}
    }
    @Override void setup()throws Exception{
        contracts=(MintTownyContracts)Bukkit.getPluginManager().getPlugin("NeverLandTownyContracts");fishing=contracts.fishing();orders=(PublicOrders)field(contracts,"publicOrders");
        check(fishing!=null&&fishing.ready(),"native fishing authority and durable stock enabled");
        if(!restart)TownyUniverse.getInstance().getDataSource().newResident("NativeFisher",FISHER).save();actor=new Actor();tasks=(Map<UUID,PublicOrders.Order>)field(orders,"orders");
        if(restart){orderId=UUID.fromString(Files.readString(proof.resolve("order.txt")));check(tasks.get(orderId).progress()==3,"accepted fishing progress survives full Folia restart");
            var repo=(ru.neverland.mintcontracts.service.ContractRepository)field(contracts,"repository");var city=repo.allActive().stream().filter(c->c.templateId().equals("native_city_fishing")).findFirst().orElseThrow();
            check(city.progress()==1&&!timerPaused(city)&&!city.fishingPaused(),"city progress and automatic recovery survive restart");
            pond(15,true);attempt(15,false,()->{check(tasks.get(orderId).progress()==3,"restart does not refill depleted water or grant another progress");finish();});return;}
        pond(15,false);var ordinary=hook(15);check(!event(ordinary,PlayerFishEvent.State.BITE,null).isCancelled(),"ordinary fishing without a quest keeps vanilla rules");ordinary.remove();
        var row=new PublicOrders.Order(UUID.randomUUID(),FISHER,"native_fishing","Проверка рыбалки",PublicOrders.Type.FISHING,"COD",10,0,1000,3600000,1,"ACTIVE");orderId=row.id();tasks.put(row.id(),row);call(orders,"save",new Class<?>[]{});Files.writeString(proof.resolve("order.txt"),orderId.toString());
        pond(15,false);var hook=hook(15);var bite=event(hook,PlayerFishEvent.State.BITE,null);check(bite.isCancelled(),"single water block cannot begin quest fishing");
        Item sample=world.dropItem(new Location(world,15.5,160,8.5),new ItemStack(Material.COD));var caught=event(hook,PlayerFishEvent.State.CAUGHT_FISH,sample);check(caught.isCancelled()&&caught.getExpToDrop()==0,"single-water fish and experience cancelled");sample.remove();hook.remove();
        check(tasks.get(orderId).progress()==0,"single-water catch grants no task progress");
        pond(15,false);world.getBlockAt(16,160,8).setType(Material.WATER,false);world.getBlockAt(15,160,9).setType(Material.WATER,false);world.getBlockAt(16,160,9).setType(Material.WATER,false);
        var tiny=hook(15);check(event(tiny,PlayerFishEvent.State.BITE,null).isCancelled(),"artificial 2x2 water remains too small");tiny.remove();
        pond(15,3,1);var shallow=hook(15);check(event(shallow,PlayerFishEvent.State.BITE,null).isCancelled(),"large one-deep water reports insufficient depth");shallow.remove();
        pond(15,3,3);cancelledCatch(()->attempt(15,true,()->attempt(15,true,()->attempt(16,false,()->{
            check(tasks.get(orderId).progress()==2,"chunk-edge recast does not reset depleted stock");pond(49,true);actor.getHandleRaw().setPosRaw(49.5,161,8.5);
            attempt(49,true,()->{check(tasks.get(orderId).progress()==3,"moving to other water grants one legitimate catch");cityCatch();});
        }))));
    }
    void pond(int x,boolean wide){pond(x,wide?3:0,wide?3:1);}
    void pond(int x,int radius,int depth){for(int bx=x-3;bx<=x+3;bx++)for(int z=5;z<=11;z++)for(int y=157;y<=163;y++)world.getBlockAt(bx,y,z).setType(Material.AIR,false);
        for(int bx=x-radius;bx<=x+radius;bx++)for(int z=8-radius;z<=8+radius;z++)for(int y=161-depth;y<=160;y++)world.getBlockAt(bx,y,z).setType(Material.WATER,false);
    }
    FishHook hook(int x)throws Exception{
        var level=((CraftWorld)world).getHandle();var nativeHook=new FishingHook((net.minecraft.world.entity.player.Player)actor.getHandleRaw(),level,0,0);nativeHook.setPos(x+.5,160.5,8.5);nativeHook.setNoGravity(true);level.addFreshEntity(nativeHook);
        boolean open=nativeHook.calculateOpenWater(new BlockPos(x,160,8));var f=FishingHook.class.getDeclaredField("openWater");f.setAccessible(true);f.setBoolean(nativeHook,open);var hook=(FishHook)nativeHook.getBukkitEntity();check(hook.isInOpenWater()==open,"native Minecraft habitat calculation used");return hook;
    }
    PlayerFishEvent event(FishHook hook,PlayerFishEvent.State state,Entity item){var event=new PlayerFishEvent(actor,item,hook,EquipmentSlot.HAND,state);event.setExpToDrop(3);Bukkit.getPluginManager().callEvent(event);return event;}
    void cancelledCatch(Work next)throws Exception{
        var hook=hook(15);check(!event(hook,PlayerFishEvent.State.BITE,null).isCancelled(),"large deep artificial pond passes habitat");
        later(5,()->{Item item=world.dropItem(new Location(world,15.5,160,8.5),new ItemStack(Material.COD));var caught=event(hook,PlayerFishEvent.State.CAUGHT_FISH,item);check(!caught.isCancelled(),"stock initially approves cancellation fixture");caught.setCancelled(true);
            later(7,()->{check(tasks.get(orderId).progress()==0,"final foreign cancellation grants no quest progress");item.remove();if(hook.isValid())hook.remove();next.run();});
        });
    }
    boolean timerPaused(Object contract)throws Exception{Object timer=contract.getClass().getMethod("timer").invoke(contract);return (boolean)timer.getClass().getMethod("paused").invoke(timer);}
    long timerRemaining(Object contract,long now)throws Exception{Object timer=contract.getClass().getMethod("timer").invoke(contract);return (long)timer.getClass().getMethod("remaining",long.class).invoke(timer,now);}
    void cityCatch()throws Exception{
        var personal=tasks.get(orderId);tasks.put(orderId,new PublicOrders.Order(personal.id(),personal.player(),personal.template(),personal.title(),personal.type(),personal.target(),personal.goal(),personal.progress(),personal.cents(),personal.remaining(),personal.cycle(),"CANCELLED",personal.rarity(),personal.boardDay()));
        var universe=TownyUniverse.getInstance();UUID townId=id("fishing-town");universe.newTownInternal("FishingTown",townId);var town=TownyAPI.getInstance().getTown(townId);var resident=TownyAPI.getInstance().getResident(FISHER);resident.setTown(town);town.setMayor(resident);town.setRegistered(System.currentTimeMillis());
        var plot=new com.palmergames.bukkit.towny.object.TownBlock(3,0,universe.getWorld(world.getName()));universe.addTownBlock(plot);plot.setTown(town);town.setHomeBlock(plot);town.setSpawn(origin);plot.save();resident.save();town.save();
        var repo=(ru.neverland.mintcontracts.service.ContractRepository)field(contracts,"repository");long now=System.currentTimeMillis();
        var city=new ru.neverland.mintcontracts.model.ActiveContract(UUID.randomUUID(),townId,"native_city_fishing",now,now+3600000,0,10,0,Map.of());
        city.snapshot(new ru.neverland.mintcontracts.model.ContractDefinition("native_city_fishing","Городская рыбалка",ru.neverland.mintcontracts.model.ContractType.FISH,Material.COD,0,List.of(),"COD",null,10,0,3600));repo.add(city);repo.saveOrThrow();
        check(!orders.hasFishingTask(FISHER)&&contracts.companyContracts().hasFishingTask(FISHER),"city-only fishing task activates the same protection");
        var hook=hook(49);event(hook,PlayerFishEvent.State.BITE,null);later(5,()->{
            Item item=world.dropItem(new Location(world,49.5,160,8.5),new ItemStack(Material.COD));check(!event(hook,PlayerFishEvent.State.CAUGHT_FISH,item).isCancelled(),"valid city catch uses remaining shared stock");
            later(5,()->{check(city.progress()==1&&tasks.get(orderId).progress()==3,"one city catch credits city progress and never a cancelled personal task");item.remove();if(hook.isValid())hook.remove();
                var stock=field(fishing,"stock");set(stock,"failed",true);long paused=System.currentTimeMillis();call(contracts.companyContracts(),"fishingTimers",new Class<?>[]{long.class},paused);check(timerPaused(city)&&city.fishingPaused(),"unavailable stock neutrally pauses city fishing deadline");
                check(timerRemaining(city,paused+60000)==timerRemaining(city,paused),"unavailable minute cannot expire or fail city fishing task");repo.saveOrThrow();finish();
            });
        });
    }
    void attempt(int x,boolean expected,Work next)throws Exception{
        var hook=hook(x);var bite=event(hook,PlayerFishEvent.State.BITE,null);check(!bite.isCancelled(),"valid pond bite reaches stock reservation");
        later(5,()->{Item item=world.dropItem(new Location(world,x+.5,160,8.5),new ItemStack(Material.COD));int before=tasks.get(orderId).progress();var caught=event(hook,PlayerFishEvent.State.CAUGHT_FISH,item);check(caught.isCancelled()!=expected,expected?"available stock permits caught fish":"depleted shared stock blocks caught fish");
            if(expected){var replay=event(hook,PlayerFishEvent.State.CAUGHT_FISH,item);check(replay.isCancelled(),"replayed catch cannot receive another approval");}
            later(5,()->{check(tasks.get(orderId).progress()==before+(expected?1:0),"catch credits exactly once or stays unchanged");item.remove();if(hook.isValid())hook.remove();next.run();});
        });
    }
    @Override void finish()throws Exception{
        call(orders,"save",new Class<?>[]{});TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; genuine Folia/Minecraft pond calculation, caught events, stock depletion and restart; explicit offline actor; no network client\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());
    }
}
