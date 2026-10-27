package ru.neverland.runtime;

import java.lang.reflect.*;import java.nio.file.*;import java.sql.*;import java.util.*;import java.util.concurrent.*;
import org.bukkit.*;import org.bukkit.block.*;import org.bukkit.entity.*;import org.bukkit.event.block.BlockBreakEvent;import org.bukkit.event.inventory.*;import org.bukkit.inventory.*;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.economy.*;import com.palmergames.bukkit.towny.object.economy.adapter.EconomyAdapter;
import ru.neverland.townybuilds.data.DataStore;import ru.neverland.townybuilds.api.RaidStorageApi;import ru.neverland.townybuilds.construction.*;import ru.neverland.townybuilds.storage.StockMath;
import ru.neverland.townysieges.*;import ru.neverland.core.*;

/** Adds actual Towny Account observers, a deterministic economy-adapter double, escrow, camp and admin UI checks. */
public final class CampLootRuntimeProbe extends TntRuntimeProbe {
    final Map<UUID,Double> balances=new HashMap<>();DataStore data;RaidStorageApi stock;RaidCamps.Camp camp;boolean extended;UUID capture;Actor actor;
    @Override void finish()throws Exception{if(extended){saveBalances();super.finish();return;}extended=true;installBank();
        var owner=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");data=(DataStore)field(owner,"dataStore");stock=Bukkit.getServicesManager().load(RaidStorageApi.class);check(stock!=null&&stock.healthy(),"new public raid storage API healthy");
        world.getChunkAtAsync(2,3).whenComplete((v,e)->{if(e!=null){fail(e);return;}later(3,()->{world.getChunkAt(2,3).addPluginChunkTicket(this);actor=new Actor();adminMenus();if(restart)resume();else camps();});});
    }
    void installBank()throws Exception{
        var file=proof.resolve("balances.properties");if(Files.exists(file)){var p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}p.forEach((k,v)->balances.put(UUID.fromString((String)k),Double.valueOf((String)v)));}
        var adapter=(EconomyAdapter)Proxy.newProxyInstance(EconomyAdapter.class.getClassLoader(),new Class<?>[]{EconomyAdapter.class},(p,m,a)->{
            Account account=a!=null&&a.length>0&&a[0] instanceof Account v?v:null;UUID key=account==null?null:account.getUUID();double old=balances.getOrDefault(key,0.0);
            return switch(m.getName()){case "name","toString"->"Disposable deterministic adapter";case "hasAccount"->true;case "getBalance"->old;case "newAccount","deleteAccount"->null;case "getFormattedBalance"->String.valueOf(a[0]);case "setBalance"->{balances.put(key,(Double)a[1]);yield true;}case "renameAccount"->true;case "add","subtract"->{double n=(Double)a[1];boolean add=m.getName().equals("add");if(!add&&old<n)yield false;balances.put(key,old+(add?n:-n));yield true;}default->throw new UnsupportedOperationException(m.getName());};
        });
        var field=TownyEconomyHandler.class.getDeclaredField("economy");field.setAccessible(true);field.set(null,adapter);
        set(plugin.loot(),"bank",new LootBank(){@Override public boolean ready(UUID d,UUID a){return TownyAPI.getInstance().getTown(d)!=null&&TownyAPI.getInstance().getTown(a)!=null;}});
        if(!restart){defender.getAccount().deposit(1000,"native test seed");attacker.getAccount().deposit(100,"native test seed");}
        check(Math.abs(defender.getAccount().getHoldingBalance()+attacker.getAccount().getHoldingBalance()-1100)<.001,"real Towny account totals use deterministic external adapter");
    }
    void saveBalances()throws Exception{var p=new Properties();balances.forEach((k,v)->p.setProperty(k.toString(),v.toString()));try(var out=Files.newOutputStream(proof.resolve("balances.properties"))){p.store(out,"Disposable economy fixture only");}}
    void adminMenus()throws Exception{
        var control=Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");check(control!=null&&control.isEnabled(),"Control 0.4.17 enabled");var menu=field(control,"adminMenu");call(menu,"home",new Class<?>[]{Player.class},actor.player);check(actor.top.getItem(20)!=null&&actor.top.getItem(20).getType()==Material.TNT,"admin home contains separate raid button");
        call(menu,"raids",new Class<?>[]{Player.class},actor.player);check(actor.top.getItem(11).getType()==Material.BOOK&&actor.top.getItem(13).getType()==Material.GOLD_INGOT&&actor.top.getItem(15).getType()==Material.CHEST,"admin panel separates raid history, money and resources");
        call(menu,"logs",new Class<?>[]{Player.class},actor.player);check(Arrays.stream(actor.top.getContents()).anyMatch(i->i!=null&&i.getType()==Material.TNT),"general logging categories include raids");
    }
    void camps()throws Exception{
        camp=new RaidCamps.Camp(id("camp"),ATT,DEF,ACTOR,world.getUID(),40,160,49,40,160,50,"WHITE_BANNER",System.currentTimeMillis(),"READY");
        block(camp.x(),camp.y(),camp.z()).setType(Material.CAMPFIRE,false);block(camp.bx(),camp.by(),camp.bz()).setType(Material.WHITE_BANNER,false);
        call(plugin.camps(),"mark",new Class<?>[]{Block.class,RaidCamps.Camp.class},block(camp.x(),camp.y(),camp.z()),camp);call(plugin.camps(),"mark",new Class<?>[]{Block.class,RaidCamps.Camp.class},block(camp.bx(),camp.by(),camp.bz()),camp);
        persistCamp(()->{set(plugin.camps(),"camps",Map.of(ATT,camp));plugin.camps().pulse();later(10,()->{
            check(plugin.camps().active(ATT,DEF),"real campfire and marked banner activate camp on owning region");actor.location=new Location(world,40.5,160,49.5);plugin.camps().menu(actor.player);check(actor.top.getItem(13).getType()==Material.CROSSBOW,"camp menu exposes raid start");
            plugin.camps().muster(actor.player,defender,()->{throw new AssertionError("empty roster started raid");});later(10,()->{
                check(actor.messages.stream().anyMatch(m->m.contains("мобилизованных")),"real muster rejects absent online army");plugin.camps().breakCamp(new BlockBreakEvent(block(camp.bx(),camp.by(),camp.bz()),actor.player));later(10,()->{check(!plugin.camps().active(ATT,DEF),"destroying camp banner immediately revokes camp");
                    camp=new RaidCamps.Camp(id("camp-rebuilt"),ATT,DEF,ACTOR,world.getUID(),40,160,49,40,160,50,"WHITE_BANNER",System.currentTimeMillis(),"READY");call(plugin.camps(),"mark",new Class<?>[]{Block.class,RaidCamps.Camp.class},block(40,160,49),camp);call(plugin.camps(),"mark",new Class<?>[]{Block.class,RaidCamps.Camp.class},block(40,160,50),camp);persistCamp(()->{set(plugin.camps(),"camps",Map.of(ATT,camp));coreScenario();});});
            });
        });});
    }
    void persistCamp(Work next){CompletableFuture.runAsync(()->{try(var db=DriverManager.getConnection("jdbc:sqlite:"+plugin.getDataFolder().toPath().resolve("raids.db").toAbsolutePath());var p=db.prepareStatement("INSERT OR REPLACE INTO raid_camps VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
        p.setString(1,camp.id().toString());p.setString(2,ATT.toString());p.setString(3,DEF.toString());p.setString(4,ACTOR.toString());p.setString(5,world.getUID().toString());p.setInt(6,camp.x());p.setInt(7,camp.y());p.setInt(8,camp.z());p.setInt(9,camp.bx());p.setInt(10,camp.by());p.setInt(11,camp.bz());p.setString(12,camp.banner());p.setLong(13,camp.created());p.setString(14,"READY");p.executeUpdate();
    }catch(Exception e){throw new CompletionException(e);}}).whenComplete((v,e)->{if(e!=null)fail(e);else later(1,next);});}
    void coreScenario()throws Exception{
        var city=data.town(DEF);city.setLevel("town_hall",2);city.setConstructionSite(new ConstructionSite("town_hall",world.getUID(),31,159,7,BlockFace.NORTH,2,2,1,false));
        var target=stock.core(DEF).orElseThrow();check(target.x()==31&&target.y()==160&&target.z()==8,"primary lectern selected from completed town hall, not secondary stage lectern");
        block(31,160,8).setType(Material.LECTERN,false);block(34,160,8).setType(Material.LECTERN,false);((Lectern)block(31,160,8).getState()).getInventory().setItem(0,new ItemStack(Material.WRITABLE_BOOK));
        var items=new ItemStack[54];items[0]=new ItemStack(Material.STONE,64);items[1]=new ItemStack(Material.STONE,64);items[2]=new ItemStack(Material.STONE,2);items[3]=new ItemStack(Material.OAK_LOG,25);items[4]=new ItemStack(Material.DIAMOND,9);city.setStorage(items,54);
        var full=new ItemStack[54];Arrays.setAll(full,i->new ItemStack(Material.BLACK_CONCRETE,64));data.town(ATT).setStorage(full,54);data.markDirty();data.saveOrThrow();
        origin=new Location(world,31.5,160.5,8.5);capture=RaidLootStore.operation(id("raid"),DEF);shot(4);
    }
    void shot(int left){if(left==0){later(70,()->captureChecks());return;}var entity=tnt(false);blast(entity);entity.remove();later(12,()->shot(left-1));}
    void captureChecks(){await(plugin.loot().store().get(capture),row->{try{
        check(row!=null&&row.state().equals("FUNDED"),"loot money confirmed while full target keeps resource delivery pending");check(block(31,160,8).getType().isAir(),"registered primary lectern destroyed at zero HP");check(block(34,160,8).getType()==Material.LECTERN,"unregistered nearby lectern remains protected");
        check(Math.abs(defender.getAccount().getHoldingBalance()-900)<.001&&Math.abs(attacker.getAccount().getHoldingBalance()-200)<.001,"production LootBank transfers 10 percent through actual Towny account observers");check(StockMath.count(data.town(DEF).storage(),new ItemStack(Material.STONE))==117&&StockMath.count(data.town(DEF).storage(),new ItemStack(Material.OAK_LOG))==23,"resource share removed exactly once into durable escrow");
        check(data.raidStocks().get(capture).state().equals("RESERVED")&&StockMath.total(data.raidStocks().get(capture).cargo())==15,"full target preserves all reserved cargo");
        var permission=new RaidController.Permit(ACTOR,ATT,DEF,id("raid"),System.currentTimeMillis(),false);await(plugin.loot().store().arm(permission,row.core(),new byte[0],plugin.loot().settings()),again->{try{check(again.id().equals(capture)&&again.state().equals("FUNDED"),"duplicate core cannot create a second payment claim");finish();}catch(Exception e){throw new CompletionException(e);}});
    }catch(Exception e){throw new CompletionException(e);}});}
    void resume()throws Exception{
        capture=RaidLootStore.operation(id("raid"),DEF);check(block(31,160,8).getType().isAir(),"completed town hall does not auto-rebuild captured lectern on restart");check(data.raidStocks().get(capture).state().equals("RESERVED"),"resource escrow survives full native restart");
        check(Math.abs(defender.getAccount().getHoldingBalance()-900)<.001&&Math.abs(attacker.getAccount().getHoldingBalance()-200)<.001,"completed bank legs preserved without replay on restart");data.town(ATT).setStorage(new ItemStack[54],54);data.markDirty();data.saveOrThrow();plugin.camps().pulse();later(75,()->resumeChecks());
    }
    void resumeChecks(){await(plugin.loot().store().get(capture),row->{try{
        check(row.state().equals("DONE"),"capacity recovery delivers pending loot after restart");check(StockMath.count(data.town(ATT).storage(),new ItemStack(Material.STONE))==13&&StockMath.count(data.town(ATT).storage(),new ItemStack(Material.OAK_LOG))==2,"exact stolen items delivered to attacker warehouse");check(StockMath.count(data.town(DEF).storage(),new ItemStack(Material.WRITABLE_BOOK))==1,"original lectern book returned to defender without loss");check(Math.abs(defender.getAccount().getHoldingBalance()+attacker.getAccount().getHoldingBalance()-1100)<.001,"money total conserved after delivery retry");
        var log=new ArrayList<AuditRecord>();for(String name:List.of("NeverLandTownySiegesPlus","NeverLandTownyBuilds"))AuditJournal.scan(Bukkit.getPluginManager().getPlugin(name).getDataFolder().toPath().resolve("audit"),log::add);
        check(log.stream().anyMatch(r->r.kind().equals("RAID_CORE"))&&log.stream().anyMatch(r->r.kind().equals("RAID_LOOT_MONEY"))&&log.stream().anyMatch(r->r.kind().equals("RAID_LOOT_RESOURCES")&&r.quantity()>0&&!r.asset().isBlank()),"admin audit includes actor, captured core, money and per-item resources");
        check(plugin.camps().active(ATT,DEF),"camp markers and SQLite ownership survive restart");stock.settle(capture,true);check(StockMath.count(data.town(ATT).storage(),new ItemStack(Material.STONE))==13,"resource replay remains idempotent after delivery");finish();
    }catch(Exception e){throw new CompletionException(e);}});}
    final class Actor {
        Location location=new Location(world,31.5,160,8.5);Inventory top=Bukkit.createInventory(null,9),bottom=Bukkit.createInventory(null,36);List<String> messages=new ArrayList<>();final Pig body=world.spawn(origin,Pig.class);final Player player;InventoryView view;
        Actor(){player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->ACTOR;case "getName"->"TntRaider";case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->location.clone();case "getScheduler"->body.getScheduler();case "isOnline","isOp","hasPermission"->true;case "isDead"->false;case "getGameMode"->GameMode.SURVIVAL;case "getOpenInventory"->view;case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,9);yield null;}case "sendMessage"->{messages.add(String.valueOf(a[a.length-1]));yield null;}case "hashCode"->ACTOR.hashCode();case "equals"->p==a[0];default->null;});
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(p,m,a)->switch(m.getName()){case "getTopInventory"->top;case "getBottomInventory"->bottom;case "getPlayer"->player;case "getType"->InventoryType.CHEST;default->null;});
        }
    }
}
