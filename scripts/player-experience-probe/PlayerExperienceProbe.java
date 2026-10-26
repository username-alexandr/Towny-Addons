package ru.neverland.probe;

import cc.crafto.ui.Protocol;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
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
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.gui.MenuManager;
import ru.neverland.townybuilds.model.*;
import ru.neverland.townybuilds.service.*;

/** Disposable integration: actual Folia, Towny, menus, packets and persistence; simulated player view. */
public final class PlayerExperienceProbe extends JavaPlugin {
    List<String> checks=new ArrayList<>();World world;Town town;Actor mayor,member;
    JavaPlugin buildsPlugin,control,ui;BuildService builds;DataStore data;MenuManager menus;ProjectDefinition project;Path proof;boolean restart;
    UUID id(String s){return UUID.nameUUIDFromBytes(("player-ux-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks.add("PASS "+name);}
    void later(Runnable action){Bukkit.getRegionScheduler().runDelayed(this,new Location(world,1,160,1),t->{try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()){try{action.run();}catch(Throwable e){finish(e);}}},5);}
    @Override public void onEnable(){
        if(!Files.exists(Path.of("ALLOW_DISPOSABLE_PLAYER_EXPERIENCE_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath();world=Bukkit.getWorlds().getFirst();
        world.getChunkAtAsync(0,0).whenComplete((chunk,error)->{if(error!=null){finish(error);return;}later(()->{try{setup();if(restart)restartChecks();else run();}catch(Throwable e){finish(e);}});});
    }
    void setup()throws Exception{
        Files.createDirectories(proof);buildsPlugin=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");control=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");ui=(JavaPlugin)Bukkit.getPluginManager().getPlugin("CraftoUI");
        check(buildsPlugin.isEnabled()&&control.isEnabled()&&ui.isEnabled(),"all updated plugins enabled on real Folia");
        menus=(MenuManager)field(buildsPlugin,"uiMenus");builds=(BuildService)field(menus,"builds");data=(DataStore)field(buildsPlugin,"dataStore");
        var definitions=(DefinitionRegistry)field(buildsPlugin,"definitions");
        project=new ProjectDefinition("ux_fixture",ProjectType.BUILDING,"Пробная ратуша",Material.BELL,"",10,List.of(),Map.of(1,new LevelDefinition(1,0,0,List.of(new ItemStack(Material.STONE,10),new ItemStack(Material.OAK_PLANKS,6)),Map.of(),List.of())));
        ((Map<String,ProjectDefinition>)field(definitions,"projects")).put(project.id(),project);
        var u=TownyUniverse.getInstance();town=u.getTown(id("town"));restart=town!=null;
        if(!restart){u.newTownInternal("PlayerUxTown",id("town"));town=u.getTown(id("town"));for(String name:List.of("Mayor","Member")){var r=u.getDataSource().newResident("Ux"+name,id(name));r.setTown(town);if(name.equals("Mayor"))town.setMayor(r);r.save();}town.save();}
        mayor=new Actor(id("Mayor"));member=new Actor(id("Member"));
        var type=Class.forName("cc.crafto.ui.server.CraftoUIPlugin$Connection",true,ui.getClass().getClassLoader());var ctor=type.getDeclaredConstructor(Player.class);ctor.setAccessible(true);var connection=ctor.newInstance(mayor.player);
        for(String name:List.of("ready","nativeMenus","navigation"))set(connection,name,true);
        ((Map<UUID,Object>)field(ui,"connections")).put(mayor.uuid,connection);
        check(Files.exists(ui.getDataFolder().toPath().resolve("backups/0.8.3/addons.json")),"old UI catalog migrated with backup");
    }
    void run()throws Exception{
        mayor.contents.setItem(0,new ItemStack(Material.STONE,20));mayor.contents.setItem(1,new ItemStack(Material.OAK_PLANKS,2));mayor.contents.setItem(2,new ItemStack(Material.DIAMOND,3));
        var custom=new ItemStack(Material.STONE,5);var meta=custom.getItemMeta();meta.displayName(Component.text("Не расходовать"));custom.setItemMeta(meta);mayor.contents.setItem(3,custom);
        var quote=builds.previewContribution(mayor.player,project,false);
        check(quote.ready()&&quote.total()==12,"preview contains exact missing amount from personal inventory");
        check(mayor.contents.getItem(0).getAmount()==20&&data.town(town.getUUID()).existingResourceFund(project.id())==null,"preview does not take items or create a resource fund");
        check(quote.lines().getFirst().personal()==20,"named stone excluded from ordinary resources");
        check(builds.confirmContribution(member.player,project,quote).status()==ContributionResult.Status.PREVIEW_CHANGED,"quote cannot be confirmed by another player");
        mayor.contents.setItem(0,new ItemStack(Material.STONE,19));
        check(builds.confirmContribution(mayor.player,project,quote).status()==ContributionResult.Status.PREVIEW_CHANGED&&mayor.contents.getItem(0).getAmount()==19,"changed inventory requires new preview and leaves resources untouched");
        mayor.contents.setItem(0,new ItemStack(Material.STONE,20));
        check(builds.unavailableReasons(mayor.player,project).stream().anyMatch(x->x.contains("Сдайте")),"building explains missing materials before clicking");
        check(builds.unavailableReasons(member.player,project).stream().anyMatch(x->x.contains("мэра")),"member sees mayor permission explanation");
        menus.openDetails(mayor.player,project);later(()->{
            var details=hint();check(details.getAsJsonObject("presentation").getAsJsonObject("disabled").has("15"),"disabled building action reason sent to client");
            click(13);later(()->{
                var preview=hint();check(preview.getAsJsonObject("presentation").getAsJsonObject("roles").get("confirm").getAsInt()==53,"confirmation has standard action role");
                check(mayor.contents.getItem(0).getAmount()==20,"opening preview does not contribute");click(45);later(()->{
                    hint();check(mayor.contents.getItem(0).getAmount()==20,"back cancels preview without item loss");
                    click(13);later(()->{hint();click(53);click(53);later(this::afterPersonal);});
                });
            });
        });
    }
    void afterPersonal(){try{
        hint();var fund=data.town(town.getUUID()).existingResourceFund(project.id());
        check(fund.count(new ItemStack(Material.STONE))==10&&fund.count(new ItemStack(Material.OAK_PLANKS))==2,"double click contributes once");
        check(mayor.contents.getItem(0).getAmount()==10&&mayor.contents.getItem(1)==null,"only exact required personal resources deducted");
        check(mayor.contents.getItem(2).getAmount()==3&&mayor.contents.getItem(3).getAmount()==5,"unrelated and named items preserved");
        ItemStack[] warehouse=new ItemStack[54];warehouse[0]=new ItemStack(Material.OAK_PLANKS,8);data.town(town.getUUID()).setStorage(warehouse,54);data.saveOrThrow();
        check(!builds.previewContribution(member.player,project,true).ready(),"warehouse contribution still requires mayor authorization");
        var quote=builds.previewContribution(mayor.player,project,true);check(quote.total()==4,"warehouse preview excludes already collected materials");
        check(builds.confirmContribution(mayor.player,project,quote).status()==ContributionResult.Status.SUCCESS,"authorized warehouse confirmation succeeds");
        check(data.town(town.getUUID()).storage()[0].getAmount()==4,"warehouse retains surplus");
        check(builds.confirmContribution(mayor.player,project,quote).status()==ContributionResult.Status.PREVIEW_CHANGED,"replayed confirmed quote cannot transfer twice");
        check(!builds.previewContribution(mayor.player,project,true).ready(),"completed collection explains no more materials needed");
        var records=new ArrayList<AuditRecord>();AuditJournal.scan(buildsPlugin.getDataFolder().toPath().resolve("audit"),records::add);
        check(records.stream().filter(x->x.kind().equals("BUILD_CONTRIBUTION")).count()==3,"each actual contribution recorded with player identity");
        // A different player's operation must never appear in this player's menu.
        AuditTrail.record(control,"fixture",UUID.randomUUID().toString(),"JOB_SALARY","PAID",AuditTrail.player(member.uuid),AuditRecord.Party.system(),AuditTrail.player(member.uuid),"",0,"999","PRIVATE_OTHER_PLAYER");
        openHistory();
    }catch(Exception e){finish(e);}}
    void openHistory(){mayor.packets.clear();control.getCommand("nlthistory").getExecutor().onCommand(mayor.player,control.getCommand("nlthistory"),"nlthistory",new String[0]);waitHistory(0);}
    void waitHistory(int tries){later(()->{
        if(mayor.top.getItem(22)!=null&&plain(mayor.top.getItem(22)).contains("Загружаем")){if(tries>40)throw new AssertionError("history timeout");waitHistory(tries+1);return;}
        check(mayor.top.getHolder().getClass().getName().endsWith("PersonalHistoryMenu$View"),"personal history opens without administrative permission");
        var packets=mayor.packets.stream().map(Protocol::decode).filter(x->x.kind()==Protocol.Kind.NATIVE_MENU).toList();
        check(packets.stream().anyMatch(x->x.body().get("module").getAsString().equals("Мои операции")),"personal history receives player-facing CraftoUI title");
        long rows=Arrays.stream(mayor.top.getContents()).filter(Objects::nonNull).filter(x->plain(x).contains("Материалы для строительства")).count();
        check(rows==3,"history shows own persisted material receipts");
        check(Arrays.stream(mayor.top.getContents()).filter(Objects::nonNull).noneMatch(x->plain(x).contains("999")||plain(x).contains("PRIVATE_OTHER_PLAYER")),"other player's payment and details absent");
        finish(null);
    });}
    void restartChecks(){var fund=data.town(town.getUUID()).existingResourceFund(project.id());check(fund!=null&&fund.count(new ItemStack(Material.STONE))==10&&fund.count(new ItemStack(Material.OAK_PLANKS))==6,"contribution fund survives full server restart");check(data.town(town.getUUID()).storage()[0].getAmount()==4,"warehouse surplus survives restart");openHistory();}
    String plain(ItemStack item){return item.getItemMeta()==null?"":net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName())+" "+item.getItemMeta().lore();}
    com.google.gson.JsonObject hint(){var packets=mayor.packets.stream().map(Protocol::decode).filter(x->x.kind()==Protocol.Kind.NATIVE_MENU).toList();check(!packets.isEmpty(),"real inventory receives CraftoUI hint");mayor.packets.clear();return packets.getLast().body();}
    void click(int slot){Bukkit.getPluginManager().callEvent(new InventoryClickEvent(mayor.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL));}
    void finish(Throwable error){try{
        Files.createDirectories(proof);if(error==null){data.saveOrThrow();TownyUniverse.getInstance().getDataSource().saveAll();TownyUniverse.getInstance().getDataSource().saveQueues();Files.writeString(proof.resolve("passed.txt"),"PASS "+checks.size()+" checks; restart="+restart+"\n");}
        else{getLogger().log(java.util.logging.Level.SEVERE,"PLAYER_UX_FAIL",error);var writer=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(writer));Files.writeString(proof.resolve("failed.txt"),writer.toString());}
        Files.write(proof.resolve("checks.txt"),checks);
    }catch(Exception e){e.printStackTrace();}Bukkit.getGlobalRegionScheduler().runDelayed(this,t->Bukkit.shutdown(),2);}
    final class Actor {
        final UUID uuid;final Player player;final Inventory contents=Bukkit.createInventory(null,36);Inventory top=Bukkit.createInventory(null,9);InventoryView view;final PlayerInventory inventory;final List<byte[]> packets=new ArrayList<>();boolean closed=true;
        Actor(UUID uuid){this.uuid=uuid;
            inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class[]{PlayerInventory.class},(o,m,a)->switch(m.getName()){
                case "getStorageContents","getContents"->contents.getContents();case "setStorageContents","setContents"->{contents.setContents((ItemStack[])a[0]);yield null;}case "getSize"->36;case "getItem"->contents.getItem((Integer)a[0]);case "getItemInMainHand"->contents.getItem(0);case "getItemInOffHand"->new ItemStack(Material.AIR);case "getHeldItemSlot"->0;case "addItem"->contents.addItem((ItemStack[])a[0]);default->null;});
            EntityScheduler scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class[]{EntityScheduler.class},(o,m,a)->{
                if(m.getName().equals("run"))return Bukkit.getRegionScheduler().run((org.bukkit.plugin.Plugin)a[0],new Location(world,1,160,1),(Consumer<ScheduledTask>)a[1]);
                if(m.getName().equals("runDelayed"))return Bukkit.getRegionScheduler().runDelayed((org.bukkit.plugin.Plugin)a[0],new Location(world,1,160,1),(Consumer<ScheduledTask>)a[1],(Long)a[3]);throw new UnsupportedOperationException(m.getName());});
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
                case "getUniqueId"->uuid;case "getName"->TownyAPI.getInstance().getResident(uuid).getName();case "getServer"->Bukkit.getServer();case "isOnline"->true;
                case "hasPermission"->!String.valueOf(a[0]).contains("admin");case "isOp","isSneaking"->false;case "getWorld"->world;case "getLocation"->new Location(world,1,160,1);case "getGameMode"->GameMode.SURVIVAL;
                case "getScheduler"->scheduler;case "getInventory"->inventory;case "getOpenInventory"->view;case "getItemOnCursor"->new ItemStack(Material.AIR);
                case "openInventory"->{Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view));closed=false;top=(Inventory)a[0];Bukkit.getPluginManager().callEvent(new InventoryOpenEvent(view));yield view;}
                case "closeInventory"->{Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view));closed=true;top=Bukkit.createInventory(null,9);yield null;}
                case "sendPluginMessage"->{packets.add(((byte[])a[2]).clone());yield null;}case "sendMessage","sendActionBar","playSound"->null;case "hashCode"->uuid.hashCode();case "equals"->o==a[0];case "toString"->"PlayerUxActor";default->null;});
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class[]{InventoryView.class},(o,m,a)->switch(m.getName()){
                case "getPlayer"->player;case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getType"->closed?InventoryType.CRAFTING:InventoryType.CHEST;case "title"->Component.text("Player UX fixture");case "getTitle","getOriginalTitle"->"Player UX fixture";case "getCursor"->new ItemStack(Material.AIR);
                case "convertSlot"->(Integer)a[0]<top.getSize()?a[0]:(Integer)a[0]-top.getSize();case "getInventory"->(Integer)a[0]<top.getSize()?top:inventory;case "getItem"->(Integer)a[0]<top.getSize()?top.getItem((Integer)a[0]):inventory.getItem((Integer)a[0]-top.getSize());case "getSlotType"->InventoryType.SlotType.CONTAINER;case "countSlots"->top.getSize()+36;default->null;});
        }
    }
}
