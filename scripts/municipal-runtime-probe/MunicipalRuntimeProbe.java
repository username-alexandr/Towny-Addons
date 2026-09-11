package ru.neverland.runtime;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.mintcontracts.MintTownyContracts;
import ru.neverland.mintcontracts.model.*;
import ru.neverland.mintcontracts.service.*;
import ru.neverland.mintcontracts.integration.MunicipalBank;
import ru.neverland.townybuilds.api.MunicipalStorageApi;
import ru.neverland.townybuilds.NeverLandTownyBuilds;
import ru.neverland.townytreasury.api.TownyTreasuryApi;
import ru.neverland.townytreasury.service.TreasuryService;

/** Fixtures require an explicit marker, loopback and JVM opt-in. No connected client is emulated. */
public final class MunicipalRuntimeProbe extends JavaPlugin {
    private static UUID id(String s){return UUID.nameUUIDFromBytes(("neverland-municipal-0200:"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private final UUID townId=id("town"),owner=id("owner");private ContractService contracts;private MunicipalStorageApi warehouse;private TreasuryService treasury;private Path folder;
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}private static void eq(long expected,long actual,String s){if(expected!=actual)throw new AssertionError(s+": expected "+expected+", got "+actual);}
    @Override public void onEnable(){if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_MUNICIPAL_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){getServer().getPluginManager().disablePlugin(this);return;}getServer().getScheduler().runTaskLater(this,this::run,100);}
    private void run(){String phase="initialization";try{
        folder=getDataFolder().toPath();Files.createDirectories(folder);eq(26,Arrays.stream(getServer().getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count(),"all 26 addons enabled");check(TownyEconomyHandler.isActive(),"real Vault economy active");
        contracts=((MintTownyContracts)getServer().getPluginManager().getPlugin("NeverLandTownyContracts")).companyContracts();warehouse=getServer().getServicesManager().load(MunicipalStorageApi.class);treasury=(TreasuryService)getServer().getServicesManager().load(TownyTreasuryApi.class);check(warehouse!=null&&treasury!=null&&!treasury.fault(),"warehouse and treasury services ready");
        phase=Files.exists(folder.resolve("restart-ready"))?"restart":"first";if(phase.equals("first"))first();else restart();Files.writeString(folder.resolve(phase+"-passed.txt"),"PASS\n");getLogger().info("MUNICIPAL_RUNTIME_"+phase.toUpperCase(Locale.ROOT)+"_PASS");
    }catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"MUNICIPAL_RUNTIME_FAIL at "+phase,ex);try{Files.writeString(folder.resolve("failed.txt"),ex.toString());}catch(Exception ignored){}}finally{getServer().getScheduler().runTaskLater(this,()->getServer().shutdown(),20);}}
    private Town createTown()throws Exception{var u=TownyUniverse.getInstance();u.newTownInternal("MunicipalProbe",townId);Town t=TownyAPI.getInstance().getTown(townId);var r=u.getDataSource().newResident("MunicipalMayor",owner);r.setTown(t);t.setMayor(r);World w=getServer().getWorlds().getFirst();var b=new TownBlock(40,40,u.getWorld(w.getName()));u.addTownBlock(b);b.setTown(t);t.setHomeBlock(b);b.save();t.setSpawn(new Location(w,40*Coord.getCellSize()+4,80,40*Coord.getCellSize()+4));r.save();t.save();return t;}
    private long town(){return Math.round(TownyAPI.getInstance().getTown(townId).getAccount().getHoldingBalance()*100);}private long personal(){return Math.round(TownyAPI.getInstance().getResident(owner).getAccount().getHoldingBalance()*100);}
    private ContractDefinition definition(String key,ContractType type,int goal,double reward,ItemStack item){return new ContractDefinition(key,"Проверка: "+key,type,type==ContractType.ROAD?Material.STONE_BRICKS:Material.PAPER,0,List.of(),type==ContractType.DELIVERY?"STONE":type==ContractType.MOB_KILL?"ZOMBIE":type==ContractType.ROAD?"STONE_BRICKS":"AREA",item,goal,reward,86400);}
    private ActiveContract activate(Town town,ContractDefinition d,WorkArea area){check(contracts.activate(town,d,area)==ContractService.ActivateResult.SUCCESS,"activate "+d.id());return contracts.active(townId).stream().filter(c->c.templateId().equals(d.id())).findFirst().orElseThrow();}
    private int stone()throws Exception{var builds=(NeverLandTownyBuilds)getServer().getPluginManager().getPlugin("NeverLandTownyBuilds");var field=NeverLandTownyBuilds.class.getDeclaredField("dataStore");field.setAccessible(true);var data=(ru.neverland.townybuilds.data.DataStore)field.get(builds);return Arrays.stream(data.town(townId).storage()).filter(Objects::nonNull).filter(i->i.getType()==Material.STONE).mapToInt(ItemStack::getAmount).sum();}
    private void first()throws Exception{
        Town t=createTown();check(t.getAccount().deposit(10000,"Municipal probe seed"),"seed city");check(TownyAPI.getInstance().getResident(owner).getAccount().deposit(1000,"Municipal probe seed"),"seed resident");getConfig().set("town-start",town());getConfig().set("personal-start",personal());saveConfig();treasury.reload(treasury.settings());
        // Complete a hunt with a lost bank acknowledgement; real money must not be sent twice.
        ActiveContract hunt=activate(t,definition("probe_hunt",ContractType.MOB_KILL,3,100,null),null);
        var field=ContractService.class.getDeclaredField("payments");field.setAccessible(true);var original=(MunicipalPayments)field.get(contracts);var sf=MunicipalPayments.class.getDeclaredField("store");sf.setAccessible(true);var store=(MunicipalPayments.Store)sf.get(original);
        field.set(contracts,new MunicipalPayments(store,p->{boolean result=new MunicipalBank().transfer(p);if(result)throw new java.io.IOException("simulated lost acknowledgement after real transfer");return result;}));
        check(contracts.addProgress(hunt.id(),owner,3,"municipal-runtime-probe"),"trusted hunt progress");field.set(contracts,original);var pending=contracts.repository().payments().values().stream().filter(p->p.kind()==MunicipalPayment.Kind.REWARD&&p.contract().equals(hunt.id())).findFirst().orElseThrow();check(pending.phase()==MunicipalPayment.Phase.PENDING,"uncertain real reward held");getConfig().set("payment",pending.id().toString());
        // Native road blocks: required paving alone cannot complete a broken pre-existing cell.
        World w=getServer().getWorlds().getFirst();int x=40*Coord.getCellSize()+2,z=x,y=80;for(int i=0;i<3;i++){w.getBlockAt(x+i,y-1,z).setType(Material.STONE,false);w.getBlockAt(x+i,y,z).setType(Material.AIR,false);w.getBlockAt(x+i,y+1,z).setType(Material.AIR,false);w.getBlockAt(x+i,y+2,z).setType(Material.AIR,false);}
        WorkArea roadArea=new WorkArea(w.getUID(),x,y,z,x+2,z,Set.of(WorkArea.key(x,z),WorkArea.key(x+1,z)));ActiveContract road=activate(t,definition("probe_road",ContractType.ROAD,2,200,null),roadArea);
        for(int i=0;i<2;i++){w.getBlockAt(x+i,y,z).setType(Material.STONE_BRICKS,false);road.proof(WorkArea.key(x+i,z),new WorkProof(owner,System.currentTimeMillis()-120000));}contracts.saveField();contracts.fieldWork().pulse();check(contracts.find(townId,road.id().toString())!=null&&road.progress()==2,"broken existing road cell blocks payout even with full numeric progress");
        w.getBlockAt(x+2,y,z).setType(Material.STONE_BRICKS,false);contracts.fieldWork().pulse();check(contracts.find(townId,road.id().toString())==null,"complete supported walkable road pays");
        // Partial road must return the entire reserve.
        ActiveContract partial=activate(t,definition("probe_partial_road",ContractType.ROAD,2,50,null),roadArea);partial.proof(WorkArea.key(x,z),new WorkProof(owner,System.currentTimeMillis()));partial.replaceWorkProgress(Map.of(owner,1));long before=personal();check(contracts.cancel(t,partial),"cancel partial road");eq(before,personal(),"partial road receives no pay");
        // A delivery survives a crash between warehouse persistence and contract progress.
        ItemStack sample=new ItemStack(Material.STONE);ActiveContract delivery=activate(t,definition("probe_delivery",ContractType.DELIVERY,1000,1000,sample),null);var intent=new DeliveryIntent(id("delivery"),delivery.id(),townId,owner,ContractCodec.item(sample),1000,DeliveryIntent.Phase.TAKEN,System.currentTimeMillis(),false);contracts.repository().delivery(intent);contracts.repository().saveOrThrow();check(warehouse.deposit(townId,intent.id(),sample,1000).equals("DELIVERED"),"actual 1000 stone deposit");check(warehouse.deposit(townId,intent.id(),sample,1000).equals("DELIVERED"),"exact receipt replay");eq(1000,stone(),"physical warehouse holds stone once");eq(0,delivery.progress(),"contract acknowledgement deliberately absent");getConfig().set("delivery",delivery.id().toString());
        WorkArea scouting=new WorkArea(w.getUID(),40,0,40,41,40,Set.of("40:40","41:40"));ActiveContract scout=activate(t,definition("probe_scout",ContractType.SCOUT,2,80,null),scouting);scout.proof("40:40",new WorkProof(owner,System.currentTimeMillis()));scout.add(owner,1);contracts.saveField();check(!contracts.addProgress(scout.id(),owner,1,"municipal-runtime-probe"),"generic API cannot forge scout visits");getConfig().set("scout",scout.id().toString());
        saveConfig();Files.writeString(folder.resolve("restart-ready"),"restart\n");
    }
    private void restart()throws Exception{
        eq(1000,stone(),"warehouse and receipt survived restart");var delivery=contracts.find(townId,getConfig().getString("delivery"));check(delivery!=null&&delivery.progress()==0,"unacknowledged delivery restored");
        for(int i=0;i<35;i++)contracts.tick();check(contracts.find(townId,getConfig().getString("delivery"))==null,"delivery resumed and paid");eq(1000,stone(),"resume never duplicated physical stock");
        var scout=contracts.find(townId,getConfig().getString("scout"));check(scout!=null&&scout.proofs().size()==1&&scout.progress()==1&&scout.snapshot().type()==ContractType.SCOUT,"scout terms and unique visit persisted");check(contracts.cancel(TownyAPI.getInstance().getTown(townId),scout),"partial scout settlement");
        UUID payment=UUID.fromString(getConfig().getString("payment"));check(contracts.repository().payments().get(payment).phase()==MunicipalPayment.Phase.PENDING,"unknown bank credit never retried on startup");long before=personal();getServer().dispatchCommand(getServer().getConsoleSender(),"townycontracts resolve "+payment+" applied confirm");eq(before,personal(),"admin reconciliation does not duplicate payment");check(contracts.repository().payments().get(payment).phase()==MunicipalPayment.Phase.DONE,"admin command persisted reconciliation");
        eq(getConfig().getLong("personal-start")+134000,personal(),"hunt, road, stone and partial scout rewards");eq(getConfig().getLong("town-start")-134000,town(),"city reserved, paid and refunded exact cents");eq(getConfig().getLong("town-start")+getConfig().getLong("personal-start"),town()+personal(),"all money conserved");check(contracts.active(townId).isEmpty(),"all municipal orders settled");treasury.reload(treasury.settings());check(!treasury.fault(),"Treasury remains healthy");
    }
}
