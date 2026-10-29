package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.townychronicles.*;
import ru.neverland.townychronicles.chapters.*;
import ru.neverland.townychronicles.model.*;
import ru.neverland.townychronicles.service.*;

/** Real Folia and persistent journal, with explicit historical receipts and offline Player/View fixtures. */
public final class ChronicleChaptersProbe extends AdmissionsProbe {
    TownyChronicles plugin;ChronicleRepository repository;ChapterMenu chapters;ContributionsMenu history;Path data;byte[] original;int expectedCount;
    @Override void setup()throws Exception{
        plugin=(TownyChronicles)Bukkit.getPluginManager().getPlugin("NeverLandTownyChronicles");repository=(ChronicleRepository)field(plugin,"repository");chapters=(ChapterMenu)field(plugin,"chapters");history=(ContributionsMenu)field(plugin,"contributions");data=plugin.getDataFolder().toPath().resolve("chronicles.yml");mayor=actor(M,"ChaptersMayor");
        check(plugin.isEnabled()&&plugin.getDescription().getVersion().equals("0.3.0"),"validated Chronicles 0.3.0 on actual Folia and Towny");
        if(restart){town=TownyAPI.getInstance().getTown(T);check(town!=null,"actual Towny city survives restart");original=Files.readAllBytes(data);expectedCount=Integer.parseInt(Files.readString(proof.resolve("expected-count.txt")));waitHistory(80);return;}
        var u=TownyUniverse.getInstance();u.newTownInternal("ChaptersCity",T);town=TownyAPI.getInstance().getTown(T);var resident=u.getDataSource().newResident("ChaptersMayor",M);resident.setTown(town);town.setMayor(resident);resident.save();var claim=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(origin);town.save();
        var state=repository.state(T);state.name(town.getName());state.mayor("ChaptersMayor",M);long now=System.currentTimeMillis()-120_000;state.foundedAt(now-5000);
        repository.add(new ChronicleEntry(id("old-history"),T,town.getName(),ChronicleCategory.FOUNDING,now-5000,"Old saved founding",List.of("Keep exact old details"),"Founder","Towny"));
        repository.add(new ChronicleEntry(id("mayor-history"),T,town.getName(),ChronicleCategory.MAYOR,now-3000,"Новый мэр",List.of(),"ChaptersMayor","Towny"));
        repository.add(new ChronicleEntry(id("war-history"),T,town.getName(),ChronicleCategory.WAR,now-1000,"Объявлена война",List.of(),"","Towny"));
        var receipt=new AuditRecord(AuditRecord.id("NeverLandTownySiegesPlus","fixture-repair","DONE"),now,"NeverLandTownySiegesPlus","BUILDING_REPAIR","DONE","fixture-repair",new AuditRecord.Party("PLAYER",M.toString(),"ChaptersMayor",T.toString()),new AuditRecord.Party("TOWN",T.toString(),town.getName(),T.toString()),AuditRecord.Party.system(),"",0,"125.50","private coordinates and administrative notes");
        new AuditJournal(plugin.getDataFolder().toPath().resolve("audit")).append(receipt);
        expectedCount=repository.entries(T).size();Files.writeString(proof.resolve("expected-count.txt"),Integer.toString(expectedCount));await(repository.saveAsync(),done->{try{original=Files.readAllBytes(data);var refresh=history.getClass().getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(history);waitHistory(80);}catch(Exception error){fail(error);}});
    }
    void waitHistory(int left)throws Exception{if(history.chapterEvidence().towns().getOrDefault(T,List.of()).isEmpty()){if(left<=0)throw new AssertionError("audit history not ready: "+history.historyIssue());later(1,()->waitHistory(left-1));return;}check(history.chapterEvidence().towns().get(T).size()==1,"actual asynchronous audit scan exposes one sanitized repair receipt");openReady(60);}
    void openReady(int left)throws Exception{chapters.open(mayor,CityChapters.Theme.REIGN,0);if(windows.get(M).getItem(0)==null){if(left<=0)throw new AssertionError("chapter computation not ready");later(1,()->openReady(left-1));return;}int chapterSlot=-1;for(int slot=0;slot<45;slot++)if(windows.get(M).getItem(slot)!=null&&lore(mayor,slot).contains("125.50"))chapterSlot=slot;check(chapterSlot>=0&&windows.get(M).getItem(chapterSlot).getItemMeta().getDisplayName().contains("ChaptersMayor"),"native chapter has verified mayor and exact repaired city expense");check(lore(mayor,51).contains("Москва")&&lore(mayor,51).contains("сохранившимся"),"native completeness description names Moscow and retained history");
        var event=new InventoryClickEvent(view(mayor,M),InventoryType.SlotType.CONTAINER,chapterSlot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"native chapter detail click prevents taking icon");later(2,this::details);
    }
    void details()throws Exception{String text=lore(mayor,0);check(text.contains("audit:")&&text.contains("125.50")&&!text.contains("private"),"native fact detail shows stable evidence ID without administrative notes");check(repository.entries(T).size()==expectedCount&&repository.entries(T).stream().anyMatch(e->e.id().equals(id("old-history"))&&e.details().equals(List.of("Keep exact old details"))),"viewing/restart preserves exact old entries without generated duplicate events");check(Arrays.equals(original,Files.readAllBytes(data)),"native summary and drilldown do not rewrite persisted chronicle");
        if(restart){var saved=repository.entries(T).stream().filter(e->e.source().equals("mayor-commendation")).toList();check(saved.size()==1&&saved.get(0).id().toString().equals(Files.readString(proof.resolve("thank-id.txt")))&&saved.get(0).timestamp()==Long.parseLong(Files.readString(proof.resolve("thank-timestamp.txt"))),"full JVM restart preserves one acknowledged thank with its exact ID and timestamp");finishChapters();return;}
        chapters.open(mayor,CityChapters.Theme.RECOVERY,0);check(windows.get(M).getItem(0).getType()==Material.ANVIL&&lore(mayor,0).contains("125.50"),"recovery tab uses actual completed repair receipt");
        var denied=limited(M,"ChaptersMayor");denied(()->chapters.open(denied,CityChapters.Theme.REIGN,0),"revoked permission cannot open chapters");var visitor=actor(P,"NoTown");denied(()->chapters.open(visitor,CityChapters.Theme.REIGN,0),"townless visitor cannot inspect city chapters");
        plugin.getConfig().set("chapters.enabled",false);denied(()->chapters.open(mayor,CityChapters.Theme.REIGN,0),"optional chapter disable blocks only new views");plugin.getCommand("chronicles").execute(mayor,"chronicles",new String[]{});check(windows.get(M).getItem(48).getType()==Material.BOOK&&repository.entries(T).size()==expectedCount,"ordinary chronicle remains usable with chapters disabled");plugin.getConfig().set("chapters.enabled",true);contributionChecks();
    }
    void contributionChecks()throws Exception{
        history.open(mayor,0);var old=windows.get(M);
        var event=new InventoryClickEvent(view(mayor,M),InventoryType.SlotType.CONTAINER,49,ClickType.LEFT,InventoryAction.PICKUP_ALL);history.click(event);
        var unrelated=Bukkit.createInventory(null,9);windows.put(M,unrelated);
        later(2,()->{check(event.isCancelled()&&windows.get(M)==unrelated,"stale contribution back click cannot replace a newer menu");
            history.open(mayor,0);var denied=limitedContribution(mayor);var revoke=new InventoryClickEvent(view(denied,M),InventoryType.SlotType.CONTAINER,49,ClickType.LEFT,InventoryAction.PICKUP_ALL);history.click(revoke);
            later(2,()->{check(!windows.containsKey(M),"revoked contribution permission closes view before deferred back action");blockedThank();});
        });
    }
    Player limitedContribution(Player base){var handler=java.lang.reflect.Proxy.getInvocationHandler(base);return(Player)java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->m.getName().equals("hasPermission")&&a[0].equals("townychronicles.contributions")?false:handler.invoke(base,m,a));}
    void blockedThank()throws Exception{
        var trail=Class.forName("ru.neverland.core.AuditTrail",true,plugin.getClass().getClassLoader());var journalsField=trail.getDeclaredField("journals");journalsField.setAccessible(true);@SuppressWarnings("unchecked")var journals=(Map<Path,Object>)journalsField.get(null);
        Path path=plugin.getDataFolder().toPath().resolve("audit");var journalType=Class.forName("ru.neverland.core.AuditJournal",true,plugin.getClass().getClassLoader());var journal=journals.get(path);if(journal==null){journal=journalType.getConstructor(Path.class).newInstance(path);journals.put(path,journal);}final Object actualJournal=journal;
        release=new java.util.concurrent.CountDownLatch(1);var entered=new java.util.concurrent.CountDownLatch(1);var lockThread=new Thread(()->{synchronized(actualJournal){entered.countDown();try{release.await(12,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}},"native-chronicles-blocked-audit");lockThread.setDaemon(true);lockThread.start();if(!entered.await(1,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("audit monitor fixture unavailable");
        history.thank(mayor,"ChaptersMayor","Спасибо за строительство города");
        denied(()->history.thank(mayor,"ChaptersMayor","Повторная благодарность до записи"),"second thank for same city blocked until first audit receipt");
        var pulse=new java.util.concurrent.atomic.AtomicBoolean();Bukkit.getGlobalRegionScheduler().runDelayed(this,t->SuiteState.tryRun("chronicle native blocked audit heartbeat",()->pulse.set(true)),2);
        later(5,()->{check(repository.entries(T).stream().noneMatch(e->e.source().equals("mayor-commendation")),"blocked real audit file cannot publish a thank early");check(pulse.get(),"global suite remains available while actual audit append is blocked");check(messages.stream().noneMatch(m->m.contains("Благодарность сохранена")),"player receives no false success before durable audit");release.countDown();waitThank(80);});
    }
    void waitThank(int left)throws Exception{
        if(repository.entries(T).stream().noneMatch(e->e.source().equals("mayor-commendation"))){if(left<=0)throw new AssertionError("thank audit never acknowledged");later(1,()->waitThank(left-1));return;}
        check(repository.entries(T).stream().filter(e->e.source().equals("mayor-commendation")).count()==1,"confirmed audit restores exactly one chronological thank entry");
        denied(()->history.thank(mayor,"ChaptersMayor","Повторная благодарность после записи"),"confirmed thank preserves one minute city cooldown");
        var thank=repository.entries(T).stream().filter(e->e.source().equals("mayor-commendation")).findFirst().orElseThrow();var rows=new ArrayList<AuditRecord>();AuditJournal.scan(plugin.getDataFolder().toPath().resolve("audit"),rows::add);var receipt=rows.stream().filter(r->r.kind().equals("CITY_COMMENDATION")).findFirst().orElseThrow();check(receipt.id().equals(thank.id().toString())&&receipt.at()==thank.timestamp(),"chronicle publication preserves exact durable audit ID and captured timestamp");
        Files.writeString(proof.resolve("thank-id.txt"),thank.id().toString());Files.writeString(proof.resolve("thank-timestamp.txt"),Long.toString(thank.timestamp()));expectedCount=repository.entries(T).size();Files.writeString(proof.resolve("expected-count.txt"),Integer.toString(expectedCount));await(repository.saveAsync(),done->{try{original=Files.readAllBytes(data);finishChapters();}catch(Exception ex){fail(ex);}});
    }
    Player limited(UUID uuid,String name){var base=actor(uuid,name);var handler=java.lang.reflect.Proxy.getInvocationHandler(base);return(Player)java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->m.getName().equals("hasPermission")&&a[0].equals("townychronicles.use")?false:handler.invoke(base,m,a));}
    void finishChapters()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" checks; actual Folia Towny Chronicles, real asynchronous journal and native menus, exact legacy records and full restart, read-only chapter rendering and permissions; explicitly seeded past entries/repair receipt and offline Player/View/EntityScheduler; no real combat, repair payment or network client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
