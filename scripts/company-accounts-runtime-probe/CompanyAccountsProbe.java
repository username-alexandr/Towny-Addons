package ru.neverland.runtime;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import io.papermc.paper.threadedregions.scheduler.*;
import ru.neverland.core.*;
import ru.neverland.townycompanies.*;
import ru.neverland.townycompanies.api.*;
import ru.neverland.mintcontracts.MintTownyContracts;
import ru.neverland.mintcontracts.model.*;
import ru.neverland.mintcontracts.service.ContractService;
import static ru.neverland.townycompanies.CompanyData.*;
import static ru.neverland.townycompanies.ContractAccounting.*;

/** Genuine addon commands/menus/persistence; explicit seeded contract and paid-receipt fixtures. */
public final class CompanyAccountsProbe extends TntRuntimeProbe {
    static final UUID COMPANY=id("accounting-company"),CONTRACT=id("accounting-contract"),WAGE=id("accounting-wage"),TAX=id("accounting-tax"),PENDING=id("accounting-pending");
    JavaPlugin owner;CompanyService service;CompanyRepository repository;CompanyCommand commands;CompanyMenus menus;ContractService contracts;Player mayor,member;Inventory top;final List<String> messages=new ArrayList<>();
    @SuppressWarnings("unchecked") Player actor(UUID id,String name){
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(p,m,a)->switch(m.getName()){
            case "runDelayed"->Bukkit.getRegionScheduler().runDelayed(this,origin,(Consumer<ScheduledTask>)a[1],(Long)a[3]);case "run"->Bukkit.getRegionScheduler().run(this,origin,(Consumer<ScheduledTask>)a[1]);default->throw new UnsupportedOperationException(m.getName());});
        return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->name;case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp"->false;case "getScheduler"->scheduler;
            case "hasPermission"->!a[0].equals("neverlandtownycompanies.admin");case "sendMessage"->{if(a[0] instanceof String s)messages.add(s);yield null;}
            case "openInventory"->{top=(Inventory)a[0];yield null;}case "getOpenInventory"->Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,n,b)->n.getName().equals("getTopInventory")?top:null);
            case "hashCode"->id.hashCode();case "equals"->p==a[0];default->null;});
    }
    void waitFor(BooleanSupplier ready,Work next,int left)throws Exception{if(ready.getAsBoolean()){next.run();return;}if(left<=0)throw new AssertionError("accounts native wait timed out");later(1,()->waitFor(ready,next,left-1));}
    void command(Player p,String...a){owner.getCommand("townycompanies").execute(p,"company",a);}
    void command(String...a){command(mayor,a);}String lore(int slot){return String.join(" ",top.getItem(slot).getItemMeta().getLore());}
    Report report(){return service.profit.state().reports().get(reportId(COMPANY,CONTRACT));}
    void confirmed(Work next,long revision)throws Exception{command("confirm");command("confirm");waitFor(()->report()!=null&&report().revision()==revision&&!service.profit.saving(),next,150);}
    @SuppressWarnings("unchecked") void action(int slot)throws Exception{var actions=(Map<Integer,CompanyCommand.Action>)field(top.getHolder(),"actions");var a=actions.get(slot);if(a==null)throw new AssertionError("No company menu action "+slot);a.run();}
    @Override void setup()throws Exception{
        owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyCompanies");service=(CompanyService)Bukkit.getServicesManager().load(CompaniesApi.class);repository=(CompanyRepository)field(service,"repository");commands=(CompanyCommand)owner.getCommand("townycompanies").getExecutor();menus=(CompanyMenus)field(owner,"menus");contracts=((MintTownyContracts)Bukkit.getPluginManager().getPlugin("NeverLandTownyContracts")).companyContracts();
        check(owner.isEnabled()&&owner.getDescription().getVersion().equals("0.3.0")&&service.profit.available(),"actual Companies 0.3 accounting and original payroll enabled");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("AccountsCity",DEF);var town=TownyAPI.getInstance().getTown(DEF);for(var e:Map.of(MAYOR,"AccountsMayor",ACTOR,"AccountsMember").entrySet()){var r=u.getDataSource().newResident(e.getValue(),e.getKey());r.setTown(town);if(e.getKey().equals(MAYOR))town.setMayor(r);r.save();}var c=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(c);c.setTown(town);town.setHomeBlock(c);c.save();town.setSpawn(origin);town.save();
            long now=System.currentTimeMillis();service.ledger.put(new Company(COMPANY,DEF,"Проверочная компания",Kind.SHOP,MAYOR,Map.of(MAYOR,Role.OWNER,ACTOR,Role.WORKER),Map.of(),null,0,200000,0,now+604800000,1,false));var bank=service.ledger.state();var pays=new HashMap<>(bank.payments());pays.put(WAGE,new Payment(WAGE,COMPANY,ACTOR,Purpose.SALARY,1000,Phase.DONE,now));pays.put(TAX,new Payment(TAX,COMPANY,DEF,Purpose.TAX,500,Phase.DONE,now));pays.put(PENDING,new Payment(PENDING,COMPANY,ACTOR,Purpose.SALARY,100,Phase.PENDING,now));repository.save(new CompanyLedger.State(bank.companies(),pays,bank.receipts()));
            var city=new ActiveContract(CONTRACT,DEF,"native_accounting",now,now+86400000,0,64,100,Map.of());city.snapshot(new ContractDefinition("native_accounting","Поставка досок",ContractType.DELIVERY,Material.OAK_PLANKS,0,List.of(),"OAK_PLANKS",new ItemStack(Material.OAK_PLANKS),64,100,86400));contracts.repository().add(city);contracts.repository().saveOrThrow();
        }
        mayor=actor(MAYOR,"AccountsMayor");member=actor(ACTOR,"AccountsMember");service.tick();if(restart){resume();return;}
        check(service.contracts.offers(DEF).stream().anyMatch(o->CONTRACT.toString().equals(o.get("id"))&&o.get("rewardCents").equals(10000L)&&Boolean.TRUE.equals(o.get("funded"))),"actual Contracts bridge supplies integer escrow cents and funded status");
        command("profit","create",CONTRACT.toString());check(report()==null&&contracts.find(DEF,CONTRACT.toString()).companyId()==null,"creating quote neither writes a report nor accepts the contract");confirmed(()->created(),1);
    }
    void created()throws Exception{
        check(report().reward()==10000&&service.ledger.company(COMPANY).balance()==200000,"acknowledged report freezes reward without moving company funds");menus.profit(member,CONTRACT);check(top.getItem(31).getType()==Material.BARRIER&&lore(40).contains("неизвестно"),"worker sees incomplete estimate and clear reason editing is unavailable");
        command(member,"profit","budget",CONTRACT.toString(),"20","10","5");check(report().revision()==1&&!service.profit.saving(),"ordinary company worker cannot create a cost change quote");
        command("profit","budget",CONTRACT.toString(),"20","10","5");confirmed(()->budgeted(),2);
    }
    void budgeted()throws Exception{check(service.profit.summary(report()).plannedMargin()==6500,"three confirmed budget categories calculate exact planned remainder");command("profit","materials",CONTRACT.toString(),"20","Закуплено у жителей");confirmed(()->materials(),3);}
    void materials()throws Exception{
        check(service.profit.summary(report()).accountedMargin()==null&&report().declaredMaterials()==2000,"declared materials do not invent a received contract payout");
        contracts.find(DEF,CONTRACT.toString()).companyId(COMPANY);contracts.repository().saveOrThrow();menus.profitPayments(mayor,CONTRACT,0);int count=0;for(int i=0;i<45;i++)if(top.getItem(i)!=null)count++;check(count==2,"only two completed salary/tax receipts are eligible; pending payment excluded");
        commands.allocationQuote(mayor,CONTRACT,WAGE,1000);confirmed(()->wages(),4);
    }
    void wages()throws Exception{
        check(service.profit.summary(report()).paidWages()==1000&&allocated(service.profit.state(),WAGE)==1000,"completed salary source allocated once after durable confirmation");command("profit","allocate",CONTRACT.toString(),WAGE.toString(),"0.01");command("confirm");check(report().revision()==4&&!service.profit.saving(),"second attribution above paid source cannot mutate the report");
        commands.allocationQuote(mayor,CONTRACT,TAX,500);confirmed(()->taxes(),5);
    }
    void taxes()throws Exception{
        var bank=service.ledger.state();var pays=new HashMap<>(bank.payments());pays.put(CONTRACT,new Payment(CONTRACT,COMPANY,DEF,Purpose.REFUND,1000,Phase.DONE,System.currentTimeMillis()));var receipts=new HashMap<>(bank.receipts());receipts.put(CONTRACT,new Receipt(CONTRACT,COMPANY,DEF,9000,1000));repository.save(new CompanyLedger.State(bank.companies(),pays,receipts));
        var summary=service.profit.summary(report());check(summary.received()==9000&&summary.refund()==1000&&summary.accountedMargin()==5500,"seeded settlement receipt excludes city refund and uses actual allocated expenses");menus.profit(mayor,CONTRACT);check(lore(19).contains("65.00")&&lore(40).contains("55.00")&&lore(22).contains("не подтверждается"),"native report distinguishes estimate, accounted remainder and declared material evidence");
        var allocation=service.profit.state().allocations().values().stream().filter(a->a.payment().equals(WAGE)&&!a.reversed()).findFirst().orElseThrow();commands.undoQuote(mayor,CONTRACT,allocation.id());confirmed(()->undone(),6);
    }
    void undone()throws Exception{check(allocated(service.profit.state(),WAGE)==0&&service.ledger.state().payments().get(WAGE).phase()==Phase.DONE&&service.ledger.company(COMPANY).balance()==200000,"undo restores accounting capacity without reversing bank payment or changing balance");commands.allocationQuote(mayor,CONTRACT,WAGE,500);confirmed(()->holdWriter(),7);}
    @SuppressWarnings("unchecked") void holdWriter()throws Exception{
        check(service.profit.summary(report()).accountedMargin()==6000,"deliberately reassigned half receipt gives exact conserved remainder");var clazz=Class.forName("ru.neverland.core.AtomicFiles",true,owner.getClass().getClassLoader());var f=clazz.getDeclaredField("writers");f.setAccessible(true);var locks=(Map<Path,ReentrantLock>)f.get(null);var path=owner.getDataFolder().toPath().resolve("contract-accounts.yml").toAbsolutePath().normalize();var lock=locks.computeIfAbsent(path,p->new ReentrantLock());var held=new CountDownLatch(1);gate=new CountDownLatch(1);var holder=new Thread(()->{lock.lock();try{held.countDown();if(!gate.await(15,TimeUnit.SECONDS))fail(new AssertionError("cost writer gate timeout"));}catch(InterruptedException e){Thread.currentThread().interrupt();fail(e);}finally{lock.unlock();}},"native-cost-write-holder");holder.setDaemon(true);holder.start();
        waitFor(()->held.getCount()==0,()->{command("profit","budget",CONTRACT.toString(),"21","10","5");command("confirm");later(3,()->{check(report().revision()==7&&service.profit.saving(),"blocked actual file writer leaves the prior report visible while region continues");var control=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"disable","Companies"});later(3,()->{check(owner.isEnabled(),"Control waits for in-flight cost write before unloading plugin classes");gate.countDown();waitFor(()->!owner.isEnabled(),()->{check(Files.readString(path).contains("budget-materials: 2100"),"controlled disable drains the accepted report and payroll workers");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"enable","Companies"});check(!owner.isEnabled(),"Folia re-enable is deferred until full server restart");finish();},200);});});},100);
    }
    void resume()throws Exception{
        check(report().revision()==8&&report().reward()==10000&&report().budget().materials()==2100&&report().declaredMaterials()==2000,"confirmed report, frozen reward, estimate and declaration survive restart separately");
        var summary=service.profit.summary(report());check(summary.plannedMargin()==6400&&summary.accountedMargin()==6000&&summary.paidWages()==500&&summary.paidTaxes()==500,"restart preserves actual allocations and exact planned/received distinction");
        check(service.profit.state().allocations().values().stream().filter(a->a.payment().equals(WAGE)).count()==2&&service.profit.state().allocations().values().stream().anyMatch(Allocation::reversed),"reversed receipt attribution remains in saved accounting history");
        check(service.ledger.company(COMPANY).balance()==200000&&service.ledger.state().payments().get(PENDING).phase()==Phase.PENDING,"accounting neither changes original funds nor resolves ambiguous payment");
        var bank=service.ledger.state();var reduced=new HashMap<>(bank.payments());reduced.remove(WAGE);repository.save(new CompanyLedger.State(bank.companies(),reduced,bank.receipts()));check(service.profit.summary(report()).accountedMargin()==null&&service.profit.summary(report()).unverified()==1,"missing authoritative salary receipt produces incomplete report instead of fake profit");repository.save(bank);
        var api=Bukkit.getServicesManager().load(CompaniesApi.class);check(api.contractAccounting(COMPANY,CONTRACT).get("accountedMarginCents").equals(6000L),"public primitive report API matches restored verified accounting");
        var old=service.profit;byte[] data=Files.readAllBytes(owner.getDataFolder().toPath().resolve("contract-accounts.yml"));owner.getConfig().set("accounting.enabled",false);service.profit=new CompanyProfit(owner,service);menus.profits(mayor,0);check(top.getItem(22).getType()==Material.BARRIER&&service.ledger.company(COMPANY).balance()==200000&&Arrays.equals(data,Files.readAllBytes(owner.getDataFolder().toPath().resolve("contract-accounts.yml"))),"neutral feature disable preserves accounting file, company funds and existing contracts");service.profit.prepareClose();service.profit=old;owner.getConfig().set("accounting.enabled",true);finish();
    }
    @Override void finish()throws Exception{var rows=new ArrayList<AuditRecord>();AuditJournal.scan(owner.getDataFolder().toPath().resolve("audit"),rows::add);check(rows.stream().filter(r->r.kind().equals("COMPANY_CONTRACT_COST")).count()==8,"eight acknowledged report changes create eight unique audit operations despite repeated confirm");TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; real Folia/Towny/Companies/Contracts commands, native report inventories, actual delayed atomic writer, immutable cost allocation and Control/full-restart lifecycle; explicit offline player/scheduler/view, funded-contract and paid-bank-receipt fixtures; no real bank payout, player acceptance or GUI client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
