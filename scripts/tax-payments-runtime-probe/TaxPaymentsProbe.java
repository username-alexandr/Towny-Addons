package ru.neverland.runtime;

import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import ru.neverland.core.*;
import ru.neverland.townytaxes.api.NeverLandTownyTaxesApi;
import ru.neverland.townytreasury.service.TreasuryService;
import ru.neverland.townytreasury.api.TownyTreasuryApi;
import ru.neverland.townytaxes.service.*;
import ru.neverland.townytaxes.model.*;

/** Real account observers, async tax DB, actual commands, restart and controlled unload. Explicit bank/player fixtures. */
public final class TaxPaymentsProbe extends ForecastProbe {
    FiscalService fiscal;JavaPlugin taxes;UUID normalPolicy,unknownPolicy,unknownReceipt;TaxPayments.Bank realBank;volatile boolean creditApplied;final UUID queuedReceipt=id("tax-pause-ready");Player player;List<String> output=new ArrayList<>();
    @Override void setup()throws Exception{
        taxes=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyTaxes");treasury=(TreasuryService)Bukkit.getServicesManager().load(TownyTreasuryApi.class);
        fiscal=(FiscalService)Bukkit.getServicesManager().load(NeverLandTownyTaxesApi.class);check(taxes.isEnabled()&&fiscal!=null,"actual Taxes 0.2 enabled");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("TaxPaymentCity",DEF);var town=TownyAPI.getInstance().getTown(DEF);var resident=u.getDataSource().newResident("TaxReceiptMayor",MAYOR);resident.setTown(town);town.setMayor(resident);resident.save();town.save();}
        if(restart){var properties=new Properties();try(var in=Files.newInputStream(proof.resolve("balances.properties"))){properties.load(in);}properties.forEach((k,v)->balances.put(UUID.fromString((String)k),Double.parseDouble((String)v)));}
        bank();if(restart){TownyAPI.getInstance().getTown(DEF).getAccount().withdraw(10000,"remove disposable repeated seed");treasury.reload(treasury.settings());}
        player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->MAYOR;case "getName"->"TaxReceiptMayor";case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "hasPermission"->a[0].equals("townytaxes.use");case "isOnline"->true;case "isOp"->false;case "sendMessage"->{if(a[0] instanceof String text)output.add(text);yield null;}case "hashCode"->MAYOR.hashCode();case "equals"->p==a[0];default->null;});
        waitFor(()->fiscal.coordinator()!=null&&fiscal.coordinator().ready(),()->{if(restart)resume();else first();},150);
    }
    void waitFor(BooleanSupplier condition,Work next,int left)throws Exception{if(condition.getAsBoolean()){next.run();return;}if(left<=0)throw new AssertionError("native tax wait timed out");later(1,()->waitFor(condition,next,left-1));}
    double personal(){return TownyAPI.getInstance().getResident(MAYOR).getAccount().getHoldingBalance(true);}double townMoney(){return TownyAPI.getInstance().getTown(DEF).getAccount().getHoldingBalance(true);}
    TaxPayment find(UUID policy){return fiscal.coordinator().store().snapshot().receipts().values().stream().filter(r->policy.equals(r.policy())).findFirst().orElse(null);}
    TaxPolicy policy(double price){return new TaxPolicy(UUID.randomUUID(),Domain.Scope.PLAYER,MAYOR,"TaxReceiptMayor",Domain.TaxType.FIXED,price,Domain.Scope.TOWN,DEF,"TaxPaymentCity",86400000,System.currentTimeMillis()-1,true,"native fixture");}
    void first()throws Exception{
        TownyAPI.getInstance().getResident(MAYOR).getAccount().deposit(500,"disposable personal funding");var policy=policy(12.345);normalPolicy=policy.id();fiscal.repository().put(policy);fiscal.repository().save();
        var quote=fiscal.taxPreview(MAYOR,10000,10000).iterator().next();check(quote.get("cents").equals(1235L),"public preview uses final penny rounding from frozen calculation");
        taxes.getCommand("mytaxes").execute(player,"mytaxes",new String[]{"preview","100","100"});check(output.stream().anyMatch(s->s.contains("12.35"))&&personal()==500,"real personal preview command explains amount without spending");
        fiscal.collectNow();waitFor(()->find(normalPolicy)!=null&&find(normalPolicy).phase()==TaxPayment.Phase.DONE,()->normalDone(),150);
    }
    void normalDone()throws Exception{
        check(Math.abs(personal()-487.65)<.00001&&Math.abs(townMoney()-10012.35)<.00001,"real Towny debit and credit equal public preview");var receipt=find(normalPolicy);check(receipt.calculation().cents()==1235&&receipt.calculation().describe().contains("12.35"),"durable receipt retains the same formula and amount");
        fiscal.collectNow();check(Math.abs(personal()+townMoney()-10500)<.00001,"repeat collection cannot duplicate completed fixed-tax period");taxes.getCommand("mytaxes").execute(player,"mytaxes",new String[]{"receipts"});check(output.stream().anyMatch(s->s.contains("Уплачено")),"actual player history command shows confirmed receipt");
        Object engine=field(fiscal.coordinator(),"engine");realBank=(TaxPayments.Bank)field(engine,"bank");set(engine,"bank",new TaxPayments.Bank(){public boolean ready(TaxPayment r,TaxPayments.Leg leg){return !r.id().equals(queuedReceipt)&&realBank.ready(r,leg);}public boolean apply(TaxPayment r,TaxPayments.Leg leg)throws Exception{boolean result=realBank.apply(r,leg);if(leg==TaxPayments.Leg.CREDIT&&result){creditApplied=true;throw new java.io.IOException("explicit lost acknowledgement after real native credit");}return result;}});
        var policy=policy(17.5);unknownPolicy=policy.id();fiscal.repository().put(policy);fiscal.repository().save();fiscal.collectNow();waitFor(()->find(unknownPolicy)!=null&&find(unknownPolicy).phase()==TaxPayment.Phase.CREDITING&&creditApplied,()->unknownDone(),150);
    }
    void unknownDone()throws Exception{
        unknownReceipt=find(unknownPolicy).id();check(Math.abs(personal()-470.15)<.00001&&Math.abs(townMoney()-10029.85)<.00001,"explicit acknowledgement loss occurs after actual bank credit without minting");
        fiscal.collectNow();fiscal.coordinator().kick(unknownReceipt);
        fiscal.coordinator().begin(new TaxPayment(queuedReceipt,id("pause-policy"),TaxPayment.Kind.TAX,Domain.Scope.PLAYER,MAYOR,"TaxReceiptMayor",Domain.Scope.TOWN,DEF,"TaxPaymentCity",TaxCalculation.calculate(7,1,1,1,0),TaxPayment.Phase.PREPARED,System.currentTimeMillis(),true,"explicit administrator-pause fixture"));
        later(8,()->{check(fiscal.coordinator().store().get(queuedReceipt).phase()==TaxPayment.Phase.PREPARED,"unattempted tax receipt waits without source withdrawal");check(Math.abs(personal()+townMoney()-10500)<.00001&&find(unknownPolicy).phase()==TaxPayment.Phase.CREDITING,"unknown credit is held instead of repeated or refunded");
            Files.writeString(proof.resolve("unknown.txt"),unknownReceipt.toString());var control=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"disable","Taxes"});
            waitFor(()->!taxes.isEnabled(),()->{check(Files.isRegularFile(taxes.getDataFolder().toPath().resolve("payments.db")),"controlled plugin disable drains SQLite before classloader closure");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"enable","Taxes"});
                check(!taxes.isEnabled(),"Folia records enable request for full restart instead of hot reload");finish();
            },150);
        });
    }
    void resume()throws Exception{
        unknownReceipt=UUID.fromString(Files.readString(proof.resolve("unknown.txt")));check(fiscal.coordinator().store().get(queuedReceipt).phase()==TaxPayment.Phase.VOID&&fiscal.taxDebt(MAYOR)==0,"administrator pause cancels unattempted receipt without debt after restart");var before=fiscal.coordinator().store().get(unknownReceipt);check(before.phase()==TaxPayment.Phase.CREDITING,"uncertain credit survives full Folia restart");
        fiscal.collectNow();later(8,()->{check(Math.abs(personal()-470.15)<.00001&&Math.abs(townMoney()-10029.85)<.00001,"restart and automatic pulse never repeat bank credit");
            taxes.getCommand("townytaxes").execute(Bukkit.getConsoleSender(),"townytaxes",new String[]{"resolve",unknownReceipt.toString(),"credit","applied","confirm","native fixture confirms receiver was credited"});
            waitFor(()->fiscal.coordinator().store().get(unknownReceipt).phase()==TaxPayment.Phase.DONE,()->{check(Math.abs(personal()+townMoney()-10500)<.00001,"real administrator reconciliation completes receipt without moving money again");
                var audit=new ArrayList<AuditRecord>();AuditJournal.scan(taxes.getDataFolder().toPath().resolve("audit"),audit::add);check(audit.stream().anyMatch(r->r.kind().equals("TAX_RECONCILE"))&&audit.stream().anyMatch(r->r.kind().equals("TAX_TRANSFER")&&r.operation().equals(unknownReceipt.toString())),"native admin journal links tax transfer and reconciliation evidence");finish();},150);
        });
    }
    @Override void finish()throws Exception{
        var properties=new Properties();balances.forEach((id,amount)->properties.setProperty(id.toString(),amount.toString()));try(var out=Files.newOutputStream(proof.resolve("balances.properties"))){properties.store(out,"Explicit deterministic bank fixture");}
        TownySettings.setProperty("economy.using_economy",false);TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; genuine Folia/Towny tax commands, account observers, SQLite, controlled unload and scheduled re-enable after restart, formula receipts and reconciliation; explicit deterministic provider/player fixtures and injected lost acknowledgement; no real Vault or GUI client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());
    }
}
