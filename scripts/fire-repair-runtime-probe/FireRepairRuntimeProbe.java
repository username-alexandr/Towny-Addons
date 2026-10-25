package ru.neverland.runtime;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.*;
import net.kyori.adventure.text.Component;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.SuiteState;
import ru.neverland.mintevents.service.*;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.*;
import ru.neverland.mintevents.gui.FireRepairMenus;

/** Real Folia/Towny/services and inventories; players and entity scheduler are simulated. */
public final class FireRepairRuntimeProbe extends JavaPlugin {
    static UUID id(String s){return UUID.nameUUIDFromBytes(("fire-repair-probe-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID TOWN=id("town"),OTHER=id("other"),MAYOR=id("mayor"),OWNER=id("owner"),MEMBER=id("member"),ADMIN=id("admin");
    JavaPlugin owner;EventService events;EventRepository repository;FireRepairService repairs;FireRepairMenus menus;
    Town town;World world;TownBlock plot;FireDamage first,second;Actor mayor,landowner,member,admin;
    Path proof;int checks;final Deque<Work> steps=new ArrayDeque<>();interface Work{void run()throws Exception;}
    static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    void check(boolean value,String label)throws Exception{if(!value)throw new AssertionError(label);checks++;getLogger().info("CHECK "+label);Files.writeString(proof.resolve("checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    void denied(Work work,String label)throws Exception{try{work.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){check(true,label);}}
    @Override public void onEnable(){
        if(!Boolean.getBoolean("neverland.fireRepairProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_FIRE_REPAIR_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath();getServer().getGlobalRegionScheduler().runDelayed(this,t->SuiteState.run(()->{try{Files.createDirectories(proof);setup();schedule();}catch(Throwable e){fail(e);}}),30);
    }
    void setup()throws Exception{
        owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyEvents");events=(EventService)field(owner,"events");repository=(EventRepository)field(owner,"repository");repairs=new FireRepairService(owner,new TownyHook(),events);
        check(owner.isEnabled(),"Events enabled on Folia 26.2");
        if(Files.exists(proof.resolve("first-passed.txt"))){
            check(repairs.plots(TOWN).isEmpty(),"confirmed plots stay closed after native server restart");
            check(!events.fires().damage(OTHER).isEmpty(),"unrelated town damage survives restart");
            var audit=new ArrayList<ru.neverland.core.AuditRecord>();ru.neverland.core.AuditJournal.scan(owner.getDataFolder().toPath().resolve("audit"),audit::add);check(audit.stream().filter(r->r.kind().equals("FIRE_REPAIR")&&r.outcome().equals("COMPLETED")).count()==3,"exactly three completion records survive restart including duplicate click");check(audit.stream().anyMatch(r->r.actor().id().equals(ADMIN.toString())&&r.details().contains("Администратор")),"audit identifies administrator and plot");
            Files.writeString(proof.resolve("restart-passed.txt"),"PASS "+checks+" checks\n");getServer().shutdown();return;
        }
        var u=TownyUniverse.getInstance();u.newTownInternal("RepairTown",TOWN);u.newTownInternal("RepairOther",OTHER);town=TownyAPI.getInstance().getTown(TOWN);world=Bukkit.getWorlds().getFirst();
        for(var entry:Map.of(MAYOR,"RepairMayor",OWNER,"RepairOwner",MEMBER,"RepairMember",ADMIN,"RepairAdmin").entrySet()){
            Resident r=u.getDataSource().newResident(entry.getValue(),entry.getKey());if(!entry.getKey().equals(ADMIN))r.setTown(town);r.save();
        }
        town.setMayor(TownyAPI.getInstance().getResident(MAYOR));int size=TownySettings.getTownBlockSize();
        for(int x:List.of(12,13)){TownBlock b=new TownBlock(x,12,u.getWorld(world.getName()));u.addTownBlock(b);b.setTown(town);if(x==12){plot=b;b.setResident(TownyAPI.getInstance().getResident(OWNER));town.setHomeBlock(b);town.setSpawn(new Location(world,x*size+1,80,12*size+1));}b.save();}town.save();
        first=new FireDamage(TOWN,world.getUID(),12*size+1,80,12*size+1,"minecraft:oak_planks",100);
        second=new FireDamage(TOWN,world.getUID(),13*size+1,80,12*size+1,"minecraft:oak_planks",100);
        events.fires().repository().prepare(first);events.fires().repository().prepare(second);
        events.fires().repository().prepare(new FireDamage(OTHER,world.getUID(),14*size+1,80,12*size+1,"minecraft:oak_planks",100));
        mayor=new Actor(MAYOR);landowner=new Actor(OWNER);member=new Actor(MEMBER);admin=new Actor(ADMIN);admin.op=true;
        menus=new FireRepairMenus(owner,new TownyHook(),events,p->p.closeInventory());Bukkit.getPluginManager().registerEvents(menus,owner);
        var selection=repairs.plots(TOWN).getFirst();check(selection.x()==12,"damage grouped using Towny plot size");
        check(repairs.allowed(landowner.player,selection,false),"plot owner can confirm");check(repairs.allowed(mayor.player,selection,false),"mayor can confirm");
        check(!repairs.allowed(member.player,selection,false),"ordinary resident cannot confirm another plot");member.manage=true;check(repairs.allowed(member.player,selection,false),"delegated manager can confirm");member.manage=false;
        check(repairs.allowed(admin.player,selection,true),"OP can confirm without town");admin.op=false;admin.control=true;check(repairs.allowed(admin.player,selection,true),"Control permission grants admin repair");admin.control=false;
        denied(()->repairs.confirm(admin.player,selection,true),"revoked admin permission blocks confirmation");admin.op=true;
        var fire=new ActiveEvent(TOWN,"fire",100,Long.MAX_VALUE,0,100,0,0);repository.put(fire);fire.pausedAt(System.currentTimeMillis());denied(()->repairs.confirm(admin.player,selection,true),"paused fire cannot be acknowledged");repository.remove(TOWN);
        SuiteState.freeze(owner.getName());denied(()->repairs.confirm(admin.player,selection,true),"disabled module cannot acknowledge repairs");SuiteState.thaw(owner.getName());
        steps.add(()->{menus.open(landowner.player,TOWN,false,0);click(landowner,0);});
        steps.add(()->{check(contains(landowner,"Ремонт завершён"),"owner sees completion button");click(landowner,22);click(landowner,22);});
        steps.add(()->{check(events.fires().repository().get(first.id())==null,"button closes selected plot without requiring original blocks");check(events.fires().repository().get(second.id())!=null,"neighbour remains pending");check(!events.hasFireDamageInArea(TOWN,world.getUID(),12*size,12*size,13*size-1,13*size-1),"closed plot area no longer blocks construction");check(events.hasFireDamageInArea(TOWN,world.getUID(),13*size,12*size,14*size-1,13*size-1),"neighbour plot area stays blocked");
            var newer=new FireDamage(TOWN,world.getUID(),first.x(),80,first.z(),"minecraft:oak_planks",200);events.fires().repository().prepare(newer);denied(()->repairs.confirm(admin.player,selection,true),"stale menu cannot close later incident");
            menus.open(landowner.player,TOWN,false,0);click(landowner,0);});
        steps.add(()->{plot.setResident(TownyAPI.getInstance().getResident(MEMBER));click(landowner,22);});
        steps.add(()->{check(events.fires().repository().get(first.id())!=null,"plot transfer invalidates owner confirmation");plot.setResident(TownyAPI.getInstance().getResident(OWNER));Bukkit.getPluginCommand("nltadmin").execute(admin.player,"nltadmin",new String[0]);check(contains(admin,"Ремонт после пожара"),"nltadmin exposes repair category");clickName(admin,"Ремонт после пожара");});
        steps.add(()->{check(contains(admin,"RepairTown"),"admin menu lists affected towns");clickName(admin,"RepairTown");});
        steps.add(()->click(admin,0));
        steps.add(()->{click(admin,22);admin.top=Bukkit.createInventory(null,9);});
        steps.add(()->{check(events.fires().repository().get(first.id())!=null,"closing menu before scheduled click cancels action");menus.open(admin.player,TOWN,true,0);click(admin,0);});
        steps.add(()->click(admin,22));
        steps.add(()->{check(events.fires().repository().get(first.id())==null,"admin button closes selected plot");click(admin,0);});
        steps.add(()->click(admin,22));
        steps.add(()->{check(repairs.plots(TOWN).isEmpty(),"all selected town repairs completed");check(admin.scheduled>0&&landowner.scheduled>0,"menu clicks request EntityScheduler");TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve("first-passed.txt"),"PASS "+checks+" checks\n");getServer().shutdown();});
    }
    boolean contains(Actor a,String name){for(var item:a.top.getContents())if(item!=null&&item.hasItemMeta()&&item.getItemMeta().getDisplayName().contains(name))return true;return false;}
    void clickName(Actor a,String name){for(int i=0;i<a.top.getSize();i++){var item=a.top.getItem(i);if(item!=null&&item.hasItemMeta()&&item.getItemMeta().getDisplayName().contains(name)){click(a,i);return;}}throw new AssertionError("No candidate "+name);}
    void click(Actor a,int slot){var event=new InventoryClickEvent(a.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);getServer().getPluginManager().callEvent(event);if(!event.isCancelled())throw new AssertionError("item extraction not cancelled");}
    void schedule(){if(steps.isEmpty())return;getServer().getGlobalRegionScheduler().runDelayed(this,t->SuiteState.run(()->{try{steps.removeFirst().run();schedule();}catch(Throwable e){fail(e);}}),4);}
    void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"PROBE_FAIL",e);try{Files.writeString(proof.resolve("failed.txt"),e.toString());}catch(Exception ignored){}getServer().shutdown();}
    final class Actor {
        final Player player;PermissibleBase permissions;Inventory top=Bukkit.createInventory(null,9);final Inventory bottom=Bukkit.createInventory(null,36);InventoryView view;boolean manage,op,control;int scheduled;
        Actor(UUID id){
            EntityScheduler scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(proxy,m,a)->{
                if(m.getName().equals("runDelayed")){scheduled++;return Bukkit.getGlobalRegionScheduler().runDelayed((org.bukkit.plugin.Plugin)a[0],(Consumer<ScheduledTask>)a[1],(Long)a[3]);}
                throw new UnsupportedOperationException(m.getName());
            });
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->switch(m.getName()){
                case "getUniqueId"->id;case "getName"->TownyAPI.getInstance().getResident(id).getName();case "getServer"->Bukkit.getServer();case "isOnline"->true;case "isOp"->op;
                case "hasPermission"->switch(a[0].toString()){case "mintevents.use","mintevents.repair"->true;case "mintevents.repair.manage"->manage;case "neverlandtownycontrol.admin"->control;default->false;}; case "getScheduler"->scheduler;
                case "performCommand"->{String[] parts=((String)a[0]).split(" ");yield Bukkit.getPluginCommand(parts[0]).execute((Player)proxy,parts[0],Arrays.copyOfRange(parts,1,parts.length));}
                case "getOpenInventory"->view;case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,9);yield null;}
                case "sendMessage"->null;case "hashCode"->id.hashCode();case "equals"->proxy==a[0];case "toString"->"Simulated player "+id;default->null;
            });
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(proxy,m,a)->switch(m.getName()){
                case "getTopInventory"->top;case "getBottomInventory"->bottom;case "getPlayer"->player;case "getType"->InventoryType.CHEST;case "getCursor"->new ItemStack(Material.AIR);
                case "countSlots"->top.getSize()+36;case "getTitle","getOriginalTitle"->"Fixture";case "title"->Component.text("Fixture");case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];
                case "getInventory"->(Integer)a[0]<top.getSize()?top:bottom;case "getItem"->(Integer)a[0]<top.getSize()?top.getItem((Integer)a[0]):bottom.getItem((Integer)a[0]-top.getSize());default->null;
            });
        }
    }
}
