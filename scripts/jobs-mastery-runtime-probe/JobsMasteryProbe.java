package ru.neverland.runtime;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.*;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.*;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.earnings.IncomeRuntime;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townyjobs.*;
import ru.neverland.townyjobs.api.TownyJobsApi;
import ru.neverland.townyjobs.mastery.*;

/** Real Folia actor, addon services, SQLite and listeners; explicit offline actor and crafting-container fixture. */
public final class JobsMasteryProbe extends TntRuntimeProbe {
    JavaPlugin owner;JobsService jobs;WageService wages;MasteryService mastery;IncomeRuntime income;Actor actor;Inventory top;CraftingInventory crafting;ItemStack[] matrix;Recipe recipe;AtomicLong clock=new AtomicLong(System.currentTimeMillis());boolean permitted=true;
    final class Actor extends CraftPlayer {
        Actor()throws Exception{super((CraftServer)Bukkit.getServer(),new ServerPlayer(((CraftServer)Bukkit.getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(MAYOR,"NativeJobWorker"),ClientInformation.createDefault()));
            var f=net.minecraft.world.entity.Entity.class.getDeclaredField("bukkitEntity");f.setAccessible(true);f.set(getHandleRaw(),this);getHandleRaw().setPosRaw(15.5,161,8.5);
            var region=io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();f=region.getClass().getDeclaredField("allEntities");f.setAccessible(true);((ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity>)f.get(region)).add(getHandleRaw());taskScheduler.registerTo(region.entitySchedulerTickList);
        }
        @Override public boolean isOnline(){return true;}@Override public boolean hasPermission(String p){return p.startsWith("neverlandtownyjobs.")&&(!p.equals("neverlandtownyjobs.mastery")||permitted);}@Override public GameMode getGameMode(){return GameMode.SURVIVAL;}
        @Override public void sendMessage(String s){}@Override public void sendMessage(String...s){}@Override public void sendMessage(net.kyori.adventure.text.Component s){}@Override public void sendActionBar(net.kyori.adventure.text.Component s){}
        @Override public InventoryView openInventory(Inventory inventory){top=inventory;return view();}@Override public InventoryView getOpenInventory(){return view();}
    }
    InventoryView view(){return(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,m,a)->switch(m.getName()){case "getTopInventory"->top;case "getBottomInventory"->actor.getInventory();case "getPlayer"->actor;case "getType"->InventoryType.WORKBENCH;case "convertSlot"->(Integer)a[0];case "getCursor"->actor.getItemOnCursor();case "getInventory"->(Integer)a[0]<10?top:actor.getInventory();default->null;});}
    void waitFor(BooleanSupplier ready,Work next,int left)throws Exception{if(ready.getAsBoolean()){next.run();return;}if(left<=0)throw new AssertionError("jobs native wait timed out");later(1,()->waitFor(ready,next,left-1));}
    void command(String...args){owner.getCommand("townyjobs").execute(actor,"townyjobs",args);}
    @Override void setup()throws Exception{
        owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyJobs");jobs=(JobsService)Bukkit.getServicesManager().load(TownyJobsApi.class);mastery=jobs.masteryService();wages=(WageService)field(owner,"wages");income=(IncomeRuntime)field(wages,"income");
        check(owner.isEnabled()&&mastery!=null&&mastery.ready()&&wages!=null,"actual Jobs 0.3 mastery configuration, Minecraft item registry and salary listeners enabled");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("MasteryCity",DEF);var town=TownyAPI.getInstance().getTown(DEF);var resident=u.getDataSource().newResident("NativeJobWorker",MAYOR);resident.setTown(town);town.setMayor(resident);resident.save();for(int x=-2;x<=3;x++)for(int z=-2;z<=2;z++){var plot=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(plot);plot.setTown(town);if(x==0&&z==0)town.setHomeBlock(plot);plot.save();}town.setSpawn(origin);town.save();
            var data=(DataStore)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds"),"dataStore");data.town(DEF).setConstructionSite(new ConstructionSite("forge",world.getUID(),15,160,8,BlockFace.NORTH,1,1,2,false,6));data.town(DEF).setLevel("forge",1);data.markDirty();data.saveOrThrow();jobs.repository().put(MAYOR,new Career(Profession.BLACKSMITH,Map.of(Profession.BLACKSMITH,1800L),DEF,"forge",1,1,1));}
        actor=new Actor();top=Bukkit.createInventory(null,9);set(jobs,"clock",(java.util.function.LongSupplier)clock::get);set(jobs,"source",(JobsService.PresenceSource)id->id.equals(MAYOR)?new WorkPolicy.Presence(DEF,world.getUID(),15.5,161,8.5,true,true):null);jobs.refresh(false);
        if(restart){resume();return;}
        permitted=false;command("mastery","start","toolsmith");check(mastery.store().get(MAYOR).active()==null,"explicit mastery permission is required");permitted=true;command("mastery","start","toolsmith");
        waitFor(()->mastery.store().get(MAYOR).active()!=null,()->first(),100);
    }
    void smelt(int count,int xp){world.getBlockAt(15,160,8).setType(Material.FURNACE,false);Bukkit.getPluginManager().callEvent(new FurnaceExtractEvent(actor,world.getBlockAt(15,160,8),new ItemStack(Material.IRON_INGOT,count),count,xp));}
    void duty()throws Exception{jobs.beginShift(MAYOR);clock.addAndGet(31000);jobs.signal(MAYOR,Profession.BLACKSMITH);check(jobs.status(MAYOR).equals("На смене")&&SuiteScheduler.owns(actor),"real regional CraftPlayer passes active worksite shift checks");}
    void first()throws Exception{
        mastery.open(actor,0);check(top.getItem(40).getItemMeta().getLore().stream().anyMatch(s->s.contains("Нет")||s.contains("нет срока")),"native mastery menu explains persistent order without deadline");smelt(24,5);
        later(3,()->{check(mastery.store().get(MAYOR).active().stage()==0,"completed-looking event outside shift cannot progress mastery");duty();smelt(24,0);later(2,()->{check(mastery.store().get(MAYOR).active().stage()==0,"smelting without experience proof cannot advance metal work");smelt(24,5);waitFor(()->mastery.store().get(MAYOR).active().stage()==1,()->craftTests(),100);});});
    }
    @SuppressWarnings("unchecked") void container(Material output,int amount){
        actor.getInventory().clear();matrix=new ItemStack[9];if(output==Material.IRON_SWORD){matrix[0]=new ItemStack(Material.IRON_INGOT,8);matrix[3]=new ItemStack(Material.IRON_INGOT,8);matrix[6]=new ItemStack(Material.STICK,8);}else{for(int i:new int[]{0,2,3,4,5,6,7,8})matrix[i]=new ItemStack(Material.IRON_INGOT,8);}
        recipe=Objects.requireNonNull(Bukkit.getRecipe(NamespacedKey.minecraft(output.name().toLowerCase(Locale.ROOT))),"real vanilla recipe");
        crafting=(CraftingInventory)Proxy.newProxyInstance(CraftingInventory.class.getClassLoader(),new Class<?>[]{CraftingInventory.class},(p,m,a)->switch(m.getName()){case "getMatrix"->matrix.clone();case "getType"->InventoryType.WORKBENCH;case "getRecipe"->recipe;case "getResult"->new ItemStack(output,1);case "getSize"->10;case "getHolder","getLocation"->null;case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);default->null;});top=crafting;
    }
    CraftItemEvent craftEvent(boolean cancelled){var event=new CraftItemEvent(recipe,view(),InventoryType.SlotType.RESULT,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);if(cancelled)event.setCancelled(true);return event;}
    void take(Material output,int count,boolean allIngredients){for(int i=0;i<matrix.length;i++)if(matrix[i]!=null&&(allIngredients||matrix[i].getType()!=Material.STICK))matrix[i].setAmount(matrix[i].getAmount()-count);actor.getInventory().addItem(new ItemStack(output,count));}
    void craftTests()throws Exception{
        check(income.ledger().all().size()==1&&mastery.store().get(MAYOR).active().progress()==0,"confirmed metal extraction creates one normal capped salary receipt and enters crafting step");
        container(Material.IRON_SWORD,1);craftEvent(true);later(3,()->{check(mastery.store().get(MAYOR).active().progress()==0,"final foreign event cancellation grants no crafting progress");container(Material.IRON_SWORD,1);craftEvent(false);take(Material.IRON_SWORD,1,false);later(3,()->{check(mastery.store().get(MAYOR).active().progress()==0,"real listener rejects output with only part of recipe consumed");container(Material.IRON_SWORD,1);craftEvent(false);take(Material.IRON_SWORD,1,true);waitFor(()->mastery.store().get(MAYOR).active().progress()==1,()->pause(),100);});});
    }
    void pause()throws Exception{
        check(mastery.store().get(MAYOR).active().stage()==1&&jobs.settings().level(jobs.career(MAYOR))==2,"verified crafting progresses ordered chain without replacing profession experience");var control=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"disable","Jobs"});
        waitFor(()->!owner.isEnabled(),()->{check(Files.isRegularFile(owner.getDataFolder().toPath().resolve("mastery.db")),"controlled disable drains mastery SQLite before unloading classes");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"enable","Jobs"});check(!owner.isEnabled(),"Folia schedules Jobs re-enable for full restart");finish();},150);
    }
    void resume()throws Exception{
        var order=mastery.store().get(MAYOR).active();check(order!=null&&order.stage()==1&&order.progress()==1&&order.definition().steps().get(1).amount()==4&&order.definition().bonus()==.02,"accepted recipe targets and reward stay frozen despite changed config after restart");check(!jobs.onDuty(MAYOR)&&jobs.settings().level(jobs.career(MAYOR))==2,"restart preserves level but never restores an AFK work shift");
        duty();container(Material.IRON_SWORD,3);craftEvent(false);take(Material.IRON_SWORD,3,true);waitFor(()->mastery.store().get(MAYOR).active().stage()==2,()->finishRecipe(),100);
    }
    void finishRecipe()throws Exception{
        container(Material.IRON_CHESTPLATE,2);craftEvent(false);take(Material.IRON_CHESTPLATE,2,true);waitFor(()->mastery.store().get(MAYOR).unlocked().containsKey("toolsmith"),()->{check(mastery.store().get(MAYOR).active()==null&&mastery.bonus(MAYOR,Profession.BLACKSMITH,"forge")==0,"completed order unlocks a specialty without silently enabling it or minting a completion reward");command("mastery","choose","toolsmith");waitFor(()->"toolsmith".equals(mastery.store().get(MAYOR).chosen().get(Profession.BLACKSMITH)),()->{check(Math.abs(jobs.bonus(DEF,"forge")-.055)<.00001,"chosen frozen specialty adds two percent within existing building cap");new JobsMenus(owner,jobs).open(actor);check(top.getItem(4).getItemMeta().getLore().stream().anyMatch(s->s.contains("5.5%")),"native profession summary includes selected mastery in personal contribution");check(income.ledger().used(MAYOR)<=35000&&jobs.mastery(MAYOR).get("available").equals(true),"personal API and original daily salary cap remain in effect");finish();},100);},100);
    }
    @Override void finish()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; genuine Folia Jobs services, Minecraft item registry, regional networkless CraftPlayer, listeners, salary receipts, SQLite, ordered work, controlled disable and restart; explicit synthetic furnace events and crafting container/inventory mutations, no network client or actual bank payout claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
