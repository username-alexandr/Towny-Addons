package ru.neverland.runtime;

import java.nio.file.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.townycontrol.*;

/** Real Control menus, SQLite index, file audit and Folia lifecycle. Seeded evidence is not a bank payment. */
public final class ControlAuditProbe extends AdmissionsProbe {
    NeverLandTownyControl control;Object menu,queue;BankAudit bank;Path journalFile,index;byte[] initial;
    final AtomicBoolean allowed=new AtomicBoolean(true),op=new AtomicBoolean();
    final UUID B=id("audit-town-b"),C=id("audit-town-c");final String review=id("review-operation").toString();
    AuditRecord exact,shutdown;int messageStart;
    interface PredicateCheck {boolean ready()throws Exception;}
    Object call(Object owner,String method,Class<?>[] types,Object... args)throws Exception{try{var m=owner.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(owner,args);}catch(InvocationTargetException error){if(error.getCause() instanceof Exception ex)throw ex;throw error;}}
    Class<?> local(String name)throws Exception{return Class.forName("ru.neverland.townycontrol."+name,true,control.getClass().getClassLoader());}
    String digest(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    Player administrator(){var base=actor(M,"AuditAdmin");var handler=Proxy.getInvocationHandler(base);return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){case "isOp"->op.get();case "hasPermission"->a[0].toString().startsWith("neverlandtownycontrol.")?allowed.get():handler.invoke(base,m,a);default->handler.invoke(base,m,a);});}
    AuditRecord.Party city(UUID id,String name){return new AuditRecord.Party("TOWN",id.toString(),name,id.toString());}
    AuditRecord record(String key,long at,String module,String kind,String outcome,String operation,AuditRecord.Party from,AuditRecord.Party to,String money){return new AuditRecord(AuditRecord.id(module,key,"probe"),at,module,kind,outcome,operation,new AuditRecord.Party("PLAYER",M.toString(),"AuditAdmin",T.toString()),from,to,"",0,money,"Explicit native audit fixture; no payment was executed");}
    void command(String label,String... args){control.getCommand(label).execute(mayor,label,args);}
    void click(int slot)throws Exception{var event=new InventoryClickEvent(view(mayor,M),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);if(!event.isCancelled())throw new AssertionError("native audit click did not protect icons");}
    void poll(int left,PredicateCheck condition,Work next)throws Exception{if(condition.ready()){next.run();return;}if(left<=0)throw new AssertionError("native audit wait timed out; messages="+messages);later(1,()->poll(left-1,condition,next));}
    boolean named(int slot,String text){var inventory=windows.get(M);return inventory!=null&&inventory.getItem(slot)!=null&&inventory.getItem(slot).getItemMeta().getDisplayName().contains(text);}
    String newMessages(){return String.join("\n",messages.subList(messageStart,messages.size()));}
    @Override void setup()throws Exception{
        control=(NeverLandTownyControl)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");menu=field(control,"adminMenu");bank=(BankAudit)field(control,"bankAudit");queue=field(bank,"queue");mayor=administrator();
        check(control.isEnabled()&&control.getDescription().getVersion().equals("0.6.0"),"actual Control 0.6.0 enabled on Folia with original Towny API");
        index=control.getDataFolder().toPath().resolve("audit-index/derived-v1.sqlite");
        if(restart){resumeControl();return;}
        var u=TownyUniverse.getInstance();u.newTownInternal("AuditSourceCity",T);town=TownyAPI.getInstance().getTown(T);var resident=u.getDataSource().newResident("AuditAdmin",M);resident.setTown(town);town.setMayor(resident);resident.save();var claim=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(origin);town.save();
        long now=System.currentTimeMillis();var a=city(T,"AuditSourceCity");var b=city(B,"SecondCity");var c=city(C,"ThirdCity");var journal=new AuditJournal(control.getDataFolder().toPath().resolve("audit"));
        for(var r:List.of(
            record("review",now-1_200_000,"NeverLandTownyMarket","MARKET_DEAL","REVIEW",review,a,b,"500"),
            record("related",now-900_000,"NeverLandTownyContracts","CONTRACT_PAYMENT","COMPLETED",review,a,b,"500"),
            record("transfer-one",now-120_000,"NeverLandTownyControl","BANK_TRANSFER","COMPLETED","chain-one",a,b,"1200"),
            record("transfer-two",now-60_000,"NeverLandTownyControl","BANK_TRANSFER","COMPLETED","chain-two",b,c,"1180"),
            record("leg",now-110_000,"NeverLandTownyControl","BANK_LEG","CREDIT","leg",b,c,"1200"),
            record("command",now-100_000,"NeverLandTownyControl","BANK_COMMAND","OBSERVED","command",b,c,"1200"),
            record("old-pending",now-1_400_000,"NeverLandTownyMarket","MARKET_DEAL","PREPARED","resolved",a,b,"10"),
            record("done",now-1_300_000,"NeverLandTownyMarket","MARKET_DEAL","DONE","resolved",a,b,"10")
        ))journal.append(r);
        journalFile=AuditJournal.files(journal.directory()).get(0);initial=Files.readAllBytes(journalFile);Files.writeString(proof.resolve("source-file.txt"),journalFile.getFileName().toString());Files.writeString(proof.resolve("source-seeded.sha256"),digest(initial));
        command("nltadmin","logs");check(!mayor.isOp()&&named(42,"Проверка операций"),"non-OP administrator permission opens real logging menu and review button42");
        click(42);poll(80,()->named(40,"Как читать карточки"),this::signalsReady);
    }
    void signalsReady()throws Exception{
        check(named(0,"Быстрая передача")&&named(1,"Проверить завершение")&&lore(mayor,48).contains("Найдено: 2"),"actual indexed journal produces one three-city chain and one unresolved review, without counting bank legs or resolved operation");
        check(lore(mayor,0).contains("не доказательство")&&lore(mayor,1).contains("Сверьте"),"native review cards clearly require verification and make no automatic accusation");
        click(1);later(2,()->{check(named(40,"Проверить завершение")&&lore(mayor,0).contains(review),"native review detail shows original operation ID");click(0);poll(80,()->named(48,"Страница"),this::relatedReady);});
    }
    void relatedReady()throws Exception{
        check(lore(mayor,48).contains("Найдено: 2"),"related-operation lookup includes exact ID across two modules only");
        click(0);later(2,()->{
            check(lore(mayor,25).contains(review)&&named(42,"Связанные записи"),"full receipt exposes exact operation and related-record action");
            check(Files.isRegularFile(index)&&Arrays.equals(initial,Files.readAllBytes(journalFile)),"SQLite derived index exists while admin inspection leaves authoritative source bytes unchanged");
            permissionChecks();
        });
    }
    void permissionChecks()throws Exception{
        command("nltadmin","logs");click(42);var foreign=Bukkit.createInventory(null,9);windows.put(M,foreign);
        later(3,()->{check(windows.get(M)==foreign,"deferred old admin click cannot replace a newer window");
            command("nltadmin","logs");click(42);allowed.set(false);
            later(3,()->{check(!windows.containsKey(M),"revoked admin audit permission closes pending menu action");
                op.set(true);command("nltadmin","logs");check(named(42,"Проверка операций"),"OP retains administrative menu access without assigned permission");op.set(false);allowed.set(true);staleAsync();
            });
        });
    }
    void staleAsync()throws Exception{
        var type=local("AuditIndex");var lockField=type.getDeclaredField("WRITE");lockField.setAccessible(true);Object lock=lockField.get(null);var gate=hold(lock,"native-audit-index-delay");
        command("nltadmin","logs");click(42);
        poll(30,()->named(22,"Проверяем квитанции"),()->{var foreign=Bukkit.createInventory(null,9);windows.put(M,foreign);gate.countDown();later(5,()->{check(windows.get(M)==foreign,"completed asynchronous index query cannot reopen a replaced admin menu");corruption();});});
    }
    CountDownLatch hold(Object monitor,String name)throws Exception{
        var gate=new CountDownLatch(1);release=gate;var entered=new CountDownLatch(1);var thread=new Thread(()->{synchronized(monitor){entered.countDown();try{gate.await(12,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}},name);thread.setDaemon(true);thread.start();if(!entered.await(1,TimeUnit.SECONDS))throw new AssertionError("fixture monitor unavailable");return gate;
    }
    void corruption()throws Exception{
        Files.writeString(journalFile,"broken-tail",StandardOpenOption.APPEND);messageStart=messages.size();command("nltaudit","search","kind=NOT_IN_SOURCE");
        poll(100,()->newMessages().contains("не прочитан полностью"),()->{
            check(newMessages().contains("не выдан как полный"),"real corrupted source refuses stale or empty indexed results even outside the selected filter");
            Files.write(journalFile,initial);messageStart=messages.size();command("nltaudit","search","id="+review);
            poll(100,()->newMessages().contains("Аудит: 2 записей"),()->{
                check(Arrays.equals(initial,Files.readAllBytes(journalFile)),"repairing fixture source recovers exact indexed lookup without changing source bytes");
                Files.writeString(index,"not a database");messageStart=messages.size();command("nltaudit","search","id="+review);
                poll(100,()->newMessages().contains("не прочитан полностью"),()->{
                    check(newMessages().contains("reindex"),"broken derived cache requests reindex instead of inventing complete results");
                    messageStart=messages.size();command("nltaudit","reindex");poll(100,()->newMessages().contains("Индекс пересобран"),()->{
                        check(newMessages().contains("8 записей")&&Arrays.equals(initial,Files.readAllBytes(journalFile)),"actual reindex command rebuilds disposable cache and preserves authoritative history");blockedBank();
                    });
                });
            });
        });
    }
    Object actualJournal()throws Exception{
        var trail=Class.forName("ru.neverland.core.AuditTrail",true,control.getClass().getClassLoader());var field=trail.getDeclaredField("journals");field.setAccessible(true);@SuppressWarnings("unchecked")var journals=(Map<Path,Object>)field.get(null);Path path=control.getDataFolder().toPath().resolve("audit");Object journal=journals.get(path);if(journal==null){journal=Class.forName("ru.neverland.core.AuditJournal",true,control.getClass().getClassLoader()).getConstructor(Path.class).newInstance(path);journals.put(path,journal);}return journal;
    }
    boolean enqueue(AuditRecord record)throws Exception{var method=Arrays.stream(queue.getClass().getDeclaredMethods()).filter(m->m.getName().equals("submit")).findFirst().orElseThrow();method.setAccessible(true);var type=method.getParameterTypes()[0];Object captured=type.getMethod("parse",List.class).invoke(null,record.fields());return Boolean.TRUE.equals(method.invoke(queue,captured));}
    void captureBank(AuditRecord.Party from)throws Exception{var method=Arrays.stream(bank.getClass().getDeclaredMethods()).filter(m->m.getName().equals("capture")).findFirst().orElseThrow();method.setAccessible(true);var type=method.getParameterTypes()[2];var constructor=type.getConstructor(String.class,String.class,String.class,String.class);Object actor=constructor.newInstance("SYSTEM","","Автоматически","");Object source=constructor.newInstance(from.type(),from.id(),from.name(),from.town());Object target=constructor.newInstance("UNKNOWN","","Неизвестно","");method.invoke(bank,"BANK_LEG","DEBIT",actor,source,target,3.0,"captured before city rename");}
    void blockedBank()throws Exception{
        var gate=hold(actualJournal(),"native-bank-audit-delay");long captured=System.currentTimeMillis()-12345;
        exact=record("exact-bank",captured,"NeverLandTownyControl","BANK_LEG","CREDIT","exact-bank",AuditRecord.Party.unknown(),city(T,"Captured City"),"12.50");
        check(enqueue(exact),"actual bounded bank queue accepts immutable pre-captured receipt");
        var snapshot=AuditTrail.town(T);captureBank(snapshot);
        TownyUniverse.getInstance().getDataSource().renameTown(town,"AuditRenamedCity");town.save();
        var heartbeat=new AtomicBoolean();Bukkit.getGlobalRegionScheduler().runDelayed(this,t->SuiteState.tryRun("native bank audit heartbeat",()->heartbeat.set(true)),2);
        later(5,()->{check(bank.status().contains("очереди 2")&&Arrays.equals(initial,Files.readAllBytes(journalFile)),"blocked real bank audit append remains pending without false durable records");check(heartbeat.get(),"global SuiteState remains available while bank audit filesystem worker waits");gate.countDown();
            poll(100,()->bank.status().contains("очереди 0"),this::bankWritten);
        });
    }
    void bankWritten()throws Exception{
        var records=new ArrayList<AuditRecord>();AuditJournal.scan(journalFile.getParent(),records::add);check(records.stream().filter(r->r.id().equals(exact.id())).count()==1&&records.stream().anyMatch(r->r.equals(exact)),"actual bank writer preserves the exact immutable receipt ID and observation time");
        check(records.stream().anyMatch(r->r.details().equals("captured before city rename")&&r.from().name().equals("AuditSourceCity"))&&town.getName().equals("AuditRenamedCity"),"queued bank observation retains captured Towny name after the real city was renamed");
        check(bank.status().contains("записано 2")&&bank.status().contains("пропуски за запуск 0"),"native queue diagnostics report completed writes without gaps");
        var gate=hold(actualJournal(),"native-bank-shutdown-delay");shutdown=record("shutdown-bank",System.currentTimeMillis()-3210,"NeverLandTownyControl","BANK_LEG","CREDIT","shutdown-bank",AuditRecord.Party.unknown(),city(T,"AuditRenamedCity"),"7.00");
        check(enqueue(shutdown)&&bank.status().contains("очереди 1"),"real Control shutdown begins with an accepted bank audit receipt still pending");
        Files.writeString(proof.resolve("shutdown-record.txt"),AuditJournal.encode(shutdown));Files.writeString(proof.resolve("exact-record.txt"),AuditJournal.encode(exact));
        var unblock=new Thread(()->{try{long until=System.nanoTime()+8_000_000_000L;while(control.isEnabled()&&System.nanoTime()<until)Thread.sleep(10);boolean free=false;long guardDeadline=System.nanoTime()+2_000_000_000L;while(!(free=SuiteState.tryRun("native Control shutdown audit drain",()->{}))&&System.nanoTime()<guardDeadline)Thread.sleep(10);Files.writeString(proof.resolve("shutdown-drain.txt"),"disabled="+!control.isEnabled()+" suiteFree="+free+" "+bank.status());Thread.sleep(200);}catch(Exception ex){try{Files.writeString(proof.resolve("shutdown-drain-failed.txt"),ex.toString());}catch(Exception ignored){}}finally{gate.countDown();}},"native-control-shutdown-release");unblock.setDaemon(true);unblock.start();finishControl();
    }
    void resumeControl()throws Exception{
        town=TownyAPI.getInstance().getTown(T);check(town!=null&&town.getName().equals("AuditRenamedCity"),"actual renamed Towny city survives full JVM restart");journalFile=control.getDataFolder().toPath().resolve("audit").resolve(Files.readString(proof.resolve("source-file.txt")));
        var rows=new ArrayList<AuditRecord>();AuditJournal.scan(journalFile.getParent(),rows::add);var exactExpected=AuditJournal.decode(Files.readString(proof.resolve("exact-record.txt")));var shutdownExpected=AuditJournal.decode(Files.readString(proof.resolve("shutdown-record.txt")));
        check(rows.size()==11&&rows.stream().filter(r->r.id().equals(exactExpected.id())).count()==1&&rows.contains(exactExpected)&&rows.contains(shutdownExpected),"full restart preserves exact bank receipts including the pending shutdown receipt without replay");
        String drain=Files.readString(proof.resolve("shutdown-drain.txt"));check(drain.contains("disabled=true")&&drain.contains("suiteFree=true")&&drain.contains("очереди 1")&&!Files.exists(proof.resolve("shutdown-drain-failed.txt")),"actual Control onDisable drains pending bank audit outside shared SuiteState");
        byte[] saved=Files.readAllBytes(journalFile);check(Files.isRegularFile(index),"derived SQLite cache remains available after actual JVM restart");messageStart=messages.size();command("nltaudit","search","id="+review);
        poll(100,()->newMessages().contains("Аудит: 2 записей"),()->{check(Arrays.equals(saved,Files.readAllBytes(journalFile)),"reopened index and related lookup leave persistent audit evidence unchanged");messageStart=messages.size();command("nltaudit","reindex");
            poll(100,()->newMessages().contains("Индекс пересобран"),()->{check(newMessages().contains("11 записей")&&Arrays.equals(saved,Files.readAllBytes(journalFile)),"post-restart rebuild includes drained bank tail without changing source");command("nltadmin","logs");click(42);poll(100,()->named(40,"Как читать карточки"),()->{check(lore(mayor,48).contains("Найдено: 2"),"review hints remain reproducible after restart and bank tail is not double-counted");Files.writeString(proof.resolve("source-final.sha256"),digest(saved));finishControl();});});
        });
    }
    void finishControl()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" checks; actual Folia Control menus, derived SQLite index, corruption recovery, permissions, immutable bank audit and shutdown drain; seeded administrative evidence and offline Player/View/EntityScheduler fixtures; no real bank payment or network rendering claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
