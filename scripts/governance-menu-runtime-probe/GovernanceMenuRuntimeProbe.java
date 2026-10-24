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
import ru.neverland.governance.gui.*;
import ru.neverland.governance.service.*;
import ru.neverland.governance.model.*;

/** Disposable Folia + actual Towny and Governance, with explicitly simulated player/view/scheduler objects. */
public final class GovernanceMenuRuntimeProbe extends JavaPlugin {
    static UUID id(String name){return UUID.nameUUIDFromBytes(("governance-menu-0123-"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID TOWN=id("town"),OTHER=id("other"),MAYOR=id("mayor"),FIRST=id("first"),SECOND=id("second"),GUEST=id("guest");
    JavaPlugin owner;GovernanceService service;GovernanceRepository repository;Town town,other;Actor mayor,ordinary;Path proof;
    int checks;final Deque<Work> steps=new ArrayDeque<>();Inventory stale;GovernanceMenuHolder review;
    interface Work{void run()throws Exception;}
    static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    void check(boolean valid,String label)throws Exception{if(!valid)throw new AssertionError(label);checks++;getLogger().info("CHECK "+label);Files.writeString(proof.resolve("checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    @Override public void onEnable(){
        if(!Boolean.getBoolean("neverland.governanceProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_GOVERNANCE_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getGlobalRegionScheduler().runDelayed(this,t->SuiteState.run(()->{try{setup();schedule();}catch(Throwable e){fail(e);}}),30);
    }
    void setup()throws Exception{
        proof=getDataFolder().toPath();Files.createDirectories(proof);owner=(JavaPlugin)getServer().getPluginManager().getPlugin("NeverLandTownyGovernance");
        check(owner.isEnabled(),"Governance enabled on Folia");service=(GovernanceService)field(owner,"governance");repository=(GovernanceRepository)field(owner,"repository");
        if(Files.exists(proof.resolve("first-passed.txt"))){
            town=TownyAPI.getInstance().getTown(TOWN);check(town!=null,"Towny town survived restart");
            check(service.data(town).offices().get("treasurer").getFirst().residentId().equals(FIRST),"appointed resident persisted after restart");
            check(service.data(town).history().stream().anyMatch(h->h.type().equals("APPOINT")),"appointment audit persisted after restart");
            Files.writeString(proof.resolve("restart-passed.txt"),"PASS "+checks+" assertions\n");getServer().shutdown();return;
        }
        var u=TownyUniverse.getInstance();u.newTownInternal("MenuTown",TOWN);u.newTownInternal("MenuOther",OTHER);town=TownyAPI.getInstance().getTown(TOWN);other=TownyAPI.getInstance().getTown(OTHER);
        for(var entry:Map.of(MAYOR,"MenuMayor",FIRST,"MenuFirst",SECOND,"MenuSecond",GUEST,"MenuGuest").entrySet()){
            Resident r=u.getDataSource().newResident(entry.getValue(),entry.getKey());r.setTown(entry.getKey().equals(GUEST)?other:town);r.save();
        }
        town.setMayor(TownyAPI.getInstance().getResident(MAYOR));other.setMayor(TownyAPI.getInstance().getResident(GUEST));
        for(int i=0;i<35;i++){Resident r=u.getDataSource().newResident("Resident"+i,id("r"+i));r.setTown(town);r.save();}
        var world=getServer().getWorlds().getFirst();int x=12;
        for(Town t:List.of(town,other)){TownBlock b=new TownBlock(x,x,u.getWorld(world.getName()));u.addTownBlock(b);b.setTown(t);t.setHomeBlock(b);t.setSpawn(new Location(world,x*16+1,80,x*16+1));b.save();t.save();x++;}
        mayor=new Actor(MAYOR);ordinary=new Actor(SECOND);
        steps.add(()->{open(mayor);check(mayor.type()==GovernanceMenuHolder.Type.COUNCIL,"council menu opens");click(mayor,11);});
        steps.add(()->{check(mayor.type()==GovernanceMenuHolder.Type.OFFICE,"office button opens management");click(mayor,45);});
        steps.add(()->{check(mayor.type()==GovernanceMenuHolder.Type.RESIDENTS,"assign button opens residents");check(contains(mayor,"MenuFirst"),"offline town resident included");check(!contains(mayor,"MenuGuest"),"foreign resident excluded");check(mayor.top.getItem(53).getType()==Material.ARROW,"residents pagination available");click(mayor,53);});
        steps.add(()->{check(contains(mayor,"Resident9"),"last resident page reachable");click(mayor,45);});
        steps.add(()->clickName(mayor,"MenuFirst"));
        steps.add(()->{check(mayor.type()==GovernanceMenuHolder.Type.OFFICE_CONFIRM,"candidate opens confirmation");check(holders("treasurer").isEmpty(),"preview does not appoint");click(mayor,33);});
        steps.add(()->{check(mayor.type()==GovernanceMenuHolder.Type.RESIDENTS,"back cancels preview");check(holders("treasurer").isEmpty(),"cancel leaves no appointment");clickName(mayor,"MenuFirst");});
        steps.add(()->{review=(GovernanceMenuHolder)mayor.top.getHolder();click(mayor,29);click(mayor,29);});
        steps.add(()->{check(holders("treasurer").size()==1&&holders("treasurer").getFirst().residentId().equals(FIRST),"button appoints through service");check(review.takeAction(29)==null,"confirmation is single-use");check(service.data(town).history().stream().filter(h->h.type().equals("APPOINT")).count()==1,"double click records one appointment");check(((GovernanceMenuHolder)mayor.top.getHolder()).takeAction(45)==null,"full office has no assign action");clickName(mayor,"MenuFirst");});
        steps.add(()->{check(mayor.type()==GovernanceMenuHolder.Type.OFFICE_CONFIRM,"holder opens dismissal confirmation");click(mayor,29);});
        steps.add(()->{check(holders("treasurer").isEmpty(),"dismiss button removes exact holder");check(service.data(town).history().stream().anyMatch(h->h.type().equals("DISMISS")),"dismissal recorded in history");click(mayor,45);});
        steps.add(()->clickName(mayor,"MenuFirst"));
        steps.add(()->{check(service.appoint(mayor.player,"treasurer",SECOND)==GovernanceService.Result.SUCCESS,"concurrent appointment fixture");click(mayor,29);});
        steps.add(()->{check(holders("treasurer").size()==1&&holders("treasurer").getFirst().residentId().equals(SECOND),"stale confirmation cannot overwrite another holder");service.dismiss(mayor.player,"treasurer",SECOND);open(mayor);click(mayor,11);});
        steps.add(()->click(mayor,45));steps.add(()->clickName(mayor,"MenuFirst"));
        steps.add(()->{mayor.allowed=false;click(mayor,29);});
        steps.add(()->{check(holders("treasurer").isEmpty(),"revoked office permission blocks confirmation");mayor.allowed=true;open(mayor);click(mayor,11);});
        steps.add(()->click(mayor,45));steps.add(()->clickName(mayor,"MenuFirst"));
        steps.add(()->{var r=TownyAPI.getInstance().getResident(FIRST);r.setTown(null);r.setTown(other);click(mayor,29);});
        steps.add(()->{check(holders("treasurer").isEmpty(),"resident leaving town blocks confirmation");TownyAPI.getInstance().getResident(FIRST).setTown(null);TownyAPI.getInstance().getResident(FIRST).setTown(town);open(mayor);click(mayor,11);});
        steps.add(()->click(mayor,45));steps.add(()->clickName(mayor,"MenuFirst"));
        steps.add(()->{stale=mayor.top;click(mayor,29);mayor.top=Bukkit.createInventory(null,9);});
        steps.add(()->{check(holders("treasurer").isEmpty(),"closing inventory before scheduled callback cancels mutation");open(ordinary);click(ordinary,11);});
        steps.add(()->{check(((GovernanceMenuHolder)ordinary.top.getHolder()).takeAction(45)==null,"ordinary resident cannot access assignment action");check(service.appoint(ordinary.player,"treasurer",FIRST)==GovernanceService.Result.NO_PERMISSION,"service also rejects ordinary resident");check(service.appoint(mayor.player,"treasurer",GUEST)==GovernanceService.Result.NOT_RESIDENT,"service rejects foreign resident");open(mayor);click(mayor,11);});
        steps.add(()->click(mayor,45));steps.add(()->clickName(mayor,"MenuFirst"));steps.add(()->click(mayor,29));
        steps.add(()->{
            var council=((ru.neverland.townycouncil.NeverLandTownyCouncil)Bukkit.getPluginManager().getPlugin("NeverLandTownyCouncil")).council();
            var appointed=new Actor(FIRST);var category=LawCategory.TAX;
            check(service.mayPropose(appointed.player,town,category),"appointed treasurer retains existing law proposal rights");
            var rights=council.rules(town);council.changeAccess(mayor.player,town,rights.mayor(),rights.revision(),"",FIRST,"law_tax","deny");
            check(!service.mayPropose(appointed.player,town,category),"new personal deny overrides appointed office rights");
            rights=council.rules(town);council.changeAccess(mayor.player,town,rights.mayor(),rights.revision(),"",FIRST,"law_tax","allow");
            check(service.mayPropose(appointed.player,town,category),"new personal allow coexists with appointment menus");
            rights=council.rules(town);council.changeAccess(mayor.player,town,rights.mayor(),rights.revision(),"",FIRST,"law_tax","default");
            check(service.mayPropose(appointed.player,town,category),"reset restores original office rules");
        });
        steps.add(()->{check(holders("treasurer").size()==1,"final appointment committed");check(mayor.scheduled>0,"menu transitions request EntityScheduler");repository.save();TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve("first-passed.txt"),"PASS "+checks+" assertions\n");getServer().shutdown();});
    }
    List<OfficeHolder> holders(String office){return service.data(town).offices().getOrDefault(office,List.of());}
    void open(Actor a){getServer().getPluginCommand("governance").execute(a.player,"governance",new String[]{"council"});}
    boolean contains(Actor a,String name){for(var item:a.top.getContents())if(item!=null&&item.hasItemMeta()&&item.getItemMeta().getDisplayName().contains(name))return true;return false;}
    void clickName(Actor a,String name){for(int i=0;i<a.top.getSize();i++){var item=a.top.getItem(i);if(item!=null&&item.hasItemMeta()&&item.getItemMeta().getDisplayName().contains(name)){click(a,i);return;}}throw new AssertionError("No candidate "+name);}
    void click(Actor a,int slot){var event=new InventoryClickEvent(a.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);getServer().getPluginManager().callEvent(event);if(!event.isCancelled())throw new AssertionError("item extraction not cancelled");}
    void schedule(){if(steps.isEmpty())return;getServer().getGlobalRegionScheduler().runDelayed(this,t->SuiteState.run(()->{try{steps.removeFirst().run();schedule();}catch(Throwable e){fail(e);}}),4);}
    void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"PROBE_FAIL",e);try{Files.writeString(proof.resolve("failed.txt"),e.toString());}catch(Exception ignored){}getServer().shutdown();}
    final class Actor {
        final Player player;PermissibleBase permissions;Inventory top=Bukkit.createInventory(null,9);final Inventory bottom=Bukkit.createInventory(null,36);InventoryView view;boolean allowed=true;int scheduled;
        Actor(UUID id){
            EntityScheduler scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(proxy,m,a)->{
                if(m.getName().equals("runDelayed")){scheduled++;return Bukkit.getGlobalRegionScheduler().runDelayed((org.bukkit.plugin.Plugin)a[0],(Consumer<ScheduledTask>)a[1],(Long)a[3]);}
                throw new UnsupportedOperationException(m.getName());
            });
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->switch(m.getName()){
                case "getUniqueId"->id;case "getName"->TownyAPI.getInstance().getResident(id).getName();case "getServer"->Bukkit.getServer();case "isOnline"->true;case "isOp"->false;
                case "hasPermission"->a[0].equals("townygovernance.offices")?allowed:!a[0].equals("townygovernance.bypass");case "getScheduler"->scheduler;
                case "getOpenInventory"->view;case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,9);yield null;}
                case "sendMessage"->null;case "hashCode"->id.hashCode();case "equals"->proxy==a[0];case "toString"->"Simulated player "+id;default->null;
            });
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(proxy,m,a)->switch(m.getName()){
                case "getTopInventory"->top;case "getBottomInventory"->bottom;case "getPlayer"->player;case "getType"->InventoryType.CHEST;case "getCursor"->new ItemStack(Material.AIR);
                case "countSlots"->top.getSize()+36;case "getTitle","getOriginalTitle"->"Fixture";case "title"->Component.text("Fixture");case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];
                case "getInventory"->(Integer)a[0]<top.getSize()?top:bottom;case "getItem"->(Integer)a[0]<top.getSize()?top.getItem((Integer)a[0]):bottom.getItem((Integer)a[0]-top.getSize());default->null;
            });
        }
        GovernanceMenuHolder.Type type(){return ((GovernanceMenuHolder)top.getHolder()).type();}
    }
}
