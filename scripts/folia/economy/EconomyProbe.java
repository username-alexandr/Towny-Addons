import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import ru.neverland.earnings.*;
import ru.neverland.core.*;

/** Native Towny/Vault and Folia checks in an isolated server; no production files are used. */
public final class EconomyProbe extends JavaPlugin {
    private final List<String> results=Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger remaining=new AtomicInteger(2);private int failed;
    private void check(String title,boolean value){results.add((value?"PASS ":"FAIL ")+title);if(!value)failed++;}
    @Override public void onEnable(){
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{synchronized(SuiteState.lock()){
            try{bankChecks();regions();}catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"Probe failed",ex);check(ex.toString(),false);finish();}
        }},60);
    }
    private void bankChecks()throws Exception{
        check("Towny economy active with Vault provider",TownyEconomyHandler.isActive());
        for(String module:List.of("Builds","Contracts","Jobs","Companies","Taxes","Control")){var p=Bukkit.getPluginManager().getPlugin("NeverLandTowny"+module);check("module enabled: "+module,p!=null&&p.isEnabled());}
        check("orders executor initialized",Bukkit.getPluginCommand("orders").getExecutor().getClass().getName().endsWith("PublicOrders"));
        check("salary executor initialized",Bukkit.getPluginCommand("jobpay").getExecutor().getClass().getName().endsWith("WageService"));
        var universe=TownyUniverse.getInstance();UUID id=UUID.randomUUID();var resident=universe.getDataSource().newResident("Probe"+id.toString().substring(0,8),id);
        check("public-order recipient has no town",!resident.hasTown());
        var ledger=new RewardLedger(getDataFolder().toPath().resolve("native-payments"),150000,200000);ledger.load();var bank=new TownyRewardBank(this);for(String line:bank.diagnostics())results.add("DIAGNOSTIC "+line);
        double before=resident.getAccount().getHoldingBalance();UUID payment=UUID.randomUUID();ledger.reserve(payment,id,12000,"orders:native-townless",false);ledger.submit(payment,bank);
        check("Towny/Vault personal balance increased by 120",Math.abs(resident.getAccount().getHoldingBalance()-before-120)<0.0001);
        ledger.submit(payment,bank);check("duplicate submission does not mint twice",Math.abs(resident.getAccount().getHoldingBalance()-before-120)<0.0001);
        var reload=new RewardLedger(getDataFolder().toPath().resolve("native-payments"),150000,200000);reload.load();reload.submit(payment,bank);check("reloaded PAID does not mint twice",Math.abs(resident.getAccount().getHoldingBalance()-before-120)<0.0001);
        universe.newTown("ProbeCity"+id.toString().substring(0,6));var town=universe.getTown("ProbeCity"+id.toString().substring(0,6));resident.setTown(town);town.setMayor(resident);
        double treasury=town.getAccount().getHoldingBalance();check("resident transfers income to town treasury",resident.getAccount().payTo(100,town,"native earned-income contribution"));
        check("town received 100, resident retained 20",Math.abs(town.getAccount().getHoldingBalance()-treasury-100)<0.0001&&Math.abs(resident.getAccount().getHoldingBalance()-before-20)<0.0001);
        // Existing city escrow must move existing money, not invoke the server reward ledger.
        check("city contract reservation spends treasury",town.getAccount().withdraw(30,"native city escrow"));
        check("city escrow amount conserved",Math.abs(town.getAccount().getHoldingBalance()-treasury-70)<0.0001);
        var fixture=Bukkit.getPluginManager().getPlugin("EconomyFixture");var health=fixture.getClass().getField("healthy");
        UUID pending=UUID.randomUUID();ledger.reserve(pending,id,100,"jobs:provider-outage",false);double saved=resident.getAccount().getHoldingBalance();
        health.setBoolean(fixture,false);check("disabled provider pauses new bank calls",!bank.ready(id));ledger.submit(pending,bank);check("outage retains pending obligation",ledger.get(pending).state()==RewardLedger.State.PENDING);
        health.setBoolean(fixture,true);ledger.submit(pending,bank);check("provider recovery pays pending once",ledger.get(pending).state()==RewardLedger.State.PAID&&Math.abs(resident.getAccount().getHoldingBalance()-saved-1)<0.0001);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"orders admin status");Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"jobpay admin status");
    }
    private void regions(){World world=Bukkit.getWorlds().getFirst();for(int x:new int[]{0,10000}){
        Location at=new Location(world,x,80,0);world.getChunkAtAsync(at).thenAccept(chunk->Bukkit.getRegionScheduler().run(this,at,t->{synchronized(SuiteState.lock()){
            try{check("region owns location "+x,Bukkit.isOwnedByCurrentRegion(at));var original=at.getBlock().getBlockData();at.getBlock().setType(Material.IRON_ORE,false);
                var guard=new ActionGuard(this,"native-blocks.log");check("fresh ore eligible "+x,guard.untouched(at.getBlock()));guard.deny(at.getBlock());check("placed/mined coordinate rejected "+x,!guard.untouched(at.getBlock()));var reload=new ActionGuard(this,"native-blocks.log");check("block provenance survives reload "+x,!reload.untouched(at.getBlock()));at.getBlock().setBlockData(original,false);
                ArmorStand entity=world.spawn(at,ArmorStand.class);SuiteScheduler.entity(this,entity,()->{check("entity scheduler owns entity "+x,Bukkit.isOwnedByCurrentRegion(entity));entity.remove();if(remaining.decrementAndGet()==0)finish();});
            }catch(Throwable ex){check(ex.toString(),false);if(remaining.decrementAndGet()==0)finish();}
        }}));}}
    private void finish(){Bukkit.getGlobalRegionScheduler().run(this,t->{try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("result.txt"),String.join("\n",results)+"\nfailed="+failed+"\n");}catch(Exception ex){throw new RuntimeException(ex);}});}
}
