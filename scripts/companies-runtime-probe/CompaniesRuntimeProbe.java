package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townycompanies.*;
import ru.neverland.townycompanies.api.CompaniesApi;
import ru.neverland.mintcontracts.MintTownyContracts;
import ru.neverland.mintcontracts.model.*;
import ru.neverland.mintcontracts.service.ContractService;
import ru.neverland.townytreasury.api.TownyTreasuryApi;
import ru.neverland.townytreasury.service.TreasuryService;
import static ru.neverland.townycompanies.CompanyData.*;

/** Explicitly destructive fixtures; requires a disposable loopback server and a JVM opt-in. */
public final class CompaniesRuntimeProbe extends JavaPlugin {
    private static UUID id(String name){return UUID.nameUUIDFromBytes(("neverland-companies-runtime-0190:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private final UUID townId=id("town"),owner=id("owner"),company=id("company");
    private CompanyService service;private ContractService contracts;private TreasuryService treasury;
    private Path folder;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static void eq(long expected,long actual,String label){if(expected!=actual)throw new AssertionError(label+": expected "+expected+", got "+actual);}
    @Override public void onEnable(){
        if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_COMPANIES_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){getLogger().severe("Refusing to run outside an opted-in disposable loopback server");getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getScheduler().runTaskLater(this,this::run,100L);
    }
    private void run(){String phase="initialization";
        try {
            folder=getDataFolder().toPath();Files.createDirectories(folder);
            eq(26,Arrays.stream(getServer().getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count(),"all 26 add-ons enabled");
            check(TownyEconomyHandler.isActive(),"Towny/Vault/Essentials economy active");
            service=(CompanyService)getServer().getServicesManager().load(CompaniesApi.class);check(service!=null,"Companies API registered");
            contracts=((MintTownyContracts)getServer().getPluginManager().getPlugin("NeverLandTownyContracts")).companyContracts();
            treasury=(TreasuryService)getServer().getServicesManager().load(TownyTreasuryApi.class);check(treasury!=null&&!treasury.fault(),"Treasury ready");
            check(getServer().getPluginCommand("company")!=null,"company command registered");
            phase=Files.exists(folder.resolve("restart-ready"))?"restart":"first";
            if(phase.equals("first"))first();else restart();
            Files.writeString(folder.resolve(phase+"-passed.txt"),"PASS\n");getLogger().info("COMPANIES_RUNTIME_"+phase.toUpperCase(Locale.ROOT)+"_PASS");
        }catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"COMPANIES_RUNTIME_FAIL at "+phase,ex);try{Files.writeString(folder.resolve("failed.txt"),ex.toString());}catch(Exception ignored){}}
        finally{getServer().getScheduler().runTaskLater(this,()->getServer().shutdown(),20L);}
    }
    private Town createTown()throws Exception {
        var universe=TownyUniverse.getInstance();check(TownyAPI.getInstance().getTown(townId)==null,"fresh fixture city");universe.newTownInternal("CompanyProbe",townId);Town town=TownyAPI.getInstance().getTown(townId);
        var resident=universe.getDataSource().newResident("CompanyProbeMayor",owner);resident.setTown(town);town.setMayor(resident);
        var world=getServer().getWorlds().getFirst();var block=new TownBlock(40,40,universe.getWorld(world.getName()));universe.addTownBlock(block);block.setTown(town);town.setHomeBlock(block);block.save();town.setSpawn(new Location(world,40*Coord.getCellSize()+4,64,40*Coord.getCellSize()+4));resident.save();town.save();return town;
    }
    private long townBalance(){return Math.round(TownyAPI.getInstance().getTown(townId).getAccount().getHoldingBalance()*100);}
    private long personal(){return Math.round(TownyAPI.getInstance().getResident(owner).getAccount().getHoldingBalance()*100);}
    private void first()throws Exception {
        Town town=createTown();var resident=TownyAPI.getInstance().getResident(owner);
        check(town.getAccount().deposit(10_000,"Company runtime fixture"),"seed town");check(resident.getAccount().deposit(1000,"Company runtime fixture"),"seed resident");
        long townStart=townBalance(),personalStart=personal();getConfig().set("town-start",townStart);getConfig().set("personal-start",personalStart);saveConfig();treasury.reload(treasury.settings());
        long now=System.currentTimeMillis();service.ledger.put(new Company(company,townId,"Проверочная шахта",Kind.MINE,owner,Map.of(owner,Role.OWNER),Map.of(),null,0,0,0,now+86_400_000,0,false));
        var deposit=service.ledger.begin(company,owner,Purpose.DEPOSIT,20_000,now);service.ledger.process(deposit.id());eq(20_000,service.ledger.company(company).balance(),"actual personal deposit");eq(personalStart-20_000,personal(),"personal debit");
        Company c=service.ledger.company(company);service.ledger.put(c.funds(c.balance(),c.debt(),now-1));service.tick();
        eq(5000,service.ledger.company(company).balance(),"automatic company tax");eq(0,service.ledger.company(company).debt(),"tax debt paid");eq(townStart+15_000,townBalance(),"actual city received company tax");
        treasury.reload(treasury.settings());long taxes=treasury.treasury(townId).orElseThrow().weeks().values().stream().mapToLong(w->w.income().getOrDefault("tax",0L)).sum();eq(15_000,taxes,"Treasury reports company tax");
        check(!service.canTake(owner,company,townId),"offline actor cannot accept through API");check(!service.canContribute(owner,company,townId),"offline actor cannot spoof progress");
        var definition=contracts.registry().get("undead_hunt");check(contracts.activate(town,definition)==ContractService.ActivateResult.SUCCESS,"city reserved actual contract reward");
        ActiveContract contract=contracts.active(townId).getFirst();contract.companyId(company);contract.add(owner,30);contract.settlement(ContractStatus.EXPIRED,100_000,400_000);check(contracts.repository().save(),"durable settlement intent");
        Path data=((JavaPlugin)getServer().getPluginManager().getPlugin("NeverLandTownyContracts")).getDataFolder().toPath().resolve("contract-data.yml");byte[] beforeSettlement=Files.readAllBytes(data);
        check(contracts.cancel(town,contract),"company settlement accepted via optional reflection bridge");service.tick();
        eq(105_000,service.ledger.company(company).balance(),"company received partial contract reward");eq(townStart-85_000,townBalance(),"unearned escrow returned to city");check(contracts.active(townId).isEmpty(),"contract closed after escrow custody accepted");
        check(service.settleEscrow(contract.id(),company,townId,100_000,400_000),"exact settlement replay succeeds");eq(105_000,service.ledger.company(company).balance(),"replay has no money effect");
        // Restore only the pre-acknowledgement Contracts snapshot to emulate a crash between plugins.
        Files.write(data,beforeSettlement);contracts.repository().load();getConfig().set("contract",contract.id().toString());
        var field=CompanyService.class.getDeclaredField("repository");field.setAccessible(true);var repo=(CompanyRepository)field.get(service);
        CompanyLedger uncertain=new CompanyLedger(repo,p->{boolean result=new CompanyBank().transfer(p);if(result)throw new java.io.IOException("simulated acknowledgement loss after real bank credit");return result;});
        var withdrawal=uncertain.begin(company,owner,Purpose.WITHDRAW,5000,now);uncertain.process(withdrawal.id());check(repo.state().payments().get(withdrawal.id()).phase()==Phase.PENDING,"actual credit with lost acknowledgement held");
        eq(personalStart-15_000,personal(),"owner credited before acknowledgement loss");eq(100_000,service.ledger.company(company).balance(),"withdrawal reserve removed once");
        getConfig().set("payment",withdrawal.id().toString());saveConfig();Files.writeString(folder.resolve("restart-ready"),"restart\n");
    }
    private void restart()throws Exception {
        long townStart=getConfig().getLong("town-start"),personalStart=getConfig().getLong("personal-start");
        eq(townStart-85_000,townBalance(),"city tax/refund survived restart");eq(personalStart-15_000,personal(),"uncertain withdrawal not repeated at startup");eq(100_000,service.ledger.company(company).balance(),"company balance persisted");
        UUID contractId=UUID.fromString(getConfig().getString("contract"));ActiveContract c=contracts.find(townId,contractId.toString());
        if(c!=null)check(contracts.cancel(TownyAPI.getInstance().getTown(townId),c),"resume saved settlement");
        check(contracts.active(townId).isEmpty(),"duplicate settlement acknowledged");eq(100_000,service.ledger.company(company).balance(),"reward not replayed after crash");
        UUID payment=UUID.fromString(getConfig().getString("payment"));check(service.ledger.state().payments().get(payment).phase()==Phase.PENDING,"ambiguous bank transfer remains pending");
        getServer().dispatchCommand(getServer().getConsoleSender(),"company admin resolve "+payment+" applied confirm");check(service.ledger.state().payments().get(payment).phase()==Phase.DONE,"admin reconciliation command works");
        service.tick();eq(personalStart-15_000,personal(),"reconciliation never credits twice");eq(townStart+personalStart,townBalance()+personal()+service.ledger.company(company).balance(),"all money conserved across bank, company and contract escrow");
        treasury.reload(treasury.settings());check(!treasury.fault(),"Treasury remained healthy after restart");
    }
}
