package ru.neverland.runtime;

import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.object.economy.Account;
import com.palmergames.bukkit.towny.object.economy.adapter.EconomyAdapter;
import com.palmergames.bukkit.towny.object.economy.provider.EconomyProvider;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townybuilds.service.DefinitionRegistry;
import ru.neverland.townyupkeep.service.UpkeepService;
import ru.neverland.townyupkeep.api.TownyUpkeepApi;
import ru.neverland.townyupkeep.model.*;
import ru.neverland.townyupkeep.model.Entry.*;
import ru.neverland.townyupkeep.gui.UpkeepMenu;
import ru.neverland.townytreasury.service.TreasuryService;
import ru.neverland.townytreasury.api.TownyTreasuryApi;
import ru.neverland.townytreasury.gui.TreasuryMenu;

/** Genuine addon APIs/commands/inventories. Explicit registered-site, player and deterministic bank fixtures. */
public final class ForecastProbe extends TntRuntimeProbe {
    UpkeepService upkeep;TreasuryService treasury;JavaPlugin owner;DataStore data;Inventory top=Bukkit.createInventory(null,9);Player mayor,resident;long due;final Map<UUID,Double> balances=new HashMap<>();
    Player actor(UUID id,String name){return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
        case "getUniqueId"->id;case "getName"->name;case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp"->false;
        case "hasPermission"->Set.of("neverlandtownyupkeep.use","neverlandtownytreasury.use").contains(a[0]);case "openInventory"->{top=(Inventory)a[0];yield null;}
        case "getOpenInventory"->Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,n,b)->n.getName().equals("getTopInventory")?top:null);
        case "hashCode"->id.hashCode();case "equals"->p==a[0];default->null;});}
    void bank()throws Exception{
        var adapter=(EconomyAdapter)Proxy.newProxyInstance(EconomyAdapter.class.getClassLoader(),new Class<?>[]{EconomyAdapter.class},(p,m,a)->{
            Account account=a!=null&&a.length>0&&a[0] instanceof Account v?v:null;UUID id=account==null?null:account.getUUID();double old=balances.getOrDefault(id,0.0);
            return switch(m.getName()){case "name","toString"->"Forecast disposable bank fixture";case "hasAccount"->true;case "getBalance"->old;case "newAccount","deleteAccount"->null;case "getFormattedBalance"->String.valueOf(a[0]);case "setBalance"->{balances.put(id,(Double)a[1]);yield true;}case "renameAccount"->true;case "add","subtract"->{double n=(Double)a[1];boolean add=m.getName().equals("add");if(!add&&old<n)yield false;balances.put(id,old+(add?n:-n));yield true;}default->throw new UnsupportedOperationException(m.getName());};});
        // Readiness marker only: the sealed provider is allocated without Reserve initialization.
        // Account operations below use the explicit deterministic EconomyAdapter; no real Reserve/Vault test is claimed.
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        EconomyProvider provider=(EconomyProvider)unsafe.allocateInstance(Class.forName("com.palmergames.bukkit.towny.object.economy.provider.ReserveEconomyProvider",false,Towny.class.getClassLoader()));
        for(var e:Map.of("economy",adapter,"provider",provider).entrySet()){var f=TownyEconomyHandler.class.getDeclaredField(e.getKey());f.setAccessible(true);f.set(null,e.getValue());}
        TownySettings.setProperty("economy.using_economy",true);check(TownyEconomyHandler.isActive(),"native Towny economy readiness uses explicit deterministic provider fixture");
        TownyAPI.getInstance().getTown(DEF).getAccount().deposit(10000,"disposable forecast funding");treasury.reload(treasury.settings());
    }
    void command(Player p,String...args){owner.getCommand("townyupkeep").execute(p,"townyupkeep",args);}
    @Override void setup()throws Exception{
        owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyUpkeep");upkeep=(UpkeepService)Bukkit.getServicesManager().load(TownyUpkeepApi.class);treasury=(TreasuryService)Bukkit.getServicesManager().load(TownyTreasuryApi.class);var builds=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");data=(DataStore)field(builds,"dataStore");
        check(upkeep!=null&&treasury!=null&&owner.isEnabled(),"real Upkeep/Treasury services enabled with current Builds API");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("ForecastCity",DEF);var town=TownyAPI.getInstance().getTown(DEF);for(var e:Map.of(MAYOR,"ForecastMayor",ACTOR,"ForecastMember").entrySet()){var r=u.getDataSource().newResident(e.getValue(),e.getKey());r.setTown(town);if(e.getKey().equals(MAYOR))town.setMayor(r);r.save();}for(int x=0;x<2;x++){var c=new TownBlock(x,0,u.getWorld(world.getName()));u.addTownBlock(c);c.setTown(town);if(x==0)town.setHomeBlock(c);c.save();}town.setSpawn(origin);town.save();
            var site=new ConstructionSite("town_hall",world.getUID(),15,160,8,BlockFace.EAST,1,1,2,false,6);site.blueprintSet(BlueprintCatalog.currentPin("town_hall",BuildingStyle.MEDIEVAL));data.town(DEF).setConstructionSite(site);data.town(DEF).setLevel("town_hall",1);data.town(DEF).placement("forge",PlacementDraft.create(1));data.markDirty();data.saveOrThrow();}
        upkeep.reload(upkeep.settings());mayor=actor(MAYOR,"ForecastMayor");resident=actor(ACTOR,"ForecastMember");bank();Key key=new Key(DEF,"town_hall");due=upkeep.get(key).due();
        long price=ConstructionReceipt.cents(((DefinitionRegistry)field(builds,"definitions")).project("forge").level(1).money());var forecast=treasury.forecast(DEF,86400);
        if(restart){
            long original=Long.parseLong(Files.readString(proof.resolve("due.txt")));check(upkeep.paused(DEF,"town_hall")&&!upkeep.active(DEF,"town_hall")&&due==original,"manual stop and original paid deadline survive full restart");check(((Number)forecast.get("plannedCents")).longValue()==price,"paused upkeep creates no duplicate future obligation after restart");
            command(mayor,"resume","town_hall");check(!upkeep.paused(DEF,"town_hall")&&upkeep.get(key).due()==original&&upkeep.active(DEF,"town_hall"),"resume restores existing paid interval without new grace");
            var before=upkeep.get(key);upkeep.put(key,new Entry(false,due,"injected unknown bank receipt",new Invoice(UUID.randomUUID(),new Cost(1000,Map.of()),3600,Phase.MONEY_PENDING)));command(mayor,"pause","town_hall","confirm");check(!upkeep.paused(DEF,"town_hall")&&upkeep.get(key).invoice()!=null,"in-progress bank receipt cannot be hidden by suspension");check(Boolean.FALSE.equals(treasury.forecast(DEF,86400).get("complete")),"uncertain upkeep receipt makes full treasury forecast incomplete");upkeep.put(key,before);finish();return;
        }
        long bills=upkeep.plannedBills(DEF,86400).stream().mapToLong(r->((Number)r.get("cents")).longValue()).sum();check(bills>0&&((Number)forecast.get("plannedCents")).longValue()==price+bills,"forecast combines actual selected blueprint price and exact future billing periods");
        double bankBefore=TownyAPI.getInstance().getTown(DEF).getAccount().getHoldingBalance(true);for(int i=0;i<5;i++)treasury.forecast(DEF,86400);check(TownyAPI.getInstance().getTown(DEF).getAccount().getHoldingBalance(true)==bankBefore,"repeated planning never debits the real Towny account");
        var savings=upkeep.savings(DEF,Set.of("town_hall"),86400);check(((Number)savings.get("savingCents")).longValue()==bills,"savings and treasury share identical billing boundary");
        new UpkeepMenu(owner,upkeep).open(mayor,DEF,0,"pause/town_hall");check(top.getItem(31).getType()==Material.RED_DYE&&top.getItem(13).getItemMeta().getLore().stream().anyMatch(s->s.contains("рабочие места")),"native confirmation explains production/job consequences before stopping");
        command(resident,"pause","town_hall","confirm");check(!upkeep.paused(DEF,"town_hall"),"ordinary resident cannot suspend city benefits");command(mayor,"pause","town_hall","no");check(!upkeep.paused(DEF,"town_hall"),"command requires explicit confirmation");command(mayor,"pause","town_hall","confirm");
        check(upkeep.paused(DEF,"town_hall")&&!upkeep.active(DEF,"town_hall")&&!ru.neverland.integration.BuildingOperations.active(DEF,"town_hall"),"approved stop disables shared operational benefits immediately");
        check(upkeep.get(key).due()==due&&upkeep.get(key).invoice()==null,"stop neither refunds nor extends paid time nor creates a bill");check(((Number)treasury.forecast(DEF,86400).get("plannedCents")).longValue()==price,"treasury excludes future charges for suspended building");
        var treasuryOwner=Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus");new TreasuryMenu(treasuryOwner,treasury).open(mayor,DEF,"forecast/1");check(top.getItem(4).getType()==Material.CLOCK&&top.getItem(52).getType()==Material.CLOCK,"native forecast menu separates horizon, categories and completeness");
        SuiteState.freeze("NeverLandTownyUpkeep");check(Boolean.FALSE.equals(treasury.forecast(DEF,86400).get("complete")),"disabled provider is an explicit gap rather than zero complete costs");SuiteState.thaw("NeverLandTownyUpkeep");Files.writeString(proof.resolve("due.txt"),Long.toString(due));finish();
    }
    @Override void finish()throws Exception{TownySettings.setProperty("economy.using_economy",false);TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; native Folia/Towny addon APIs, future billing, suspensions, menus and confirmed permissions; explicit deterministic economy adapter/provider, registered-site and player fixtures; no real Vault or GUI client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
