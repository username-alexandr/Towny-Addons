package ru.neverland.mintcontracts.orders;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.*;
import ru.neverland.core.*;
import ru.neverland.earnings.*;
import static ru.neverland.mintcontracts.orders.PublicOrders.Type.*;

/** Real order listeners, API events, scheduler handoffs, durable progress and income guards. */
public final class MountedOrdersSmoke {
    private static int checks;
    private static String region="player",ride="on foot";
    private static GameMode mode=GameMode.SURVIVAL;
    private static Material held=Material.DIAMOND_SWORD;
    private static boolean flying,gliding,permission=true,natural=true,playerOwned=true;
    private static final Deque<Runnable> victimTasks=new ArrayDeque<>(),playerTasks=new ArrayDeque<>();
    private static Player player;
    private static UUID actor=UUID.randomUUID();
    private static PublicOrders orders;
    private static Map<UUID,PublicOrders.Order> rows;
    private static Path file;
    private static World world;
    private static sun.misc.Unsafe unsafe;
    public static class Fixture extends JavaPlugin {
        @Override public Logger getLogger(){return Logger.getLogger("mounted-orders-test");}
    }
    private static final class Stack extends ItemStack {
        private final Material material;
        Stack(Material material){this.material=material;}
        @Override public Material getType(){return material;}
    }
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(ride+": "+label);}
    private static Object unexpected(String name){throw new AssertionError("Unexpected state access: "+name);}
    private static <T> T stub(Class<T> type,BiFunction<String,Object[],Object> methods){
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(self,m,a)->switch(m.getName()){
            case "equals"->self==a[0];case "hashCode"->System.identityHashCode(self);case "toString"->type.getSimpleName();
            default->methods.apply(m.getName(),a);
        }));
    }
    private static void set(Object o,Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private static ScheduledTask queue(Deque<Runnable> tasks,Consumer<ScheduledTask> task){
        tasks.addLast(()->task.accept(null));
        return stub(ScheduledTask.class,(n,a)->switch(n){case "isCancelled"->false;case "cancel"->ScheduledTask.CancelledState.CANCELLED_BY_CALLER;default->unexpected(n);});
    }
    private static void drain(Deque<Runnable> tasks,String owner){region=owner;while(!tasks.isEmpty())tasks.removeFirst().run();}
    private static PublicOrders.Order order(PublicOrders.Type type,String target){return new PublicOrders.Order(UUID.randomUUID(),actor,"test_"+type,"Проверка",type,target,100,0,1000,600000,1,"ACTIVE");}
    private static UUID add(PublicOrders.Type type,String target){var o=order(type,target);rows.put(o.id(),o);return o.id();}
    private static int progress(UUID id){return rows.get(id).progress();}
    private static PlayerFishEvent fish(PlayerFishEvent.State state,Material target){
        Item caught=stub(Item.class,(n,a)->n.equals("getItemStack")?new Stack(target):unexpected(n));
        FishHook hook=stub(FishHook.class,(n,a)->unexpected(n));
        return new PlayerFishEvent(player,caught,hook,EquipmentSlot.HAND,state);
    }
    private static EntityDeathEvent kill(boolean ranged,boolean dispenser,EntityDamageEvent.DamageCause cause){
        return kill(ranged,dispenser,cause,Material.BOW);
    }
    private static EntityDeathEvent kill(boolean ranged,boolean dispenser,EntityDamageEvent.DamageCause cause,Material weapon){
        var last=new EntityDamageEvent[1];
        var tags=stub(PersistentDataContainer.class,(n,a)->n.equals("has")?natural:unexpected(n));
        Zombie victim=stub(Zombie.class,(n,a)->switch(n){case "getType"->EntityType.ZOMBIE;case "getPersistentDataContainer"->tags;case "getLastDamageCause"->last[0];case "getLocation"->new Location(world,1,64,1);default->unexpected(n);});
        Class<? extends AbstractArrow> arrowType=weapon==Material.TRIDENT?Trident.class:AbstractArrow.class;
        AbstractArrow arrow=stub(arrowType,(n,a)->switch(n){case "getShooter"->dispenser?null:player;case "getWeapon"->new Stack(weapon);default->unexpected(n);});
        Entity direct=ranged?arrow:player;
        DamageSource source=stub(DamageSource.class,(n,a)->switch(n){case "getCausingEntity"->dispenser?null:player;case "getDirectEntity"->direct;case "getDamageType"->null;default->unexpected(n);});
        last[0]=new EntityDamageByEntityEvent(direct,victim,cause,source,10);
        return new EntityDeathEvent(victim,source,new ArrayList<>());
    }
    private static void cast(PlayerFishEvent e){region="player";orders.fish(e);}
    private static void attack(EntityDeathEvent e,boolean ranged){region=ranged?"victim":"player";orders.kill(e);}
    private static void settle(){drain(victimTasks,"victim");drain(playerTasks,"player");}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(sun.misc.Unsafe)uf.get(null);
        world=stub(World.class,(n,a)->unexpected(n));
        var entityScheduler=stub(EntityScheduler.class,(n,a)->n.equals("runDelayed")?queue(playerTasks,(Consumer<ScheduledTask>)a[1]):unexpected(n));
        var locationScheduler=stub(RegionScheduler.class,(n,a)->n.equals("runDelayed")?queue(victimTasks,(Consumer<ScheduledTask>)a[2]):unexpected(n));
        var inventory=stub(PlayerInventory.class,(n,a)->n.equals("getItemInMainHand")?new Stack(held):unexpected(n));
        player=stub(Player.class,(n,a)->{
            if(n.equals("getScheduler"))return entityScheduler;
            if(!region.equals("player")||!playerOwned)throw new AssertionError("Remote mounted player state: "+n);
            return switch(n){case "getUniqueId"->actor;case "getGameMode"->mode;case "isFlying"->flying;case "isGliding"->gliding;case "isInsideVehicle"->!ride.equals("on foot");case "hasPermission"->permission;case "getInventory"->inventory;default->unexpected(n);};
        });
        var server=stub(Server.class,(n,a)->switch(n){
            case "getName","getVersion","getBukkitVersion","getMinecraftVersion"->"test";case "getLogger"->Logger.getLogger("test");case "isStopping"->false;
            case "isOwnedByCurrentRegion"->a[0]!=player||region.equals("player")&&playerOwned;
            case "getRegionScheduler"->locationScheduler;default->unexpected(n);
        });set(null,Bukkit.class,"server",server);
        Path dir=Files.createTempDirectory("mounted-orders-");
        Fixture plugin=(Fixture)unsafe.allocateInstance(Fixture.class);
        var description=new PluginDescriptionFile("NeverLandTownyContracts","test",Fixture.class.getName());
        set(plugin,JavaPlugin.class,"description",description);set(plugin,JavaPlugin.class,"pluginMeta",description);set(plugin,JavaPlugin.class,"dataFolder",dir.toFile());set(plugin,JavaPlugin.class,"isEnabled",true);
        orders=(PublicOrders)unsafe.allocateInstance(PublicOrders.class);file=dir.resolve("public-orders.yml");AtomicFiles.loaded(file);
        rows=new LinkedHashMap<>();set(orders,PublicOrders.class,"orders",rows);set(orders,PublicOrders.class,"file",file);set(orders,PublicOrders.class,"plugin",plugin);
        var income=new IncomeRuntime(plugin,"payments",100000,1000000,true);set(orders,PublicOrders.class,"income",income);set(orders,PublicOrders.class,"guard",new ActionGuard(plugin,"blocks.log"));
        UUID fishing=add(FISHING,"COD"),otherFish=add(FISHING,"COD"),hunting=add(HUNTING,"ZOMBIE");
        for(String mount:List.of("on foot","boat","chest boat","raft","happy ghast","horse","camel","pig","strider","minecart")){
            ride=mount;int caught=progress(fishing),slain=progress(hunting);
            cast(fish(PlayerFishEvent.State.CAUGHT_FISH,Material.COD));check(progress(fishing)==caught,"fish waits for player scheduler");settle();
            check(progress(fishing)==caught+1&&progress(otherFish)==0,"one catch advances only one accepted personal order");
            attack(kill(false,false,EntityDamageEvent.DamageCause.ENTITY_ATTACK),false);settle();check(progress(hunting)==slain+1,"mounted melee credits personal hunter");
            attack(kill(true,false,EntityDamageEvent.DamageCause.PROJECTILE),true);check(playerTasks.isEmpty(),"remote shooter waits for victim event to finish");settle();check(progress(hunting)==slain+2,"mounted bow credits only after player handoff");
        }
        ride="happy ghast";
        for(Material weapon:List.of(Material.AIR,Material.COPPER_SPEAR,Material.NETHERITE_SPEAR,Material.MACE)){
            int before=progress(hunting);held=weapon;attack(kill(false,false,EntityDamageEvent.DamageCause.ENTITY_ATTACK),false);settle();check(progress(hunting)==before+1,"mounted personal melee weapon: "+weapon);
        }
        for(Material weapon:List.of(Material.BOW,Material.CROSSBOW,Material.TRIDENT)){
            int before=progress(hunting);held=Material.STONE;attack(kill(true,false,EntityDamageEvent.DamageCause.PROJECTILE,weapon),true);settle();check(progress(hunting)==before+1,"mounted projectile uses launched weapon even after switching hand: "+weapon);
        }held=Material.DIAMOND_SWORD;
        int caught=progress(fishing),slain=progress(hunting);
        for(var state:List.of(PlayerFishEvent.State.FISHING,PlayerFishEvent.State.BITE,PlayerFishEvent.State.CAUGHT_ENTITY,PlayerFishEvent.State.FAILED_ATTEMPT,PlayerFishEvent.State.REEL_IN))cast(fish(state,Material.COD));
        settle();check(progress(fishing)==caught,"casts, bites, hooked entities and failed attempts do not count");
        cast(fish(PlayerFishEvent.State.CAUGHT_FISH,Material.SALMON));settle();check(progress(fishing)==caught,"wrong catch cannot advance cod order");
        var cancelledFish=fish(PlayerFishEvent.State.CAUGHT_FISH,Material.COD);cast(cancelledFish);cancelledFish.setCancelled(true);settle();check(progress(fishing)==caught,"late fishing cancellation blocks progress");
        var cancelledKill=kill(true,false,EntityDamageEvent.DamageCause.PROJECTILE);attack(cancelledKill,true);cancelledKill.setCancelled(true);settle();check(progress(hunting)==slain,"late death cancellation blocks progress");
        natural=false;attack(kill(true,false,EntityDamageEvent.DamageCause.PROJECTILE),true);settle();check(progress(hunting)==slain,"spawner/egg/command mob without natural proof rejected");natural=true;
        attack(kill(true,true,EntityDamageEvent.DamageCause.PROJECTILE),true);attack(kill(false,false,EntityDamageEvent.DamageCause.THORNS),false);settle();check(progress(hunting)==slain,"dispenser and indirect trap damage rejected");
        for(GameMode blocked:List.of(GameMode.CREATIVE,GameMode.SPECTATOR,GameMode.ADVENTURE)){
            mode=blocked;cast(fish(PlayerFishEvent.State.CAUGHT_FISH,Material.COD));attack(kill(true,false,EntityDamageEvent.DamageCause.PROJECTILE),true);settle();check(progress(fishing)==caught&&progress(hunting)==slain,"mode remains ineligible: "+blocked);
        }mode=GameMode.SURVIVAL;
        for(String blocked:List.of("flight","elytra","permission","module pause")){
            flying=blocked.equals("flight");gliding=blocked.equals("elytra");permission=!blocked.equals("permission");if(blocked.equals("module pause"))SuiteState.freeze(plugin.getName());
            cast(fish(PlayerFishEvent.State.CAUGHT_FISH,Material.COD));attack(kill(true,false,EntityDamageEvent.DamageCause.PROJECTILE),true);settle();check(progress(fishing)==caught&&progress(hunting)==slain,"income restriction retained: "+blocked);
            flying=false;gliding=false;permission=true;SuiteState.thaw(plugin.getName());
        }
        cast(fish(PlayerFishEvent.State.CAUGHT_FISH,Material.COD));attack(kill(true,false,EntityDamageEvent.DamageCause.PROJECTILE),true);playerOwned=false;settle();playerOwned=true;
        check(progress(fishing)==caught&&progress(hunting)==slain,"lost player-region ownership never reads remote state or credits progress");
        cast(fish(PlayerFishEvent.State.CAUGHT_FISH,Material.COD));ride="boat";settle();check(progress(fishing)==caught+1,"changing vehicle before callback does not lose successful catch");caught++;
        region="player";check(!ActionGuard.survival(player),"shared salary/on-foot policy unchanged");
        UUID mining=add(MINING,"IRON_ORE");var progress=PublicOrders.class.getDeclaredMethod("progress",Player.class,PublicOrders.Type.class,String.class);progress.setAccessible(true);progress.invoke(orders,player,MINING,"IRON_ORE");check(progress(mining)==0,"mining keeps original mounted restriction");
        check(income.ledger().all().isEmpty(),"progress alone never issues a duplicate monetary reward");
        var load=PublicOrders.class.getDeclaredMethod("load");load.setAccessible(true);rows.clear();load.invoke(orders);
        check(progress(fishing)==caught&&progress(hunting)==slain&&progress(otherFish)==0,"real saved progress reloads without loss or duplicate credit");
        System.out.println("MountedOrdersSmoke PASS: "+checks+" checks; personal fishing/melee/bow, transport, region ownership, cancellation, provenance, one-order credit and durable progress");
    }
}
