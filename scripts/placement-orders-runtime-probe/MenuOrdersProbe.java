package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.craftbukkit.*;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.*;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.object.economy.Account;
import com.palmergames.bukkit.towny.object.economy.adapter.EconomyAdapter;
import ru.neverland.core.*;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townybuilds.service.*;
import ru.neverland.mintcontracts.orders.*;
import ru.neverland.townysieges.*;

/** Opt-in native boundary checks. Actors are real CraftPlayer handles without network logins. */
public final class MenuOrdersProbe extends TntRuntimeProbe {
    org.bukkit.plugin.java.JavaPlugin builds;DataStore data;Actor mayor,guest,soldier;
    final Map<UUID,Double> balances=new HashMap<>();
    static final UUID GUEST=id("guest-051"),ORDER=id("order-051");
    @Override void setup()throws Exception{
        plugin=(NeverLandTownySiegesPlus)Bukkit.getPluginManager().getPlugin("NeverLandTownySiegesPlus");
        builds=(org.bukkit.plugin.java.JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");
        data=(DataStore)field(builds,"dataStore");bank();
        var universe=TownyUniverse.getInstance();
        if(!restart){
            universe.newTownInternal("ProtectedCity",DEF);universe.newTownInternal("ArmyCity",ATT);
            defender=TownyAPI.getInstance().getTown(DEF);attacker=TownyAPI.getInstance().getTown(ATT);
            for(var pair:Map.of(MAYOR,DEF,ACTOR,ATT).entrySet()){
                var resident=universe.getDataSource().newResident(pair.getKey().equals(MAYOR)?"ProtectedMayor":"ArmyMayor",pair.getKey());
                var town=TownyAPI.getInstance().getTown(pair.getValue());resident.setTown(town);town.setMayor(resident);town.setRegistered(System.currentTimeMillis()-172800000);resident.save();town.save();
            }
            universe.getDataSource().newResident("OrderGuest",GUEST).save();
            for(int x=-2;x<=3;x++)for(int z=-2;z<=2;z++){
                var tb=new TownBlock(x,z,universe.getWorld(world.getName()));universe.addTownBlock(tb);tb.setTown(defender);tb.save();if(x==0&&z==0)defender.setHomeBlock(tb);
            }
            defender.setSpawn(origin);defender.save();
        }else{defender=TownyAPI.getInstance().getTown(DEF);attacker=TownyAPI.getInstance().getTown(ATT);}
        mayor=new Actor(MAYOR,"ProtectedMayor");guest=new Actor(GUEST,"OrderGuest");soldier=new Actor(ACTOR,"ArmyMayor");
        check(Bukkit.isOwnedByCurrentRegion(mayor),"native actor runs on owning Folia region");
        var ordersPlugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyContracts");
        var orders=(PublicOrders)field(ordersPlugin,"publicOrders");check(orders!=null,"public orders initialized with personal boards");
        if(restart){
            check(data.town(DEF).armyEstablished()&&data.town(DEF).level("army")==0,"army history persists after demolition and full restart");
            call(plugin.raids(),"relations",new Class<?>[]{Town.class,Town.class},attacker,defender);check(true,"established cities stay eligible after restart");
            check(terms(offers(orders.uiSnapshot(mayor))).equals(Files.readString(proof.resolve("mayor-offers.txt"))),"mayor personal offers survive native restart");
            check(terms(offers(orders.uiSnapshot(guest))).equals(Files.readString(proof.resolve("guest-offers.txt"))),"townless player's offers survive native restart");
            var saved=Files.readString(proof.resolve("accepted.txt"));
            var mine=orderState(orders);check(mine.values().stream().anyMatch(o->stable(o).equals(saved)),"accepted terms, rarity and progress survive restart");
            finish();return;
        }
        protection();orders(orders);placement();labels();data.markDirty();data.saveOrThrow();finish();
    }
    void denied(Work action,String contains,String message)throws Exception{
        try{action.run();}catch(Exception ex){Throwable root=ex;while(root.getCause()!=null)root=root.getCause();check(root.getMessage()!=null&&root.getMessage().contains(contains),message);return;}throw new AssertionError(message);
    }
    void protection()throws Exception{
        data.town(ATT).setLevel("army",1);
        denied(()->plugin.raids().declare(soldier,defender.getName()),"защищён","public raid declaration denies town without army");
        data.town(DEF).setConstructionSite(new ConstructionSite("army",world.getUID(),0,160,0,BlockFace.NORTH,0,1,1,true));
        denied(()->call(plugin.raids(),"relations",new Class<?>[]{Town.class,Town.class},attacker,defender),"защищён","unfinished army still protects newcomer");
        data.town(DEF).removeConstructionSite("army");data.town(DEF).setLevel("army",1);
        call(plugin.raids(),"relations",new Class<?>[]{Town.class,Town.class},attacker,defender);check(true,"completed army permits relation gate");
        data.town(DEF).setLevel("army",0);
        call(plugin.raids(),"relations",new Class<?>[]{Town.class,Town.class},attacker,defender);check(true,"demolishing army cannot restore protection");
        defender.setRegistered(System.currentTimeMillis());
        denied(()->call(plugin.raids(),"relations",new Class<?>[]{Town.class,Town.class},attacker,defender),"щит новичка","24h shield remains after army completion");
        defender.setRegistered(System.currentTimeMillis()-172800000);defender.save();
        var api=Bukkit.getServicesManager().load(TownyBuildsApi.class);check(api.capabilities().contains("armyEstablished")&&api.armyEstablished(DEF),"published Builds history capability available");
    }
    static String terms(List<Map<String,Object>> offers){return offers.stream().map(row->{var copy=new LinkedHashMap<>(row);copy.remove("reason");return copy;}).toList().toString();}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> offers(Map<String,Object> snapshot){return (List<Map<String,Object>>)snapshot.get("offers");}
    @SuppressWarnings("unchecked") Map<UUID,PublicOrders.Order> orderState(PublicOrders service)throws Exception{return (Map<UUID,PublicOrders.Order>)field(service,"orders");}
    String stable(PublicOrders.Order o){return o.id()+"/"+o.player()+"/"+o.template()+"/"+o.goal()+"/"+o.progress()+"/"+o.cents()+"/"+o.rarity()+"/"+o.boardDay();}
    void orders(PublicOrders service)throws Exception{
        var a=offers(service.uiSnapshot(mayor));var b=offers(service.uiSnapshot(guest));
        check(a.size()==18&&b.size()==18&&!a.equals(b),"native UI exposes separate personal random boards");
        check(!TownyAPI.getInstance().getResident(GUEST).hasTown(),"second order actor has no town");
        check(offers(service.uiSnapshot(mayor)).equals(a),"opening another player's board does not change mayor offers");
        var ids=a.stream().map(r->(String)r.get("id")).toList();
        check(service.onTabComplete(mayor,null,"orders",new String[]{"accept",""}).equals(ids),"command suggestions use same personal board");
        var foreign=b.stream().filter(r->!ids.contains(r.get("id"))).findFirst().orElseThrow();
        denied(()->service.uiExecute(mayor,"accept",foreign.get("id").toString(),foreign.get("quote").toString()),"Условия заказа","foreign board quote cannot accept another player's offer");
        var offer=b.stream().filter(r->r.get("type").equals("HUNTING")).findFirst().orElseThrow();
        service.uiExecute(guest,"accept",offer.get("id").toString(),offer.get("quote").toString());
        check(orderState(service).values().stream().filter(o->o.player().equals(GUEST)).count()==1,"townless player accepts personal order");
        call(service,"progress",new Class<?>[]{Player.class,PublicOrders.Type.class,String.class},guest,PublicOrders.Type.HUNTING,offer.get("target").toString());
        call(service,"save",new Class<?>[]{});
        var accepted=orderState(service).values().stream().filter(o->o.player().equals(GUEST)).findFirst().orElseThrow();
        check(accepted.progress()==1,"personal accepted hunting objective advances once");
        denied(()->service.uiExecute(guest,"accept",offer.get("id").toString(),offer.get("quote").toString()),"принят","repeat acceptance rejected");
        Files.writeString(proof.resolve("accepted.txt"),stable(accepted));
        Files.writeString(proof.resolve("mayor-offers.txt"),terms(a));Files.writeString(proof.resolve("guest-offers.txt"),terms(b));
    }
    void placement()throws Exception{
        var service=(PlacementService)field(builds,"placements");var registry=(DefinitionRegistry)field(builds,"definitions");var project=registry.project("town_hall");
        for(var item:project.level(1).resources())data.town(DEF).resourceFund("town_hall",1).add(item,item.getAmount());
        service.issue(mayor,defender,project,1);ItemStack blueprint=mayor.getInventory().getStorageContents()[0];check(blueprint!=null,"native blueprint issued after resource funding");
        call(service,"use",new Class<?>[]{Player.class,ItemStack.class,Location.class},mayor,blueprint,new Location(world,8,159,8));
        var sessions=(Map<?,?>)field(service,"sessions");Object first=sessions.get(MAYOR);check(first!=null,"native blueprint creates placement session");
        Object choice=field(first,"choice");check(!service.nudge(mayor,UUID.randomUUID().toString(),1,"up")&&field(sessions.get(MAYOR),"choice").equals(choice),"legacy direct movement API cannot move placement");
        mayor.sneaking=true;call(service,"use",new Class<?>[]{Player.class,ItemStack.class,Location.class},mayor,blueprint,new Location(world,8,159,8));
        check(mayor.top.getHolder(false) instanceof PlacementService.PlacementMenu&&field(sessions.get(MAYOR),"choice").equals(choice),"Shift-right-click opens menu without moving or rotating");
        var before=(PlacementChoice)choice;
        call(service,"move",new Class<?>[]{Player.class,first.getClass(),int.class,int.class,int.class},mayor,first,1,0,0);
        var after=(PlacementChoice)field(sessions.get(MAYOR),"choice");check(after.x()==before.x()+1&&after.y()==before.y(),"movement menu path changes selected location by one block");
        check(data.town(DEF).constructionSite("town_hall")==null&&data.town(DEF).existingResourceFund("town_hall")!=null,"moving preview does not start construction or consume resources");
        mayor.sneaking=false;service.stop();
    }
    void labels()throws Exception{
        Object service=field(builds,"construction");Class<?> target=Class.forName("ru.neverland.townybuilds.construction.ConstructionService$PreviewTarget",true,builds.getClass().getClassLoader());var ctor=target.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var value=ctor.newInstance(origin,new BlueprintBlock(Material.STONE,BlockRole.RESIDENT,1),1.0,new ConstructionSite("town_hall",world.getUID(),15,160,8,BlockFace.NORTH,0,1,1,true),new ConstructionLayers.Layer(0,1,3,0,1,List.of()),"Камень × 1");
        builds.getConfig().set("settings.construction.guide-label-enabled",true);builds.getConfig().set("settings.construction.block-label.enabled",null);
        call(service,"renderGuideDisplay",new Class<?>[]{Player.class,target},mayor,value);
        check(((Map<?,?>)field(service,"guideDisplays")).isEmpty(),"old enabled label config cannot create large text display");
        builds.getConfig().set("settings.construction.block-label.enabled",true);call(service,"renderGuideDisplay",new Class<?>[]{Player.class,target},mayor,value);
        var label=(TextDisplay)((Map<?,?>)field(service,"guideDisplays")).get(MAYOR);
        Files.writeString(proof.resolve("label-debug.txt"),"owns="+WorldTasks.ownsBox(origin,2)+"; label="+label+"; valid="+(label!=null&&label.isValid())+"; scale="+(label==null?"null":label.getTransformation().getScale().toString()));
        check(label!=null&&Math.abs(label.getTransformation().getScale().x()-.35)<.001,"explicit opt-in uses small 0.35 text label");
        builds.getConfig().set("settings.construction.block-label.enabled",false);call(service,"renderGuideDisplay",new Class<?>[]{Player.class,target},mayor,value);
        check(((Map<?,?>)field(service,"guideDisplays")).isEmpty(),"turning label off removes it");
    }
    @Override void finish()throws Exception{
        data.markDirty();data.saveOrThrow();TownyUniverse.getInstance().getDataSource().saveAll();
        Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; native Folia 26.2/Towny, personal boards, placement and protection; explicit offline actor/economy fixtures; no authenticated client\n");
        Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());
    }
 void bank()throws Exception{
  Path file=proof.resolve("bank.properties");if(Files.exists(file)){var p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}p.forEach((k,v)->balances.put(UUID.fromString((String)k),Double.valueOf((String)v)));}
  var adapter=(EconomyAdapter)Proxy.newProxyInstance(EconomyAdapter.class.getClassLoader(),new Class<?>[]{EconomyAdapter.class},(proxy,m,args)->{Account account=args!=null&&args.length>0&&args[0] instanceof Account a?a:null;UUID id=account==null?null:account.getUUID();double old=balances.getOrDefault(id,0.);return switch(m.getName()){case "name","toString"->"Native deterministic test adapter";case "hasAccount"->true;case "getBalance"->old;case "newAccount","deleteAccount"->null;case "getFormattedBalance"->String.valueOf(args[0]);case "setBalance"->{balances.put(id,(Double)args[1]);yield true;}case "renameAccount"->true;case "add","subtract"->{double n=(Double)args[1];boolean incoming=m.getName().equals("add");if(!incoming&&old<n)yield false;balances.put(id,old+(incoming?n:-n));yield true;}default->throw new UnsupportedOperationException(m.getName());};});
  var field=TownyEconomyHandler.class.getDeclaredField("economy");field.setAccessible(true);field.set(null,adapter);
  // Only economyType() is used on this uninitialized provider wrapper; all transfers use the explicit adapter above.
  Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");var uf=unsafeClass.getDeclaredField("theUnsafe");uf.setAccessible(true);Object unsafe=uf.get(null);Object provider=unsafeClass.getMethod("allocateInstance",Class.class).invoke(unsafe,Class.forName("com.palmergames.bukkit.towny.object.economy.provider.VaultEconomyProvider"));field=TownyEconomyHandler.class.getDeclaredField("provider");field.setAccessible(true);field.set(null,provider);
  field=TownySettings.class.getDeclaredField("config");field.setAccessible(true);((org.bukkit.configuration.ConfigurationSection)field.get(null)).set("economy.using_economy",true);
 }
 final class Actor extends CraftPlayer {
  boolean sneaking;@Override public boolean isSneaking(){return sneaking;}
  @Override public void sendMessage(net.kyori.adventure.text.Component message){}
  @Override public void sendActionBar(net.kyori.adventure.text.Component message){}
  final Pig body;Inventory top=Bukkit.createInventory(null,9);final InventoryView view;final List<String> messages=new ArrayList<>();final List<String> commands=new ArrayList<>();
  Actor(UUID id,String name)throws Exception{super((CraftServer)Bukkit.getServer(),new ServerPlayer(((CraftServer)Bukkit.getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(id,name),ClientInformation.createDefault()));var field=net.minecraft.world.entity.Entity.class.getDeclaredField("bukkitEntity");field.setAccessible(true);field.set(getHandleRaw(),this);getHandleRaw().setPosRaw(origin.getX(),origin.getY(),origin.getZ());var region=io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();field=region.getClass().getDeclaredField("allEntities");field.setAccessible(true);((ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity>)field.get(region)).add(getHandleRaw());body=world.spawn(origin,Pig.class);body.setAI(false);body.setGravity(false);view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(p,m,a)->switch(m.getName()){case "getTopInventory"->top;case "getBottomInventory"->getInventory();case "getPlayer"->this;case "getType"->org.bukkit.event.inventory.InventoryType.CHEST;default->null;});}
  @Override public boolean isOnline(){return true;}@Override public boolean hasPermission(String permission){return true;}@Override public boolean isOp(){return true;}@Override public GameMode getGameMode(){return GameMode.SURVIVAL;}@Override public InventoryView getOpenInventory(){return view;}@Override public InventoryView openInventory(Inventory inventory){top=inventory;return view;}@Override public void closeInventory(){top=Bukkit.createInventory(null,9);}@Override public void sendMessage(String s){messages.add(s);}@Override public void sendMessage(String... s){messages.addAll(List.of(s));}@Override public boolean performCommand(String command){commands.add(command);return true;}
 }
}
