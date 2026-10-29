package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.block.BlockGrowEvent;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.object.economy.*;
import com.palmergames.bukkit.towny.object.economy.adapter.*;
import com.palmergames.bukkit.towny.object.economy.provider.*;
import ru.neverland.core.*;
import ru.neverland.townyideologies.*;
import ru.neverland.townyideologies.api.*;
import ru.neverland.townyideologies.service.*;
import ru.neverland.townyideologies.integration.*;
import ru.neverland.townyideologies.gui.*;
import ru.neverland.townyideologies.reforms.*;
import static ru.neverland.townyideologies.reforms.ReformBook.*;

/** Real Folia/Towny account observer, state/API/events/menus. Actual banking backend and actors are explicit substitutes. */
public final class IdeologyReformsProbe extends AdmissionsProbe {
    static final UUID U=id("reform-other-town");NeverLandTownyIdeologies plugin;IdeologyService service;IdeologyRegistry registry;MenuManager menu;BonusService bonuses;Town helper;FixtureBank bank=new FixtureBank();boolean canSelect=true;UUID operation,staleReceipt;Path data;
    static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    void in(Work work){try{work.run();}catch(Throwable error){fail(error);}}
    void idle(Work next){idle(next,150);}
    void idle(Work next,int left){later(1,()->{if(service.data().busy()||(boolean)field(service,"paymentBusy")){if(left<1)throw new AssertionError("reform did not settle");idle(next,left-1);}else next.run();});}
    @Override InventoryView view(Player p,UUID uuid){return(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,m,a)->switch(m.getName()){case "getTopInventory"->windows.computeIfAbsent(uuid,k->Bukkit.createInventory(null,9));case "getPlayer"->p;case "getType"->InventoryType.CHEST;case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];case "getItem"->windows.get(uuid).getItem((Integer)a[0]);case "setItem"->{windows.get(uuid).setItem((Integer)a[0],(ItemStack)a[1]);yield null;}default->null;});}
    Player member(UUID uuid,String name){Player base=actor(uuid,name);return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->m.getName().equals("hasPermission")&&a[0].equals("neverlandtownyideologies.select")?canSelect:m.invoke(base,a));}
    Town city(UUID uuid,UUID resident,String name,int chunk)throws Exception{var u=TownyUniverse.getInstance();u.newTownInternal(name,uuid);var t=TownyAPI.getInstance().getTown(uuid);var r=u.getDataSource().newResident(name+"Mayor",resident);r.setTown(t);t.setMayor(r);r.save();var block=new TownBlock(chunk,0,u.getWorld(world.getName()));u.addTownBlock(block);block.setTown(t);t.setHomeBlock(block);block.save();t.setSpawn(origin);t.save();return t;}
    @Override void setup()throws Exception{
        plugin=(NeverLandTownyIdeologies)Bukkit.getPluginManager().getPlugin("NeverLandTownyIdeologies");service=plugin.ideologyService();registry=plugin.ideologyRegistry();bonuses=new BonusService(plugin,new TownyHook(),service.data(),service,registry);((org.bukkit.scheduler.BukkitTask)field(service,"task")).cancel();
        data=plugin.getDataFolder().toPath().resolve("towns.yml");mayor=member(M,"ReformCityMayor");sponsor=member(S,"ReformHelperMayor");var items=new ItemsAdderHook(plugin);menu=new MenuManager(plugin,new TownyHook(),registry,service,new EconomyService(plugin,items),items,new MessageService(plugin));
        check(plugin.isEnabled()&&plugin.getDescription().getVersion().equals("0.2.0"),"actual Ideologies0.2 plugin starts with preserved data");set(service,"bank",bank);
        if(restart){resumed();return;}
        town=city(T,M,"ReformCity",0);helper=city(U,S,"ReformHelper",1);
        check(service.get(town).orElseThrow().level()==1&&service.get(town).orElseThrow().selectedAt()==123456,"legacy active level and selection timestamp preserved without repayment");bankEvidence();
        plugin.getConfig().set("settings.economy.mode","ITEM");denied(()->service.offer(mayor,"agriculture"),"unsupported item currency rejects new paid reform before reservation");check(service.reform(T).isEmpty()&&bank.calls==0,"unsupported mode preserves old effects and zero bank calls");plugin.getConfig().set("settings.economy.mode","TOWN_BANK");
        menu.openDetails(mayor,"agriculture");click(15);check(lore(mayor,11).contains("деньги не списываются")&&lore(mayor,11).contains("После"),"native initial confirmation explains old/new effects and deferred charge");holdJournal();click(11);later(3,()->{check(service.data().busy()&&service.reform(T).isEmpty()&&service.get(town).orElseThrow().level()==1,"delayed actual file publishes neither reform nor target benefit before acknowledgement");check(bank.calls==0,"region callback continues while reform journal write is physically blocked");release.countDown();idle(this::started);});
    }
    void holdJournal()throws Exception{
        var atomic=Class.forName("ru.neverland.core.AtomicFiles",true,plugin.getClass().getClassLoader());var locksField=atomic.getDeclaredField("writers");locksField.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,java.util.concurrent.locks.ReentrantLock>)locksField.get(null);var lock=locks.computeIfAbsent(data.toAbsolutePath().normalize(),key->new java.util.concurrent.locks.ReentrantLock());release=new CountDownLatch(1);var entered=new CountDownLatch(1);
        var worker=new Thread(()->{lock.lock();entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}finally{lock.unlock();}},"native-delayed-ideology-journal");worker.setDaemon(true);worker.start();if(!entered.await(1,TimeUnit.SECONDS))throw new AssertionError("file delay did not start");
    }
    void click(int slot)throws Exception{var click=new InventoryClickEvent(view(mayor,M),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);menu.onClick(click);check(click.isCancelled(),"native ideology menu cancels item movement");}
    void started()throws Exception{
        var r=service.reform(T).orElseThrow();operation=r.id();check(r.cents()==5000&&r.target().level()==2&&bank.calls==0,"acknowledged start freezes original50coin price without charging");check(service.get(town).orElseThrow().level()==1&&growth()==1,"native growth effect still uses only previous ideology");
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("ideologies.yml").toFile());config.set("ideologies.agriculture.upgrade-prices.2",999);config.set("ideologies.agriculture.bonuses.growth-multiplier",List.of(9D,9D,9D,9D,9D));config.save(plugin.getDataFolder().toPath().resolve("ideologies.yml").toFile());registry.reload();
        check(service.reform(T).orElseThrow().cents()==5000&&growth()==1,"catalogue edit cannot reprice accepted reform or change frozen old effect");
        service.control(operation,"pause","native administrator");idle(()->advance(100000,()->{check(service.reform(T).orElseThrow().worked()==0,"administrative pause excludes elapsed module time");service.control(operation,"resume","native administrator");idle(()->advance(100000,()->{long worked=service.reform(T).orElseThrow().worked();check(worked==10000,"very long observed tick counts at most10seconds");ready(this::readyPayment);}));}));
    }
    void advance(long millis,Work next)throws Exception{set(service,"pulseAt",System.nanoTime()-millis*1000000);var method=service.getClass().getDeclaredMethod("pulse");method.setAccessible(true);method.invoke(service);idle(next);}
    void ready(Work next)throws Exception{if(service.reform(T).orElseThrow().phase()==Phase.READY){next.run();return;}advance(10000,()->ready(next));}
    void readyPayment()throws Exception{
        check(bank.calls==0&&service.get(town).orElseThrow().level()==1,"all timed stages alone never pay or activate target");
        plugin.getConfig().set("settings.economy.mode","ITEM");denied(()->service.offer(mayor,"agriculture"),"unsupported currency blocks already-ready paid reform");plugin.getConfig().set("settings.economy.mode","TOWN_BANK");
        var changed=service.offer(mayor,"agriculture");plugin.getConfig().set("settings.economy.mode","ITEM");denied(()->service.confirm(mayor,changed.token()),"currency change before confirmation invalidates payment offer");check(bank.calls==0&&service.reform(T).orElseThrow().attempts().isEmpty(),"currency change creates no payment intent or debit");plugin.getConfig().set("settings.economy.mode","TOWN_BANK");
        var afterAck=service.offer(mayor,"agriculture");var deferred=service.confirm(mayor,afterAck.token());plugin.getConfig().set("settings.economy.mode","ITEM");await(deferred,text->in(()->{plugin.getConfig().set("settings.economy.mode","TOWN_BANK");check(bank.calls==0&&service.reform(T).orElseThrow().phase()==Phase.READY,"currency change after journal acknowledgement still prevents debit");permissionPayment();}));
    }
    void permissionPayment()throws Exception{
        var offer=service.offer(mayor,"agriculture");var payment=service.confirm(mayor,offer.token());canSelect=false;
        await(payment,text->in(()->{canSelect=true;check(bank.calls==0&&service.reform(T).orElseThrow().phase()==Phase.READY,"permission loss after journal acknowledgement stops external debit");retired();}));
    }
    void retired()throws Exception{
        var base=mayor;var scheduler=(io.papermc.paper.threadedregions.scheduler.EntityScheduler)Proxy.newProxyInstance(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class.getClassLoader(),new Class<?>[]{io.papermc.paper.threadedregions.scheduler.EntityScheduler.class},(p,m,a)->{((Runnable)a[2]).run();return null;});
        var retired=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->m.getName().equals("getScheduler")?scheduler:m.invoke(base,a));var offer=service.offer(retired,"agriculture");await(service.confirm(retired,offer.token()),text->in(()->{var r=service.reform(T).orElseThrow();staleReceipt=r.attempts().getLast().id();check(r.phase()==Phase.READY&&bank.calls==0&&r.attempts().size()==3,"retired callback plus null schedule result settles one attempt only");insufficient();}));
    }
    void insufficient()throws Exception{bank.insufficient=true;var offer=service.offer(mayor,"agriculture");var future=service.confirm(mayor,offer.token());var finish=service.getClass().getDeclaredMethod("finishExternal",UUID.class);finish.setAccessible(true);finish.invoke(service,staleReceipt);check((boolean)field(service,"paymentBusy"),"stale retirement cannot clear a newer payment guard");await(future,text->in(()->{check(bank.calls==1&&bank.money==100000&&service.reform(T).orElseThrow().phase()==Phase.READY,"known insufficient bank outcome preserves ready reform and old effect");bank.insufficient=false;var offer2=service.offer(mayor,"agriculture");await(service.confirm(mayor,offer2.token()),result->in(this::paid));}));}
    int growth(){var block=world.getBlockAt(8,120,8);var state=block.getState();Ageable wheat=(Ageable)Material.WHEAT.createBlockData();wheat.setAge(1);state.setBlockData(wheat);var event=new BlockGrowEvent(block,state);bonuses.onGrow(event);return((Ageable)event.getNewState().getBlockData()).getAge();}
    void paid()throws Exception{
        check(bank.calls==2&&bank.money==95000&&service.get(town).orElseThrow().level()==2&&service.reform(T).isEmpty(),"one successful frozen50coin payment activates exact target once");check(growth()==2,"native grow event uses accepted target multiplier2 rather than edited9");check(service.get(town).orElseThrow().selectedAt()==123456,"paid upgrade keeps original selected timestamp");
        var next=service.offer(mayor,"agriculture");await(service.confirm(mayor,next.token()),text->in(()->{operation=service.reform(T).orElseThrow().id();ready(this::unknown);}));
    }
    void unknown()throws Exception{bank.throwAfter=true;var offer=service.offer(mayor,"agriculture");await(service.confirm(mayor,offer.token()),text->in(()->{check(bank.calls==3&&bank.money==45000&&service.reform(T).orElseThrow().phase()==Phase.PAYMENT_PENDING,"mutation followed by missing bank acknowledgement remains pending");check(service.get(town).orElseThrow().level()==2,"unknown bank result cannot activate new benefit");denied(()->service.offer(mayor,"agriculture"),"unknown bank outcome blocks another confirmation");denied(()->service.control(operation,"cancel","native"),"unknown bank outcome cannot be cancelled blindly");
            var other=service.offer(sponsor,"agriculture");await(service.confirm(sponsor,other.token()),v->in(()->advance(2000,()->{var second=service.reform(U).orElseThrow();service.control(second.id(),"pause","native");idle(()->{check(adminStatusOnly(operation),"admin panel exposes status only for unknown payment");Files.writeString(proof.resolve("expected.txt"),operation+"\n"+second.id()+"\n"+service.reform(U).orElseThrow().worked()+"\n");finishReforms();});})));}));}
    boolean adminStatusOnly(UUID operation)throws Exception{for(Object target:service.adminTargets()){if(target.getClass().getMethod("id").invoke(target).equals(operation.toString()))return target.getClass().getMethod("actions").invoke(target).equals(Set.of("status"));}return false;}
    void resumed()throws Exception{
        town=TownyAPI.getInstance().getTown(T);helper=TownyAPI.getInstance().getTown(U);var lines=Files.readAllLines(proof.resolve("expected.txt"));operation=UUID.fromString(lines.get(0));UUID other=UUID.fromString(lines.get(1));
        check(service.reform(T).orElseThrow().phase()==Phase.PAYMENT_PENDING&&service.get(town).orElseThrow().level()==2&&bank.calls==0,"restart preserves unknown payment and never invokes bank");check(service.reform(U).orElseThrow().paused()&&service.reform(U).orElseThrow().worked()==Long.parseLong(lines.get(2)),"second city retains exact paused worked time across full JVM downtime");
        await(service.resolve(operation,true,"Native bank record confirmed"),text->in(()->{check(bank.calls==0&&service.get(town).orElseThrow().level()==3,"manual acknowledged reconciliation activates paid target without a second debit");denied(()->service.resolve(operation,true,"repeat"),"same completed receipt cannot be reconciled twice");service.control(other,"cancel","native");idle(()->{check(service.reform(U).isEmpty()&&service.get(helper).isEmpty()&&bank.calls==0,"admin cancellation of unpaid preparation leaves city unchanged");
            var audit=new StringBuilder();try(var paths=Files.walk(plugin.getDataFolder().toPath().resolve("audit"))){for(var path:paths.filter(p->p.toString().endsWith(".tsv")).toList())audit.append(Files.readString(path));}check(audit.toString().contains("CITY_IDEOLOGY")&&audit.toString().contains(operation.toString()),"accepted audit queue drained before previous shutdown");finishReforms();});}));
    }
    void finishReforms()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; actual Folia Towny Ideologies services, native Towny AccountObserver with substituted adapter, crop event, menu, async permission checks, durable journal, stale retire callbacks and full restart. Main banking boundary, player/view/scheduler and elapsed-clock samples are explicit fixtures; no live Vault/backend or network client claim.\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
    public static class FixtureBank extends ReformBank {long money=100000;int calls;boolean insufficient,throwAfter;public boolean ready(UUID town){return true;}public boolean pay(UUID town,UUID receipt,long cents){if(SuiteState.held())throw new AssertionError("bank called while owning Suite lock");calls++;if(insufficient)return false;money-=cents;if(throwAfter){throwAfter=false;throw new IllegalStateException("Native fixture lost response after debit");}return true;}}
    void bankEvidence()throws Exception{
        check(!new ReformBank().ready(T),"real ReformBank fails closed without Towny economy adapter");var adapter=new NativeAdapter();var economy=TownyEconomyHandler.class.getDeclaredField("economy");var provider=TownyEconomyHandler.class.getDeclaredField("provider");economy.setAccessible(true);provider.setAccessible(true);Object oldEconomy=economy.get(null),oldProvider=provider.get(null);Object using=TownySettings.getProperty("economy.using_economy");
        try{economy.set(null,adapter);provider.set(null,new VaultEconomyProvider());TownySettings.setProperty("economy.using_economy",true);town.getAccount().getHoldingBalance(true);
            var bank=new ReformBank();check(bank.ready(T)&&bank.pay(T,UUID.randomUUID(),1000)&&adapter.balance==190,"actual Towny withdrawal observer confirms exact debit and token");adapter.deny=true;denied(()->bank.pay(T,UUID.randomUUID(),1000),"unobserved false bank result requires review even with unchanged balance");check(adapter.balance==190,"refused bank call does not get an automatic retry");adapter.deny=false;adapter.balance=5;town.getAccount().getHoldingBalance(true);int beforeCalls=adapter.calls;check(!bank.pay(T,UUID.randomUUID(),1000)&&adapter.calls==beforeCalls,"known insufficient balance rejected before invoking debit");adapter.balance=190;town.getAccount().getHoldingBalance(true);adapter.restoreFalse=true;denied(()->bank.pay(T,UUID.randomUUID(),1000),"mutation restored by a second movement cannot turn missing receipt into known refusal");check(adapter.balance==190,"same ending balance is not proof of no debit");adapter.restoreFalse=false;adapter.throwAfter=true;denied(()->bank.pay(T,UUID.randomUUID(),1000),"actual adapter mutation without observer confirmation is unknown");check(adapter.balance==180,"unknown native adapter outcome is not automatically refunded or retried");
        }finally{TownySettings.setProperty("economy.using_economy",using);economy.set(null,oldEconomy);provider.set(null,oldProvider);}
    }
    static final class NativeAdapter implements EconomyAdapter {double balance=200;int calls;boolean deny,throwAfter,restoreFalse;public String name(){return "NativeFixture";}public boolean add(Account a,double amount){balance+=amount;return true;}public boolean subtract(Account a,double amount){calls++;if(deny)return false;balance-=amount;if(restoreFalse){balance+=amount;return false;}if(throwAfter)throw new IllegalStateException("lost native adapter response");return true;}public boolean hasAccount(Account a){return true;}public double getBalance(Account a){return balance;}public void newAccount(Account a){}public void deleteAccount(Account a){}public boolean renameAccount(Account a,String name){return true;}public boolean setBalance(Account a,double amount){balance=amount;return true;}public String getFormattedBalance(double amount){return Double.toString(amount);}}
}
